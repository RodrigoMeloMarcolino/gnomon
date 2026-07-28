package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CatalogIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
            calendar_offerings,
            calendars,
            offerings,
            collaborators,
            tenant_memberships,
            tenants,
            users
        CASCADE
        """);
  }

  @Test
  void applicationContext_whenCatalogSchemaIsApplied_shouldValidateJpaMappings() {
    assertThat(context).isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'collaborators',
                    'calendars',
                    'offerings',
                    'calendar_offerings'
                  )
                """,
                Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT success
                FROM flyway_schema_history
                WHERE version = '3'
                """,
                Boolean.class))
        .isTrue();
  }

  @Test
  void catalogFlow_whenOwnerBuildsAndDeactivatesCatalog_shouldExposeOnlyActiveAssignedOffering()
      throws Exception {
    var owner = identity("catalog-owner", "catalog-owner@gnomon.local", "Catalog Owner");
    createTenant(owner, "Barbearia Solar", "barbearia-solar");

    mockMvc
        .perform(
            post("/v1/tenants/barbearia-solar/collaborators")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"displayName":"Ana"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.displayName").value("Ana"))
        .andExpect(jsonPath("$.calendar.active").value(true));

    UUID calendarId =
        jdbcTemplate.queryForObject(
            """
            SELECT calendar.id
            FROM calendars calendar
            JOIN tenants tenant ON tenant.id = calendar.tenant_id
            WHERE tenant.slug = 'barbearia-solar'
            """,
            UUID.class);

    createOffering(owner, "barbearia-solar", "Corte", 30, 4_500)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.active").value(true));

    UUID offeringId = offeringId("barbearia-solar", "Corte");

    mockMvc
        .perform(
            put("/v1/tenants/barbearia-solar/calendars/{calendarId}/offerings", calendarId)
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"offeringIds":["%s"]}
                    """
                        .formatted(offeringId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(offeringId.toString()));

    mockMvc
        .perform(
            get("/v1/public/tenants/barbearia-solar/offerings")
                .queryParam("calendar_id", calendarId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Corte"));

    mockMvc
        .perform(
            delete("/v1/tenants/barbearia-solar/offerings/{offeringId}", offeringId).with(owner))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/v1/public/tenants/barbearia-solar/offerings")
                .queryParam("calendar_id", calendarId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT is_active FROM offerings WHERE id = ?", Boolean.class, offeringId))
        .isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM calendar_offerings WHERE offering_id = ?",
                Integer.class,
                offeringId))
        .isOne();
  }

  @Test
  void catalogAccess_whenResourceBelongsToAnotherTenant_shouldReturnAdmin403AndPublic404()
      throws Exception {
    var ownerA = identity("owner-a", "owner-a@gnomon.local", "Owner A");
    var ownerB = identity("owner-b", "owner-b@gnomon.local", "Owner B");
    createTenant(ownerA, "Tenant A", "tenant-a");
    createTenant(ownerB, "Tenant B", "tenant-b");

    createCollaborator(ownerB, "tenant-b", "Bia");
    createOffering(ownerB, "tenant-b", "Barba", 15, null).andExpect(status().isCreated());

    UUID foreignOfferingId = offeringId("tenant-b", "Barba");
    UUID foreignCalendarId = calendarId("tenant-b");

    mockMvc
        .perform(get("/v1/tenants/tenant-a/offerings/{offeringId}", foreignOfferingId).with(ownerA))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("catalog_access_denied"));

    mockMvc
        .perform(
            get("/v1/public/tenants/tenant-a/offerings")
                .queryParam("calendar_id", foreignCalendarId.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("calendar_not_found"));
  }

  @Test
  void replaceAssignments_whenPayloadContainsForeignOffering_shouldPreservePreviousAssignment()
      throws Exception {
    var ownerA = identity("replace-owner-a", "replace-a@gnomon.local", "Replace A");
    var ownerB = identity("replace-owner-b", "replace-b@gnomon.local", "Replace B");
    createTenant(ownerA, "Replace A", "replace-a");
    createTenant(ownerB, "Replace B", "replace-b");
    createCollaborator(ownerA, "replace-a", "Ana");

    createOffering(ownerA, "replace-a", "Original", 30, 3_000).andExpect(status().isCreated());
    createOffering(ownerA, "replace-a", "Nova", 30, 4_000).andExpect(status().isCreated());
    createOffering(ownerB, "replace-b", "Estrangeira", 30, 5_000).andExpect(status().isCreated());

    UUID calendarId = calendarId("replace-a");
    UUID originalId = offeringId("replace-a", "Original");
    UUID replacementId = offeringId("replace-a", "Nova");
    UUID foreignId = offeringId("replace-b", "Estrangeira");

    replaceAssignments(ownerA, "replace-a", calendarId, originalId).andExpect(status().isOk());

    mockMvc
        .perform(
            put("/v1/tenants/replace-a/calendars/{calendarId}/offerings", calendarId)
                .with(ownerA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"offeringIds":["%s","%s"]}
                    """
                        .formatted(replacementId, foreignId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("catalog_access_denied"));

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT offering_id
                FROM calendar_offerings
                WHERE calendar_id = ?
                """,
                UUID.class,
                calendarId))
        .containsExactly(originalId);
  }

  @Test
  void createOffering_whenActiveTitleDiffersOnlyByCase_shouldReturn422() throws Exception {
    var owner = identity("duplicate-owner", "duplicate@gnomon.local", "Duplicate Owner");
    createTenant(owner, "Duplicate", "duplicate");

    createOffering(owner, "duplicate", "Corte", 30, null).andExpect(status().isCreated());
    createOffering(owner, "duplicate", "cOrTe", 30, null)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM offerings offering
                JOIN tenants tenant ON tenant.id = offering.tenant_id
                WHERE tenant.slug = 'duplicate'
                  AND offering.is_active
                """,
                Integer.class))
        .isOne();
  }

  private void createTenant(JwtRequestPostProcessor owner, String tenantName, String tenantSlug)
      throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"%s",
                      "slug":"%s",
                      "timezone":"America/Fortaleza",
                      "currencyCode":"BRL"
                    }
                    """
                        .formatted(tenantName, tenantSlug)))
        .andExpect(status().isCreated());
  }

  private void createCollaborator(
      JwtRequestPostProcessor owner, String tenantSlug, String displayName) throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/{tenantSlug}/collaborators", tenantSlug)
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"displayName":"%s"}
                    """
                        .formatted(displayName)))
        .andExpect(status().isCreated());
  }

  private org.springframework.test.web.servlet.ResultActions createOffering(
      JwtRequestPostProcessor owner,
      String tenantSlug,
      String title,
      int durationMinutes,
      Integer priceCents)
      throws Exception {
    String price = priceCents == null ? "null" : priceCents.toString();
    return mockMvc.perform(
        post("/v1/tenants/{tenantSlug}/offerings", tenantSlug)
            .with(owner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "title":"%s",
                  "description":null,
                  "durationMinutes":%d,
                  "priceCents":%s
                }
                """
                    .formatted(title, durationMinutes, price)));
  }

  private org.springframework.test.web.servlet.ResultActions replaceAssignments(
      JwtRequestPostProcessor owner, String tenantSlug, UUID calendarId, UUID offeringId)
      throws Exception {
    return mockMvc.perform(
        put("/v1/tenants/{tenantSlug}/calendars/{calendarId}/offerings", tenantSlug, calendarId)
            .with(owner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"offeringIds":["%s"]}
                """
                    .formatted(offeringId)));
  }

  private UUID offeringId(String tenantSlug, String title) {
    return jdbcTemplate.queryForObject(
        """
        SELECT offering.id
        FROM offerings offering
        JOIN tenants tenant ON tenant.id = offering.tenant_id
        WHERE tenant.slug = ?
          AND offering.title = ?
        """,
        UUID.class,
        tenantSlug,
        title);
  }

  private UUID calendarId(String tenantSlug) {
    return jdbcTemplate.queryForObject(
        """
        SELECT calendar.id
        FROM calendars calendar
        JOIN tenants tenant ON tenant.id = calendar.tenant_id
        WHERE tenant.slug = ?
        """,
        UUID.class,
        tenantSlug);
  }

  private static JwtRequestPostProcessor identity(String subject, String email, String name) {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(subject)
                    .claim("email", email)
                    .claim("name", name)
                    .claim("email_verified", true));
  }
}

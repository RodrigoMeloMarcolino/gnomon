package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.tenancy.application.port.out.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class IdentityTenancyIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserRepository userRepository;

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
  void identityAndTenancyFlow_shouldProvisionAndEnforceTenantBoundaries() throws Exception {
    var ownerA = identity("subject-owner-a", "OWNER-A@gnomon.local", "Owner A");
    var ownerB = identity("subject-owner-b", "owner-b@gnomon.local", "Owner B");

    mockMvc
        .perform(get("/v1/tenants").with(ownerA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT keycloak_sub, email::text, display_name
                FROM users
                WHERE keycloak_sub = 'subject-owner-a'
                """))
        .containsEntry("keycloak_sub", "subject-owner-a")
        .containsEntry("email", "owner-a@gnomon.local")
        .containsEntry("display_name", "Owner A");

    createTenant(ownerA, "Tenant A", "tenant-a").andExpect(status().isCreated());
    createTenant(ownerB, "Tenant B", "tenant-b").andExpect(status().isCreated());

    mockMvc
        .perform(get("/v1/tenants").with(ownerA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].slug").value("tenant-a"))
        .andExpect(jsonPath("$[0].role").value("owner"));

    mockMvc
        .perform(get("/v1/tenants/tenant-b").with(ownerA))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("membership_required"));

    assertThat(userRepository.findByEmail("OWNER-B@GNOMON.LOCAL"))
        .hasValueSatisfying(user -> assertThat(user.email()).isEqualTo("owner-b@gnomon.local"));

    mockMvc
        .perform(
            post("/v1/tenants/tenant-a/memberships")
                .with(ownerA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"OWNER-B@GNOMON.LOCAL","role":"admin"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("owner-b@gnomon.local"))
        .andExpect(jsonPath("$.role").value("admin"));

    UUID tenantBOwnerMembership =
        jdbcTemplate.queryForObject(
            """
            SELECT membership.id
            FROM tenant_memberships membership
            JOIN tenants tenant ON tenant.id = membership.tenant_id
            WHERE tenant.slug = 'tenant-b' AND membership.role = 'owner'
            """,
            UUID.class);
    UUID tenantAOwnerMembership =
        jdbcTemplate.queryForObject(
            """
            SELECT membership.id
            FROM tenant_memberships membership
            JOIN tenants tenant ON tenant.id = membership.tenant_id
            WHERE tenant.slug = 'tenant-a' AND membership.role = 'owner'
            """,
            UUID.class);

    mockMvc
        .perform(
            delete("/v1/tenants/tenant-a/memberships/{membershipId}", tenantBOwnerMembership)
                .with(ownerA))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("membership_required"));

    mockMvc
        .perform(
            delete("/v1/tenants/tenant-a/memberships/{membershipId}", tenantAOwnerMembership)
                .with(ownerA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("last_owner"));

    createTenant(ownerA, "Duplicate", "tenant-a")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("tenant_slug_taken"));
  }

  @Test
  void createTenant_whenSlugIsConcurrent_shouldReturnOneCreatedAndOneConflict() throws Exception {
    var contenderA = identity("subject-race-a", "race-a@gnomon.local", "Race A");
    var contenderB = identity("subject-race-b", "race-b@gnomon.local", "Race B");

    mockMvc.perform(get("/v1/tenants").with(contenderA)).andExpect(status().isOk());
    mockMvc.perform(get("/v1/tenants").with(contenderB)).andExpect(status().isOk());

    var barrier = new CyclicBarrier(2);
    Callable<MvcResult> requestA =
        () -> {
          barrier.await();
          return performCreateTenant(contenderA, "Race A", "concurrent-tenant");
        };
    Callable<MvcResult> requestB =
        () -> {
          barrier.await();
          return performCreateTenant(contenderB, "Race B", "concurrent-tenant");
        };

    try (var executor = Executors.newFixedThreadPool(2)) {
      var results = executor.invokeAll(List.of(requestA, requestB));
      var statuses =
          results.stream()
              .map(
                  future -> {
                    try {
                      return future.get().getResponse().getStatus();
                    } catch (Exception exception) {
                      throw new AssertionError("Concurrent tenant request failed", exception);
                    }
                  })
              .sorted()
              .toList();

      assertThat(statuses).containsExactly(201, 409);
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenants WHERE slug = 'concurrent-tenant'", Integer.class))
        .isOne();
  }

  private org.springframework.test.web.servlet.ResultActions createTenant(
      JwtRequestPostProcessor identity, String name, String slug) throws Exception {
    return mockMvc.perform(
        post("/v1/tenants")
            .with(identity)
            .contentType(MediaType.APPLICATION_JSON)
            .content(tenantBody(name, slug)));
  }

  private MvcResult performCreateTenant(JwtRequestPostProcessor identity, String name, String slug)
      throws Exception {
    return mockMvc
        .perform(
            post("/v1/tenants")
                .with(identity)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tenantBody(name, slug)))
        .andReturn();
  }

  private static String tenantBody(String name, String slug) {
    return """
        {
          "name":"%s",
          "slug":"%s",
          "timezone":"America/Fortaleza",
          "currencyCode":"BRL"
        }
        """
        .formatted(name, slug);
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

package io.gnomon;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Gates de arquitetura do ADR 0002: dependências permitidas entre camadas — {@code api →
 * application → domain ← infrastructure} (infrastructure também implementa ports de application).
 * Vacuamente verdes na fase 00 (módulos vazios); tornam-se efetivas conforme os módulos ganham
 * código.
 */
@AnalyzeClasses(packages = "io.gnomon", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domainDoesNotDependOnFrameworks =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

  @ArchTest
  static final ArchRule domainDoesNotDependOnOuterLayers =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..application..", "..infrastructure..");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnApiOrInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..infrastructure..");

  @ArchTest
  static final ArchRule apiDoesNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..");
}

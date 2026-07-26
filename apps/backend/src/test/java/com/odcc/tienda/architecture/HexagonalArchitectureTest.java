package com.odcc.tienda.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.odcc.tienda",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainMustRemainFrameworkIndependent = noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "org.hibernate..",
            "jakarta.persistence..",
            "jakarta.validation..",
            "..application..",
            "..adapter.."
        );

    @ArchTest
    static final ArchRule applicationMustNotDependOnAdaptersOrInfrastructure = noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter..",
            "..infrastructure..",
            "..security..",
            "..web..",
            "org.springframework..",
            "jakarta.persistence.."
        );

    @ArchTest
    static final ArchRule restAdaptersMustNotUsePersistenceAdaptersDirectly = noClasses()
        .that()
        .resideInAPackage("..adapter.in.rest..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter.out.persistence..",
            "org.springframework.data.jpa.repository.."
        );

    @ArchTest
    static final ArchRule restAdaptersMustNotExposeJpaEntities = noClasses()
        .that()
        .resideInAPackage("..adapter.in.rest..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..adapter.out.persistence.entity..");
}

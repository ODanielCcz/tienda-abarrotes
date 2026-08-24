package com.odcc.tienda.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

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

    @ArchTest
    static final ArchRule domainAndApplicationMustNotUseMapStruct = noClasses()
        .that()
        .resideInAnyPackage("..domain..", "..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.mapstruct..");

    @ArchTest
    static final ArchRule productionFieldsMustNotUseAutowired = noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("com.odcc.tienda..")
        .should()
        .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class);
}

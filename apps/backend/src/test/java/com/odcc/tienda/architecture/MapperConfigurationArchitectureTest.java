package com.odcc.tienda.architecture;

import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.mapstruct.Mapper;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
    packages = "com.odcc.tienda",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class MapperConfigurationArchitectureTest {

    @ArchTest
    static final ArchRule everyMapperMustUseCentralConfiguration = classes()
        .that()
        .areAnnotatedWith(Mapper.class)
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(CentralMapperConfig.class.getName());
}

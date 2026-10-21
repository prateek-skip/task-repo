package com.example.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

/** The dependency rule, enforced by the build rather than by convention. */
@AnalyzeClasses(packages = "com.example.catalog", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersDependInwardOnly = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Web").definedBy("..adapter.in..")
            .layer("Persistence").definedBy("..adapter.out.persistence..")
            .layer("Cache").definedBy("..adapter.out.cache..")
            .layer("Id").definedBy("..adapter.out.id..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Cache").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Id").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Config");

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml..", "tools.jackson..");

    @ArchTest
    static final ArchRule controllersUseUseCaseInterfacesOnly = noClasses()
            .that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..");

    @ArchTest
    static final ArchRule transactionsBelongToUseCases = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..application.service..")
            .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule noClassLevelTransactionsOutsideUseCases = noClasses()
            .that().resideOutsideOfPackage("..application.service..")
            .should().beAnnotatedWith(Transactional.class);
}

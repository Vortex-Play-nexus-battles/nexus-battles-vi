package com.nexusbattles.ms_subastas.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura exigidas por .claude/rules/backend-spring.md. El build
 * falla si alguien las rompe, para que no sean negociables "por practicidad en
 * un momento de apuro".
 */
class ReglasDeArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.nexusbattles.ms_subastas");
    }

    @Test
    void ningunaClaseImportaOtroMicroservicioDelMonorepo() {
        ArchRule regla = noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.nexusbattles.ms_identidad..",
                        "com.nexusbattles.ms_ecommerce..",
                        "com.nexusbattles.ms_cumplimiento..",
                        "com.nexusbattles.ms_finanzas..",
                        "com.nexusbattles.ms_chatbot..")
                .because("ningun servicio importa clases internas de otro dominio: "
                        + "la integracion va por REST o por evento, nunca por la clase ni por la BD ajena");

        regla.check(clases);
    }

    @Test
    void elMotorDePujasNoConoceLaPersistenciaNiLaWeb() {
        ArchRule regla = noClasses()
                .that().haveSimpleNameStartingWith("Motor")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.data..",
                        "org.springframework.web..",
                        "jakarta.persistence..",
                        "com.nexusbattles.ms_subastas..repository..")
                .because("las reglas de negocio deben poder probarse sin base de datos ni HTTP; "
                        + "quien abre transaccion y toma el lock es PujaApplicationService");

        regla.check(clases);
    }

    @Test
    void nadieUsaElRelojDelSistemaDirectamente() {
        ArchRule regla = noClasses()
                .should().callMethod(java.time.Instant.class, "now")
                .because("el reloj se inyecta como java.time.Clock: con Instant.now() el intervalo "
                        + "minimo de 5 s entre pujas no se puede probar sin dormir hilos");

        regla.check(clases);
    }
}

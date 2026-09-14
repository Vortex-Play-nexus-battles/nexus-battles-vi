package com.nexusbattles.ms_identidad.rbac;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentProfileIsolationTest {

    private Properties cargar(String archivo) throws IOException {
        Properties propiedades = new Properties();

        try (var inputStream =
                     new ClassPathResource(archivo).getInputStream()) {
            propiedades.load(inputStream);
        }

        return propiedades;
    }

    @Test
    void desarrolloYProduccionDebenUsarCredencialesSeparadas()
            throws IOException {

        Properties dev = cargar("application-dev.properties");
        Properties prod = cargar("application-prod.properties");

        assertNotEquals(
                dev.getProperty("spring.datasource.url"),
                prod.getProperty("spring.datasource.url")
        );

        assertNotEquals(
                dev.getProperty("spring.datasource.username"),
                prod.getProperty("spring.datasource.username")
        );

        assertNotEquals(
                dev.getProperty("spring.datasource.password"),
                prod.getProperty("spring.datasource.password")
        );
    }

    @Test
    void produccionNoDebeContenerCredencialesReales()
            throws IOException {

        Properties prod = cargar("application-prod.properties");

        assertEquals(
                "${DB_URL}",
                prod.getProperty("spring.datasource.url")
        );

        assertEquals(
                "${DB_USER}",
                prod.getProperty("spring.datasource.username")
        );

        assertEquals(
                "${DB_PASSWORD}",
                prod.getProperty("spring.datasource.password")
        );
    }

    @Test
    void desarrolloDebeSerElPerfilLocalPorDefecto()
            throws IOException {

        Properties base = cargar("application.properties");

        assertEquals(
                "dev",
                base.getProperty("spring.profiles.active")
        );
    }

    @Test
    void produccionDebeDeshabilitarElRespaldoDeRolPorHeader()
            throws IOException {

        Properties prod = cargar("application-prod.properties");
        Properties dev = cargar("application-dev.properties");

        // HU-RBAC-004: en produccion la unica credencial valida es el Bearer JWT.
        assertEquals(
                "false",
                prod.getProperty("app.seguridad.permitir-header-rol"),
                "El respaldo X-User-Role debe estar apagado en produccion"
        );

        // En desarrollo si se permite, para demostrar los cuatro roles sin
        // cuatro inicios de sesion reales.
        assertEquals(
                "true",
                dev.getProperty("app.seguridad.permitir-header-rol")
        );
    }
}

package com.nexusbattles.ms_identidad.rbac;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;
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

    /**
     * R9.5 — esta prueba exigia que dev tuviera literalmente {@code true}.
     *
     * <p>Se escribio cuando "en desarrollo si se permite" parecia inofensivo,
     * y por eso no vio el problema: el CD arranca ms-identidad con el perfil
     * <b>dev</b> tanto en dev como en test ({@code cd.yml:587} y {@code :831}).
     * O sea que la cabecera valia en un host desplegado y compartido: mandar
     * {@code X-User-Role: SUPER_ADMINISTRADOR} bastaba para ejecutar una
     * accion administrativa sin ningun token. El guardian estaba fijando el
     * defecto en vez de impedirlo.
     *
     * <p>La invariante nueva es mas fuerte y es la que se comprueba aqui:
     * ningun perfil <b>desplegable</b> puede traer el respaldo encendido de
     * fabrica. Sigue existiendo —se enciende a proposito con
     * {@code RBAC_PERMITIR_HEADER_ROL=true} para una demo local— y vive
     * encendido solo en {@code application-test.properties}, que esta en
     * {@code src/test/resources} y no entra en el jar.
     */
    @Test
    void ningunPerfilDesplegableTraeElRespaldoDeRolPorHeaderEncendido()
            throws IOException {

        Properties prod = cargar("application-prod.properties");
        Properties dev = cargar("application-dev.properties");

        // HU-RBAC-004: en produccion la unica credencial valida es el Bearer JWT.
        assertEquals(
                "false",
                prod.getProperty("app.seguridad.permitir-header-rol"),
                "El respaldo X-User-Role debe estar apagado en produccion"
        );

        // Y en dev tampoco viene encendido. Se comprueba RESOLVIENDO la
        // propiedad como lo hara Spring, no comparando el texto: asi la prueba
        // sigue valiendo si alguien renombra la variable de entorno.
        assertEquals(
                "false",
                resolver(dev.getProperty("app.seguridad.permitir-header-rol"), Map.of()),
                "Sin variables de entorno, dev tiene que resolver a false: el CD "
                        + "arranca con este perfil en un host compartido"
        );

        // Pero la puerta de servicio sigue ahi para quien la pida a proposito.
        assertEquals(
                "true",
                resolver(dev.getProperty("app.seguridad.permitir-header-rol"),
                        Map.of("RBAC_PERMITIR_HEADER_ROL", "true")),
                "El respaldo debe poder encenderse explicitamente para una demo local"
        );
    }

    /** Resuelve un valor con marcadores usando la misma maquinaria de Spring. */
    private static String resolver(String valor, Map<String, Object> variables) {
        StandardEnvironment entorno = new StandardEnvironment();
        // Fuera el entorno de la maquina que corre la prueba: aqui solo debe
        // pesar lo que se inyecta.
        entorno.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources()
                .addFirst(new MapPropertySource("del-despliegue", variables));
        return entorno.resolvePlaceholders(valor);
    }
}

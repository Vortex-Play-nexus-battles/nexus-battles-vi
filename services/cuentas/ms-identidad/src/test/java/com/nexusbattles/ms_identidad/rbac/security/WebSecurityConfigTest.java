package com.nexusbattles.ms_identidad.rbac.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Los origenes que el navegador puede usar contra esta API — R9.5.
 *
 * <h2>Que se fija aqui</h2>
 *
 * {@code WebSecurityConfig} tenia {@code allowedOriginPatterns("*")} escrito en
 * el codigo: cualquier pagina de cualquier dominio podia lanzar peticiones a
 * esta API desde el navegador de quien la visitara. El Bearer no viaja solo en
 * esas peticiones, asi que no es robo de sesion por si mismo; lo que si daba
 * era superficie gratis, y contradecia la regla 10 de plataforma —la
 * configuracion se fija por variable de entorno, no escrita en el codigo—.
 *
 * <p>Al pasarlo a configuracion aparece un modo de fallo nuevo, y es el que
 * esta clase cubre: si el despliegue pasa la variable SIN valor, la lista
 * llega vacia. Una lista vacia no puede significar "ninguno": significaria que
 * la API deja de responder a toda peticion con origen y nadie entenderia por
 * que. Ante eso se cae a la lista de desarrollo, que es restrictiva pero
 * utilizable.
 */
@DisplayName("R9.5: origenes CORS por configuracion, nunca vacios")
class WebSecurityConfigTest {

    @Test
    @DisplayName("null cae a la lista de desarrollo")
    void nuloCaeAlPorOmision() {
        assertArrayEquals(
                WebSecurityConfig.ORIGENES_POR_OMISION,
                WebSecurityConfig.normalizar(null));
    }

    @Test
    @DisplayName("lista vacia cae a la lista de desarrollo, no a 'ninguno'")
    void vaciaCaeAlPorOmision() {
        assertArrayEquals(
                WebSecurityConfig.ORIGENES_POR_OMISION,
                WebSecurityConfig.normalizar(new String[0]));
    }

    @Test
    @DisplayName("una variable pasada en blanco tambien: es el caso real del despliegue")
    void soloBlancosCaeAlPorOmision() {
        // `IDENTIDAD_CORS_ORIGENES=` en el .env llega asi, no como null.
        assertArrayEquals(
                WebSecurityConfig.ORIGENES_POR_OMISION,
                WebSecurityConfig.normalizar(new String[] {"", "  ", null}));
    }

    @Test
    @DisplayName("con valores reales manda la configuracion, recortada")
    void configuradosMandan() {
        assertArrayEquals(
                new String[] {"https://nexus.example", "http://localhost:8099"},
                WebSecurityConfig.normalizar(
                        new String[] {" https://nexus.example ", "", "http://localhost:8099"}));
    }

    @Test
    @DisplayName("el por omision se entrega copiado: nadie puede mutarlo desde fuera")
    void elPorOmisionNoSeComparte() {
        String[] primera = WebSecurityConfig.normalizar(null);
        String[] segunda = WebSecurityConfig.normalizar(null);

        assertNotSame(WebSecurityConfig.ORIGENES_POR_OMISION, primera);
        assertNotSame(primera, segunda);
    }
}

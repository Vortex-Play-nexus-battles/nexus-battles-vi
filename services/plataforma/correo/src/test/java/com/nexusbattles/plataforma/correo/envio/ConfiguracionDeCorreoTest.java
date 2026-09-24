package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuracion del remitente y de los enlaces.
 *
 * <p>El remitente es la causa mas comun de un correo que no llega: un
 * proveedor real rechaza el mensaje si el From no es una direccion autorizada
 * de la cuenta. Por eso tiene que poder configurarse y por eso se comprueba.
 */
class ConfiguracionDeCorreoTest {

    @Test
    void sinRemitenteConfiguradoDecideElServidor() {
        assertThat(new ConfiguracionDeCorreo(null, null, null, null).tieneRemitente()).isFalse();
        assertThat(new ConfiguracionDeCorreo("  ", "", "", 10).tieneRemitente()).isFalse();
    }

    @Test
    void aceptaLaFormaNombreYDireccion() {
        ConfiguracionDeCorreo configuracion =
                new ConfiguracionDeCorreo(
                        "The Nexus Battles VI <no-reply@nexus.test>", "", "http://x", 10);

        assertThat(configuracion.tieneRemitente()).isTrue();
        assertThat(configuracion.remitente()).isEqualTo("The Nexus Battles VI <no-reply@nexus.test>");
    }

    /** La base publica nunca lleva barra final: los enlaces quedarian con dos. */
    @Test
    void laBasePublicaPierdeLasBarrasFinales() {
        ConfiguracionDeCorreo configuracion =
                new ConfiguracionDeCorreo("", "", "https://nexus.example.com///", 10);

        assertThat(configuracion.basePublica()).isEqualTo("https://nexus.example.com");
        assertThat(configuracion.enlace("/restablecer")).isEqualTo("https://nexus.example.com/restablecer");
        assertThat(configuracion.enlace("restablecer")).isEqualTo("https://nexus.example.com/restablecer");
        assertThat(configuracion.enlace("")).isEqualTo("https://nexus.example.com");
    }

    @Test
    void unNumeroDeEnviosAbsurdoSeCorrigeSolo() {
        assertThat(new ConfiguracionDeCorreo("", "", "", 0).enviosRecordados()).isEqualTo(200);
        assertThat(new ConfiguracionDeCorreo("", "", "", -5).enviosRecordados()).isEqualTo(200);
        assertThat(new ConfiguracionDeCorreo("", "", "", null).enviosRecordados()).isEqualTo(200);
        assertThat(new ConfiguracionDeCorreo("", "", "", 25).enviosRecordados()).isEqualTo(25);
    }
}
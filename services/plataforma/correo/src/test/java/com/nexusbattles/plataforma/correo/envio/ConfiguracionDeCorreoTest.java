package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuracion del remitente y de los enlaces.
 *
 * <p>El remitente es la causa mas comun de un correo que no llega: un
 * proveedor real rechaza el mensaje si el From no es una direccion autorizada
 * de la cuenta. Por eso sale de MAIL_FROM, tiene un valor de reserva que no
 * suplanta a nadie y se comprueba al arrancar.
 */
class ConfiguracionDeCorreoTest {

    @Test
    void sinMailFromElRemitenteEsElDelDominioReservadoDePruebas() {
        // .test (RFC 2606) no existe en Internet: nunca sale con una identidad
        // inventada, y nada de .local en un valor por omision.
        assertThat(new ConfiguracionDeCorreo(null, null, null).remitente())
                .isEqualTo("The Nexus Battles VI <no-reply@nexusbattles.test>")
                .isEqualTo(ConfiguracionDeCorreo.REMITENTE_POR_OMISION)
                .doesNotContain(".local");
        // Una linea MAIL_FROM= vacia en el .env llega como cadena vacia.
        assertThat(new ConfiguracionDeCorreo("  ", "", "").remitente())
                .isEqualTo(ConfiguracionDeCorreo.REMITENTE_POR_OMISION);
    }

    @Test
    void aceptaLaFormaNombreYDireccion() {
        ConfiguracionDeCorreo configuracion =
                new ConfiguracionDeCorreo("The Nexus Battles VI <equipo@upb.edu.co>", "", "http://x");

        assertThat(configuracion.remitente()).isEqualTo("The Nexus Battles VI <equipo@upb.edu.co>");
    }

    @Test
    void unMailFromMalEscritoImpideArrancarYDiceCual() {
        assertThatThrownBy(() -> new ConfiguracionDeCorreo("no-es-un-correo <", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> new ConfiguracionDeCorreo("a@b.co, c@d.co", "", ""))
                .as("un From con dos direcciones tampoco")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elResponderAEsOpcional() {
        assertThat(new ConfiguracionDeCorreo(null, null, null).tieneResponderA()).isFalse();
        assertThat(new ConfiguracionDeCorreo(null, "soporte@upb.edu.co", null).tieneResponderA()).isTrue();
    }

    /** La base publica nunca lleva barra final: los enlaces quedarian con dos. */
    @Test
    void laBasePublicaPierdeLasBarrasFinales() {
        ConfiguracionDeCorreo configuracion =
                new ConfiguracionDeCorreo("", "", "https://nexus.example.com///");

        assertThat(configuracion.basePublica()).isEqualTo("https://nexus.example.com");
        assertThat(configuracion.enlace("/restablecer")).isEqualTo("https://nexus.example.com/restablecer");
        assertThat(configuracion.enlace("restablecer")).isEqualTo("https://nexus.example.com/restablecer");
        assertThat(configuracion.enlace("")).isEqualTo("https://nexus.example.com");
        assertThat(configuracion.enlace(null)).isEqualTo("https://nexus.example.com");
    }

    @Test
    void sinPublicBaseUrlLosEnlacesVanAlDesarrolloLocal() {
        assertThat(new ConfiguracionDeCorreo(null, null, "  ").basePublica()).isEqualTo("http://localhost");
        assertThat(new ConfiguracionDeCorreo(null, null, null).enlace("/verificar"))
                .isEqualTo("http://localhost/verificar");
    }
}

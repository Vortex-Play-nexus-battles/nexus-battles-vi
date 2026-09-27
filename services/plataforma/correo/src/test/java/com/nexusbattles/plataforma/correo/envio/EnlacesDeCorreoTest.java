package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los enlaces de los correos con codigo (contrato 1.4.0): a donde llevan segun
 * el proposito, y el codigo SIEMPRE en el fragmento.
 */
class EnlacesDeCorreoTest {

    private final EnlacesDeCorreo enlaces =
            new EnlacesDeCorreo(new ConfiguracionDeCorreo(null, null, "https://nexus.example.com/"));

    @Test
    void laVerificacionDeUnJugadorLlevaAVerificar() {
        assertThat(enlaces.confirmacionDeCuenta(PropositoDeConfirmacion.VERIFICACION, "K7QX2M9P", "valkiria@upb.edu.co"))
                .isEqualTo("https://nexus.example.com/verificar#codigo=K7QX2M9P&correo=valkiria%40upb.edu.co");
    }

    @Test
    void laActivacionDeUnaCuentaAdministrativaLlevaARestablecer() {
        assertThat(enlaces.confirmacionDeCuenta(PropositoDeConfirmacion.ACTIVACION, "K7QX2M9P", "admin@upb.edu.co"))
                .isEqualTo("https://nexus.example.com/restablecer#codigo=K7QX2M9P&correo=admin%40upb.edu.co");
    }

    @Test
    void laRecuperacionDeClaveLlevaARestablecer() {
        assertThat(enlaces.recuperacionDeClave("482915", "jugador@gmail.com"))
                .isEqualTo("https://nexus.example.com/restablecer#codigo=482915&correo=jugador%40gmail.com");
    }

    @Test
    void elCodigoNuncaVaEnLaConsulta() {
        // El fragmento no sale del navegador: no queda en la bitacora del
        // borde ni en el Referer. La consulta si.
        URI enlace = URI.create(enlaces.recuperacionDeClave("482915", "jugador@gmail.com"));

        assertThat(enlace.getRawQuery()).isNull();
        assertThat(enlace.getRawFragment()).isEqualTo("codigo=482915&correo=jugador%40gmail.com");
    }

    @Test
    void losValoresVanCodificadosParaNoRomperElFragmento() {
        // Un '+' sin codificar se leeria como espacio al otro lado; un '&'
        // partiria el fragmento en dos.
        assertThat(enlaces.recuperacionDeClave("a&b=c", "jugador+qa@gmail.com"))
                .endsWith("#codigo=a%26b%3Dc&correo=jugador%2Bqa%40gmail.com");
    }

    @Test
    void sinBasePublicaConfiguradaSeUsaLaDeDesarrolloLocal() {
        EnlacesDeCorreo locales = new EnlacesDeCorreo(new ConfiguracionDeCorreo(null, null, ""));

        assertThat(locales.recuperacionDeClave("1", "a@b.co")).startsWith("http://localhost/restablecer#");
    }

    @Test
    void elPropositoGuardadoSeLeeConActivacionPorOmision() {
        assertThat(PropositoDeConfirmacion.desde("VERIFICACION")).isEqualTo(PropositoDeConfirmacion.VERIFICACION);
        assertThat(PropositoDeConfirmacion.desde("ACTIVACION")).isEqualTo(PropositoDeConfirmacion.ACTIVACION);
        assertThat(PropositoDeConfirmacion.desde(null)).isEqualTo(PropositoDeConfirmacion.ACTIVACION);
        assertThat(PropositoDeConfirmacion.desde("OTRO")).isEqualTo(PropositoDeConfirmacion.ACTIVACION);
    }

    @Test
    void laComposicionAnadeElEnlaceSoloALosCorreosConCodigo() {
        ComposicionDeCorreo composicion = new ComposicionDeCorreo(enlaces);

        assertThat(composicion.variables(Plantilla.CONFIRMACION_CUENTA, "nuevo@gmail.com",
                        Map.of("codigo", "734201", "proposito", "VERIFICACION")))
                .containsEntry(ComposicionDeCorreo.ENLACE,
                        "https://nexus.example.com/verificar#codigo=734201&correo=nuevo%40gmail.com")
                .containsEntry("codigo", "734201");
        assertThat(composicion.variables(Plantilla.CONFIRMACION_CUENTA, "admin@gmail.com", Map.of("codigo", "734201")))
                .as("sin proposito guardado: activacion")
                .containsEntry(ComposicionDeCorreo.ENLACE,
                        "https://nexus.example.com/restablecer#codigo=734201&correo=admin%40gmail.com");
        assertThat(composicion.variables(Plantilla.RECUPERACION_CLAVE, "j@gmail.com", Map.of("codigo", "482915")))
                .containsKey(ComposicionDeCorreo.ENLACE);
        assertThat(composicion.variables(Plantilla.BIENVENIDA, "j@gmail.com", Map.of("codigo", "no-aplica")))
                .doesNotContainKey(ComposicionDeCorreo.ENLACE);
    }

    @Test
    void sinCodigoNoHayEnlaceQuePintar() {
        ComposicionDeCorreo composicion = new ComposicionDeCorreo(enlaces);

        assertThat(composicion.variables(Plantilla.RECUPERACION_CLAVE, "j@gmail.com", Map.of("apodo", "Ana")))
                .doesNotContainKey(ComposicionDeCorreo.ENLACE);
        assertThat(composicion.variables(Plantilla.RECUPERACION_CLAVE, "j@gmail.com", Map.of("codigo", " ")))
                .doesNotContainKey(ComposicionDeCorreo.ENLACE);
        assertThat(composicion.variables(Plantilla.RECUPERACION_CLAVE, "j@gmail.com", null)).isEmpty();
    }
}

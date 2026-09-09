package com.nexusbattles.plataforma.correo.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantillaCorreoServiceTest {

    private final PlantillaCorreoService service = new PlantillaCorreoService();

    @Test
    void incluyeElEncabezadoCorporativo() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).contains("THE NEXUS BATTLES VI");
    }

    @Test
    void incluyeElPieDePaginaCorporativo() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).contains("The Nexus Battles VI");
        assertThat(html).contains("instagram.com/thenexusbattles");
    }

    @Test
    void inyectaElContenidoDinamicoRecibido() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).contains("Hola Mundo");
    }

    @Test
    void noDejaMarcadoresDePlantillaSinReemplazar() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).doesNotContain("CONTENIDO_DINAMICO");
    }

    @Test
    void referenciaElLogoCorporativoComoImagenIncrustada() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html)
                .as("el logo debe ir incrustado (cid:), no como URL ni en base64")
                .contains("src=\"cid:logo-nexus\"");
    }

    @Test
    void conservaElTextoDeMarcaAunqueElClienteBloqueeImagenes() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html)
                .as("muchos clientes bloquean imágenes; sin el texto el encabezado queda vacío")
                .contains("THE NEXUS BATTLES VI");
        assertThat(html)
                .as("el texto alternativo es lo único que se ve si la imagen no carga")
                .contains("alt=\"The Nexus Battles VI\"");
    }

    @Test
    void elArchivoDelLogoExisteEnElClasspath() {
        assertThat(getClass().getResourceAsStream("/imagenes/logo-nexus.png"))
                .as("EnviadorCorreoService lo adjunta desde ahí; si falta, todo correo falla")
                .isNotNull();
    }

    @Test
    void rechazaUnaPlantillaQueNoEstaEnElRegistro() {
        // El nombre de la plantilla nunca debe poder venir del usuario: cargar
        // una arbitraria del classpath permitiria leer plantillas ajenas o
        // inyectar contenido. Solo se sirven las declaradas.
        assertThatThrownBy(() -> service.renderizar("email/inventada", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email/inventada");
    }

    @Test
    void rechazaIntentosDeSalirseDeLaCarpetaDePlantillas() {
        assertThatThrownBy(() -> service.renderizar("../../application", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conservaLosAcentosYLaEneAlRenderizar() {
        String html = service.renderizar("email/recuperacion-clave",
                Map.of("apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 15));

        assertThat(html)
                .as("si la plantilla se lee con la codificacion equivocada, los correos salen con simbolos raros")
                .contains("contraseña")
                .doesNotContain("�");
    }

    // ----- HU-COR-002: plantilla de confirmacion de cuenta -----

    @Test
    void laConfirmacionDeCuentaVaSobreLaPlantillaCorporativa() {
        // CP-01 de #24: "el correo llega con el codigo legible y aplicando el
        // diseno de la plantilla corporativa oficial".
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("src=\"cid:logo-nexus\"")
                .contains("instagram.com/thenexusbattles");
    }

    @Test
    void laConfirmacionDeCuentaMuestraElCodigoLaVigenciaYElApodo() {
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15));

        assertThat(html)
                .contains("Confirma tu cuenta")
                .contains("ElGuerrero")
                .contains("734201")
                .contains("15</strong> minutos")
                .as("debe decir que se puede pedir uno nuevo y que el anterior deja de servir (CA-03)")
                .contains("pide uno nuevo")
                .doesNotContain("000000")
                .doesNotContain("�");
    }

    @Test
    void laConfirmacionDeCuentaNoDejaValoresDeEjemploSiFaltaUnaVariable() {
        // Si ms-identidad mandara el codigo vacio, la plantilla no debe rellenar
        // con el "000000" de muestra: quedaria un correo que parece valido.
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "", "minutosVigencia", 15));

        assertThat(html).doesNotContain("000000");
    }
}

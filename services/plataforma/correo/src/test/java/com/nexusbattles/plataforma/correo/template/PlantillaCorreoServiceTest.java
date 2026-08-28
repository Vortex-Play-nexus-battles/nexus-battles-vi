package com.nexusbattles.plataforma.correo.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}

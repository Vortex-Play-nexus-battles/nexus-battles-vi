package com.nexusbattles.ms_chatbot.chat.enriquecido;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.13.0 (§7.4.3 «respuestas enriquecidas: incluyendo
// imagenes»): solo las imagenes del propio catalogo, nunca otra cosa.
class ImagenDeProductoTest {

    // Como las de la semilla de DEV: un SVG pequeno, codificado en la URL.
    private static final String SVG_DE_LA_SEMILLA = "data:image/svg+xml,%3Csvg%20xmlns%3D%27http%3A%2F%2Fwww.w3.org"
        + "%2F2000%2Fsvg%27%20viewBox%3D%270%200%2064%2064%27%3E%3Crect%20width%3D%2764%27%20height%3D%2764%27%20"
        + "fill%3D%27%231E3F88%27%2F%3E%3C%2Fsvg%3E";

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f",
        SVG_DE_LA_SEMILLA,
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
        "data:image/webp;base64,UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==",
        "data:IMAGE/JPEG;base64,/9j/4AAQSkZJRg==",
    })
    void pasanLosRecursosDelCatalogoYLasImagenesIncrustadasPequenas(String referencia) {
        assertThat(ImagenDeProducto.segura(referencia)).contains(referencia);
    }

    @Test
    void seQuitanLosEspaciosDeLosBordes() {
        assertThat(ImagenDeProducto.segura("  /api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f\n"))
            .contains("/api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        // Un nombre suelto que nadie sirve (asi hay productos en DEV).
        "espada.png",
        "/img/espada.png",
        // Otro sitio: ni rastreo ni contenido mixto.
        "https://cdn.example.com/espada.png",
        "http://nexusbattlesvi.com/api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f",
        "//evil.example/espada.png",
        // Ni codigo ni otro tipo de datos.
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "data:image/svg+xml,<svg onload=alert(1)>",
        "data:image/png;base64,abc def",
        "data:image/bmp;base64,Qk0=",
        "data:image/png",
        // Un recurso que no es un id.
        "/api/v1/productos/recursos/../../admin",
        "/api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f?x=1",
    })
    void loDemasNoPasa(String referencia) {
        assertThat(ImagenDeProducto.segura(referencia)).isEmpty();
    }

    @Test
    void unaImagenIncrustadaGrandeNoPasa() {
        String grande = "data:image/png;base64," + "A".repeat(ImagenDeProducto.LARGO_MAXIMO);

        assertThat(ImagenDeProducto.segura(grande)).isEmpty();
    }
}

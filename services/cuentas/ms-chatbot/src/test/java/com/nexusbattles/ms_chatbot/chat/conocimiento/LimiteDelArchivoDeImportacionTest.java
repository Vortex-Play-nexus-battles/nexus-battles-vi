package com.nexusbattles.ms_chatbot.chat.conocimiento;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ms-chatbot.yaml 1.6.0: el archivo de importacion no pasa del maximo de
// bytes. Aqui con un maximo de 10 para no fabricar 8 MiB; con el maximo real
// y la cadena de seguridad lo prueba BaseConocimientoAdminControllerTest.
class LimiteDelArchivoDeImportacionTest {

    private static final String RUTA = LimiteDelArchivoDeImportacion.RUTA;

    private final LimiteDelArchivoDeImportacion filtro = new LimiteDelArchivoDeImportacion(10);

    @Test
    void soloMiraElPutDeLaImportacion() {
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("PUT", RUTA))).isFalse();
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("GET", RUTA))).isTrue();
        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("PUT",
            "/chatbot/admin/base-conocimiento/borrador/temas/1"))).isTrue();

        // Detras del context-path /api/v1, como en el despliegue.
        MockHttpServletRequest conContexto = new MockHttpServletRequest("PUT", "/api/v1" + RUTA);
        conContexto.setContextPath("/api/v1");
        assertThat(filtro.shouldNotFilter(conContexto)).isFalse();
    }

    @Test
    void conContentLengthMayorQueElMaximo_responde413SinLeerElCuerpo() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", RUTA);
        peticion.setContent(new byte[11]);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(413);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        JsonNode cuerpo = JsonMapper.builder().build().readTree(respuesta.getContentAsByteArray());
        assertThat(cuerpo.get("motivo").asString()).isEqualTo("IMPORTACION_DEMASIADO_GRANDE");
        assertThat(cuerpo.get("status").asInt()).isEqualTo(413);
        assertThat(cuerpo.get("title").asString()).isEqualTo("Archivo demasiado grande");
        assertThat(cuerpo.get("detail").asString()).contains("8 MB");
        assertThat(cuerpo.get("instance").asString()).isEqualTo(RUTA);
        assertThat(cadena.getRequest()).as("no llega al controlador").isNull();
    }

    @Test
    void conContentLengthDentroDelMaximo_siguePorLaCadena() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", RUTA);
        peticion.setContent(new byte[10]);
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);

        assertThat(cadena.getRequest()).isSameAs(peticion);
    }

    // Envio por trozos (sin Content-Length): se lee como mucho el maximo y un
    // byte; si hay mas, 413.
    @Test
    void sinContentLengthYConMasDelMaximo_responde413() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(porTrozos("01234567890123"), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(413);
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    void sinContentLengthYDentroDelMaximo_sigueConElCuerpoYaLeido() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(porTrozos("{\"a\":1}"), new MockHttpServletResponse(), cadena);

        HttpServletRequest siguiente = (HttpServletRequest) cadena.getRequest();
        assertThat(siguiente.getContentLengthLong()).isEqualTo(7);
        assertThat(siguiente.getContentLength()).isEqualTo(7);
        ServletInputStream entrada = siguiente.getInputStream();
        assertThat(entrada.isReady()).isTrue();
        assertThat(entrada.isFinished()).isFalse();
        assertThat(entrada.read()).isEqualTo('{');
        byte[] resto = new byte[16];
        int leidos = entrada.read(resto, 0, resto.length);
        assertThat(new String(resto, 0, leidos, StandardCharsets.UTF_8)).isEqualTo("\"a\":1}");
        assertThat(entrada.isFinished()).isTrue();
        assertThatThrownBy(() -> entrada.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
        // El lector tambien ve el cuerpo completo, en el juego de la peticion o en UTF-8.
        assertThat(siguiente.getReader().readLine()).isEqualTo("{\"a\":1}");
    }

    @Test
    void sinContentLengthElLectorUsaElJuegoDeCaracteresDeLaPeticion() throws Exception {
        MockHttpServletRequest peticion = porTrozos("ñ");
        peticion.setCharacterEncoding("ISO-8859-1");
        peticion.setContent("ñ".getBytes(StandardCharsets.ISO_8859_1));
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);

        assertThat(((HttpServletRequest) cadena.getRequest()).getReader().readLine()).isEqualTo("ñ");
    }

    // MockHttpServletRequest siempre sabe su largo; asi se parece a un envio
    // por trozos (Transfer-Encoding: chunked), donde el largo es -1.
    private static MockHttpServletRequest porTrozos(String cuerpo) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", RUTA) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        peticion.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));
        return peticion;
    }
}

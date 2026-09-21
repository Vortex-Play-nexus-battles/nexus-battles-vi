package com.nexusbattles.plataforma.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/**
 * HU-REN-001, CA-01 — la instrumentacion que captura cada peticion.
 */
class FiltroDeLatenciaTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T10:00:00Z");

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final RegistroDeLatencia registro = new RegistroDeLatencia("salas-partidas");
    private final FiltroDeLatencia filtro = new FiltroDeLatencia(registro, reloj);

    private MockHttpServletRequest peticion(String metodo, String uri, String plantilla) {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, uri);
        if (plantilla != null) {
            peticion.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, plantilla);
        }
        return peticion;
    }

    @Test
    void mideUnaPeticionCorrectaYGuardaTodosLosCamposDelInforme() throws Exception {
        MockHttpServletRequest peticion = peticion("POST", "/api/v1/salas", "/api/v1/salas");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        respuesta.setStatus(201);

        filtro.doFilter(peticion, respuesta, new MockFilterChain());

        assertThat(registro.cuantasMuestras()).isEqualTo(1);
        MuestraDeLatencia muestra = registro.muestras().get(0);
        assertThat(muestra.servicio()).isEqualTo("salas-partidas");
        assertThat(muestra.metodo()).isEqualTo("POST");
        assertThat(muestra.ruta()).isEqualTo("/api/v1/salas");
        assertThat(muestra.estado()).isEqualTo(201);
        assertThat(muestra.duracionMs()).isNotNegative();
        assertThat(muestra.instante()).isEqualTo(AHORA);
    }

    @Test
    void mideTambienLasPeticionesQueTerminanEnError() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        respuesta.setStatus(500);

        filtro.doFilter(peticion("GET", "/api/v1/salas", "/api/v1/salas"), respuesta, new MockFilterChain());

        MuestraDeLatencia muestra = registro.muestras().get(0);
        assertThat(muestra.estado()).isEqualTo(500);
        assertThat(muestra.fallo()).isTrue();
    }

    @Test
    void mideLaPeticionAunqueLaCadenaLanceUnaExcepcion() {
        // Un fallo lento es justo lo que interesa medir: si solo se midiera el
        // camino feliz, esas peticiones no apareceria en ningun percentil.
        FilterChain cadenaQueLanza = (req, res) -> {
            throw new ServletException("el controlador reviento");
        };
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        assertThatThrownBy(() ->
                        filtro.doFilter(peticion("GET", "/api/v1/salas", "/api/v1/salas"), respuesta, cadenaQueLanza))
                .isInstanceOf(ServletException.class);

        assertThat(registro.cuantasMuestras()).isEqualTo(1);
    }

    @Test
    void agrupaPorPlantillaDeRutaYNoPorLaUriConcreta() throws Exception {
        // Sin esto, cada sala seria una operacion distinta y no habria
        // percentil por operacion que calcular.
        filtro.doFilter(
                peticion("GET", "/api/v1/salas/abc-123", "/api/v1/salas/{id}"),
                new MockHttpServletResponse(),
                new MockFilterChain());
        filtro.doFilter(
                peticion("GET", "/api/v1/salas/xyz-999", "/api/v1/salas/{id}"),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(registro.muestras())
                .extracting(MuestraDeLatencia::ruta)
                .containsExactly("/api/v1/salas/{id}", "/api/v1/salas/{id}");
    }

    @Test
    void sinPlantillaConocidaCaeEnLaUriDeLaPeticion() throws Exception {
        filtro.doFilter(
                peticion("GET", "/actuator/health", null),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(registro.muestras().get(0).ruta()).isEqualTo("/actuator/health");
    }

    @Test
    void variasPeticionesSeAcumulanEnElMismoRegistro() throws Exception {
        for (int i = 0; i < 5; i++) {
            filtro.doFilter(
                    peticion("GET", "/api/v1/salas", "/api/v1/salas"),
                    new MockHttpServletResponse(),
                    new MockFilterChain());
        }

        assertThat(registro.cuantasMuestras()).isEqualTo(5);
        assertThat(registro.informe(new ObjetivoDeLatencia(500, 95), 5).muestras()).isEqualTo(5);
    }
}

package com.nexusbattles.plataforma.observabilidad;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El trace id sobrevive al salto entre servicios — regla 5 (R11).
 *
 * <p>Lo que se prueba aqui es exactamente la mitad que faltaba: hasta R11 el
 * {@code traceparent} se leia al entrar y <b>moria en la primera llamada
 * saliente</b>. Con veinte servicios, eso convierte «seguir una peticion» en
 * «buscar por hora aproximada en veinte bitacoras».
 */
@DisplayName("Regla 5: el traceparent se reenvia en la llamada saliente")
class InterceptorDeTrazaTest {

    private static final String TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final InterceptorDeTraza interceptor = new InterceptorDeTraza();

    /** Las cabeceras con las que la peticion salio de verdad. */
    private final List<HttpHeaders> salidas = new ArrayList<>();

    private final ClientHttpRequestExecution ejecucion = (peticion, cuerpo) -> {
        salidas.add(HttpHeaders.readOnlyHttpHeaders(peticion.getHeaders()));
        return null;
    };

    @AfterEach
    void limpiarElMdc() {
        MDC.clear();
    }

    private HttpHeaders enviar(HttpHeaders cabecerasIniciales) throws IOException {
        HttpRequest peticion = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://otro/api/v1/x"));
        peticion.getHeaders().addAll(cabecerasIniciales);
        ClientHttpResponse ignorada = interceptor.intercept(peticion, new byte[0], ejecucion);
        assertThat(ignorada).isNull();
        return salidas.get(salidas.size() - 1);
    }

    @Test
    @DisplayName("con traza en el MDC: sale la MISMA traza con un span nuevo")
    void reenviaLaTraza() throws Exception {
        MDC.put(FiltroDeTraza.CLAVE_MDC, TRAZA);

        String cabecera = enviar(new HttpHeaders()).getFirst(FiltroDeTraza.CABECERA);

        assertThat(cabecera).isNotNull();
        String[] partes = cabecera.split("-");
        assertThat(partes).hasSize(4);
        assertThat(partes[0]).as("version W3C").isEqualTo("00");
        assertThat(partes[1]).as("la MISMA traza: si cambiara, no habria enlace").isEqualTo(TRAZA);
        assertThat(partes[2]).as("span nuevo, 8 bytes en hexadecimal").hasSize(16).matches("[0-9a-f]{16}");
        assertThat(partes[3]).isEqualTo("01");
    }

    @Test
    @DisplayName("dos llamadas de la misma peticion comparten traza y NO comparten span")
    void mismaTrazaSpansDistintos() throws Exception {
        MDC.put(FiltroDeTraza.CLAVE_MDC, TRAZA);

        String primera = enviar(new HttpHeaders()).getFirst(FiltroDeTraza.CABECERA);
        String segunda = enviar(new HttpHeaders()).getFirst(FiltroDeTraza.CABECERA);

        assertThat(primera.split("-")[1]).isEqualTo(segunda.split("-")[1]);
        assertThat(primera.split("-")[2])
                .as("un span por llamada: si no, el arbol de la traza sale plano")
                .isNotEqualTo(segunda.split("-")[2]);
    }

    @Test
    @DisplayName("sin traza en el MDC no se inventa ninguna")
    void sinTrazaNoInventa() throws Exception {
        // Una tarea programada, un arranque, un hilo que no vino de ninguna
        // peticion. Poner un trace id nuevo aqui enlazaria dos cosas que no
        // tienen nada que ver, y eso es peor que no tener traza.
        assertThat(enviar(new HttpHeaders()).containsHeader(FiltroDeTraza.CABECERA)).isFalse();
    }

    @Test
    @DisplayName("una traza en blanco cuenta como no tener ninguna")
    void trazaEnBlancoNoCuenta() throws Exception {
        MDC.put(FiltroDeTraza.CLAVE_MDC, "   ");
        assertThat(enviar(new HttpHeaders()).containsHeader(FiltroDeTraza.CABECERA)).isFalse();
    }

    @Test
    @DisplayName("si alguien ya puso la cabecera a mano, manda la suya")
    void noPisaLaQueYaVenia() throws Exception {
        MDC.put(FiltroDeTraza.CLAVE_MDC, TRAZA);
        HttpHeaders puestaAMano = new HttpHeaders();
        puestaAMano.set(FiltroDeTraza.CABECERA, "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");

        assertThat(enviar(puestaAMano).getFirst(FiltroDeTraza.CABECERA))
                .isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
    }
}

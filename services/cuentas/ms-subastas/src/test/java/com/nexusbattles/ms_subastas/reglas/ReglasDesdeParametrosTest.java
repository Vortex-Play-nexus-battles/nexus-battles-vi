package com.nexusbattles.ms_subastas.reglas;

import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * B8 — las reglas de 7.7 que decide administracion salen de admin-parametros,
 * y el incremento minimo NO tiene respaldo por variable de entorno.
 */
class ReglasDesdeParametrosTest {

    private static final String BASE = "http://parametros/api/v1";

    private MockRestServiceServer catalogo;
    private ReglasDesdeParametros reglas;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder();
        catalogo = MockRestServiceServer.bindTo(constructor).ignoreExpectOrder(true).build();
        LectorDeParametros lector = LectorDeParametros.sobre(constructor.build(), BASE,
                Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC), Duration.ofSeconds(30));
        reglas = new ReglasDesdeParametros(lector, new ParametrosPuja(), PoliticaAlVencer.ENTREGAR);
    }

    private void parametro(String clave, String valorJson) {
        catalogo.expect(requestTo(BASE + "/parametros/" + clave + "/valor"))
                .andRespond(withSuccess("{\"clave\":\"" + clave + "\",\"valor\":" + valorJson + ",\"version\":2}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("con el catalogo fijado, manda el catalogo: incremento, topes y politica de pendientes")
    void mandaElCatalogo() {
        parametro("subastas.incremento-minimo", "\"2.50\"");
        parametro("subastas.max-subastas-activas-por-jugador", "\"12\"");
        parametro("subastas.max-pujas-activas-por-jugador", "\"40\"");
        parametro("subastas.intervalo-minimo-segundos", "\"7\"");
        parametro("subastas.pendientes.al-vencer", "\"DEVOLVER_AL_VENDEDOR\"");

        ReglasVigentes vigentes = reglas.vigentes();

        assertEquals(0, new BigDecimal("2.50").compareTo(vigentes.incrementoMinimo()));
        assertEquals(12, vigentes.maxSubastasActivasPorJugador());
        assertEquals(40, vigentes.maxPujasActivasPorJugador());
        assertEquals(7, vigentes.intervaloMinimoSegundos());
        assertEquals(PoliticaAlVencer.DEVOLVER_AL_VENDEDOR, vigentes.alVencerPendientes());
    }

    @Test
    @DisplayName("DECISION PO: sin valor en el catalogo el incremento queda sin configurar; los topes caen al documento")
    void sinValorElIncrementoNoSeInventa() {
        parametro("subastas.incremento-minimo", "null");
        parametro("subastas.max-subastas-activas-por-jugador", "\"10\"");
        parametro("subastas.max-pujas-activas-por-jugador", "\"50\"");
        parametro("subastas.intervalo-minimo-segundos", "\"5\"");
        parametro("subastas.pendientes.al-vencer", "null");

        ReglasVigentes vigentes = reglas.vigentes();

        assertNull(vigentes.incrementoMinimo());
        assertFalse(vigentes.incremento().isPresent());
        assertEquals(10, vigentes.maxSubastasActivasPorJugador());
        assertEquals(PoliticaAlVencer.ENTREGAR, vigentes.alVencerPendientes(), "respaldo provisional");
    }

    @Test
    @DisplayName("catalogo caido: topes del documento e incremento sin configurar, nunca una excepcion")
    void catalogoCaido() {
        for (String clave : new String[] {"subastas.incremento-minimo", "subastas.max-subastas-activas-por-jugador",
                "subastas.max-pujas-activas-por-jugador", "subastas.intervalo-minimo-segundos",
                "subastas.pendientes.al-vencer"}) {
            catalogo.expect(requestTo(BASE + "/parametros/" + clave + "/valor"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        ReglasVigentes vigentes = reglas.vigentes();

        assertNull(vigentes.incrementoMinimo());
        assertEquals(10, vigentes.maxSubastasActivasPorJugador());
        assertEquals(50, vigentes.maxPujasActivasPorJugador());
        assertEquals(5, vigentes.intervaloMinimoSegundos());
    }

    @Test
    @DisplayName("un incremento mal escrito o no positivo cuenta como sin configurar")
    void incrementoInvalido() {
        parametro("subastas.incremento-minimo", "\"cero\"");
        parametro("subastas.max-subastas-activas-por-jugador", "\"10\"");
        parametro("subastas.max-pujas-activas-por-jugador", "\"50\"");
        parametro("subastas.intervalo-minimo-segundos", "\"5\"");
        parametro("subastas.pendientes.al-vencer", "\"ENTREGAR\"");
        assertNull(reglas.vigentes().incrementoMinimo());
    }

    @Test
    @DisplayName("un incremento negativo en el catalogo tampoco se acepta")
    void incrementoNegativo() {
        parametro("subastas.incremento-minimo", "\"-1\"");
        parametro("subastas.max-subastas-activas-por-jugador", "\"10\"");
        parametro("subastas.max-pujas-activas-por-jugador", "\"50\"");
        parametro("subastas.intervalo-minimo-segundos", "\"5\"");
        parametro("subastas.pendientes.al-vencer", "\"ENTREGAR\"");
        assertNull(reglas.vigentes().incrementoMinimo());
    }

    @Test
    @DisplayName("las reglas fijas de las pruebas usan los topes del entorno")
    void reglasFijas() {
        ParametrosPuja parametros = new ParametrosPuja();
        parametros.setMaxPujasActivasPorJugador(3);
        ReglasVigentes fijas = FuenteDeReglas.fijas(parametros, BigDecimal.ONE).vigentes();
        assertEquals(3, fijas.maxPujasActivasPorJugador());
        assertEquals(BigDecimal.ONE, fijas.incrementoMinimo());
        assertNull(FuenteDeReglas.fijas(parametros).vigentes().incrementoMinimo());
    }

    @Test
    @DisplayName("un incremento no positivo no se puede ni construir")
    void reglasVigentesRechazaIncrementoNoPositivo() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReglasVigentes(BigDecimal.ZERO, 10, 50, 5, PoliticaAlVencer.ENTREGAR));
    }

    @Test
    @DisplayName("penalizacion de cancelar: 50 % de la comision a dos decimales; sin comision, cero")
    void penalizacion() {
        assertEquals(new BigDecimal("0.50"), ReglasDelDocumento.penalizacionDeCancelacion(BigDecimal.ONE));
        assertEquals(new BigDecimal("1.50"), ReglasDelDocumento.penalizacionDeCancelacion(new BigDecimal("3")));
        assertEquals(new BigDecimal("0.00"), ReglasDelDocumento.penalizacionDeCancelacion(null));
        assertEquals(new BigDecimal("0.00"), ReglasDelDocumento.penalizacionDeCancelacion(BigDecimal.ZERO));
    }
}

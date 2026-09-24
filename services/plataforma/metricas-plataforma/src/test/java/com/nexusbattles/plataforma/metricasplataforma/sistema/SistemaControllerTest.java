package com.nexusbattles.plataforma.metricasplataforma.sistema;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.SondaDeSalud;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El estado de los servicios que ve la consola.
 *
 * ## Lo que se prueba
 *
 * Que los cuatro desenlaces son cuatro cosas distintas. Un servicio fuera del
 * host por capacidad, uno que vive en otro host y no se alcanza, uno que
 * deberia responder y no responde, y uno que responde, NO son el mismo hecho,
 * y la pantalla que dice si el sistema esta sano es el peor sitio para
 * confundirlos.
 */
class SistemaControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T02:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    /** Responde bien solo a las URL que se le digan. */
    private static SondaDeSalud sondaQueAcepta(String... urlsSanas) {
        return (servicio, url, instante) -> {
            for (String sana : urlsSanas) {
                if (sana.equals(url)) {
                    return Comprobacion.disponible(servicio, instante);
                }
            }
            return Comprobacion.caido(servicio, instante, "Connection refused");
        };
    }

    private static ConfiguracionDelSistema configuracion(Map<String, String> servicios) {
        return new ConfiguracionDelSistema(new LinkedHashMap<>(servicios));
    }

    @Test
    void distingueLosCuatroDesenlaces() {
        ConfiguracionDelSistema configuracion =
                configuracion(
                        Map.of(
                                "torneos", "http://srv-torneos:8083/actuator/health",
                                "ms-subastas", "http://srv-ms-subastas:8092/api/v1/actuator/health",
                                "ms-chatbot", ConfiguracionDelSistema.NO_DESPLEGADO,
                                "heroes", ConfiguracionDelSistema.NO_OBSERVABLE));
        SistemaController controlador =
                new SistemaController(
                        configuracion,
                        sondaQueAcepta("http://srv-torneos:8083/actuator/health"),
                        RELOJ);

        SistemaController.RespuestaDelSistema respuesta = controlador.servicios();

        assertEquals(4, respuesta.total());
        assertEquals(1, respuesta.operativos());
        assertEquals(1, respuesta.caidos());
        assertEquals(1, respuesta.noDesplegados());
        assertEquals(1, respuesta.noObservables());
        assertEquals(AHORA, respuesta.instante());
    }

    @Test
    void cadaServicioSaleConSuNombreYSuMotivo() {
        SistemaController controlador =
                new SistemaController(
                        configuracion(Map.of("ms-chatbot", ConfiguracionDelSistema.NO_DESPLEGADO)),
                        sondaQueAcepta(),
                        RELOJ);

        EstadoDeServicio estado = controlador.servicios().servicios().get(0);

        assertEquals("ms-chatbot", estado.servicio());
        assertEquals(EstadoDeServicio.NO_DESPLEGADO, estado.estado());
        assertTrue(estado.detalle().contains("capacidad"), "el motivo tiene que decir por que");
    }

    /**
     * Un servicio de otro host no es un servicio caido. Salir en rojo diria
     * que se rompio algo cuando lo unico cierto es que esta sonda no llega.
     */
    @Test
    void unServicioDeOtroHostNoSePintaComoCaido() {
        SistemaController controlador =
                new SistemaController(
                        configuracion(Map.of("heroes", ConfiguracionDelSistema.NO_OBSERVABLE)),
                        sondaQueAcepta(),
                        RELOJ);

        EstadoDeServicio estado = controlador.servicios().servicios().get(0);

        assertEquals(EstadoDeServicio.NO_OBSERVABLE, estado.estado());
        assertTrue(estado.detalle().contains("otro host"));
    }

    @Test
    void unaUrlVaciaCuentaComoNoDesplegado() {
        SistemaController controlador =
                new SistemaController(configuracion(Map.of("algo", "  ")), sondaQueAcepta(), RELOJ);

        assertEquals(
                EstadoDeServicio.NO_DESPLEGADO,
                controlador.servicios().servicios().get(0).estado());
    }

    @Test
    void elDetalleDelFalloLlegaTalCual() {
        SistemaController controlador =
                new SistemaController(
                        configuracion(Map.of("torneos", "http://srv-torneos:8083/actuator/health")),
                        sondaQueAcepta(),
                        RELOJ);

        EstadoDeServicio estado = controlador.servicios().servicios().get(0);

        assertEquals(EstadoDeServicio.CAIDO, estado.estado());
        assertEquals("Connection refused", estado.detalle());
    }

    @Test
    void sinServiciosConfiguradosNoRevienta() {
        SistemaController controlador =
                new SistemaController(new ConfiguracionDelSistema(null), sondaQueAcepta(), RELOJ);

        SistemaController.RespuestaDelSistema respuesta = controlador.servicios();

        assertEquals(0, respuesta.total());
        assertTrue(respuesta.servicios().isEmpty());
    }
}
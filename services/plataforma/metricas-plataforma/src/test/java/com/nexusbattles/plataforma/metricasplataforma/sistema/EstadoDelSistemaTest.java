package com.nexusbattles.plataforma.metricasplataforma.sistema;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.SondaDeSalud;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El estado de los servicios que ve la consola.
 *
 * ## Lo que se prueba
 *
 * Que los cinco desenlaces son cinco cosas distintas. Un servicio fuera del
 * host por capacidad, uno que vive en otro host sin sonda, uno que deberia
 * responder y no responde, uno que conecta pero no contesta a tiempo y uno
 * que responde NO son el mismo hecho, y la pantalla que dice si el sistema
 * esta sano es el peor sitio para confundirlos.
 *
 * Y, desde RFINAL-08, cuanto tarda: en DEV el 4-oct la pantalla tardaba
 * 8440 ms porque trece sondas iban una detras de otra.
 */
class EstadoDelSistemaTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T02:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private RondaEnParalelo ronda = RondaEnParalelo.acotada(16, Duration.ofSeconds(3));

    @AfterEach
    void cerrar() {
        ronda.close();
    }

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

    private EstadoDelSistema estado(ConfiguracionDelSistema configuracion, SondaDeSalud sonda) {
        return new EstadoDelSistema(configuracion, sonda, ronda, new ResultadoReciente<>(Duration.ZERO), RELOJ);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void distingueLosCincoDesenlaces() {
        Map<String, String> servicios = new LinkedHashMap<>();
        servicios.put("torneos", "http://srv-torneos:8083/actuator/health");
        servicios.put("ms-subastas", "http://34.193.90.11:8092/api/v1/actuator/health");
        servicios.put("misiones", "http://34.193.90.11:8105/actuator/health");
        servicios.put("ms-chatbot", ConfiguracionDelSistema.NO_DESPLEGADO);
        servicios.put("heroes", ConfiguracionDelSistema.NO_OBSERVABLE);
        SondaDeSalud sonda = (servicio, url, instante) -> switch (servicio) {
            case "torneos" -> Comprobacion.disponible(servicio, instante);
            case "misiones" -> Comprobacion.sinRespuesta(servicio, instante, "sin respuesta en 1500 ms");
            default -> Comprobacion.caido(servicio, instante, "conexión rechazada");
        };

        RespuestaDelSistema respuesta = estado(configuracion(servicios), sonda).consultar();

        assertEquals(5, respuesta.total());
        assertEquals(1, respuesta.operativos());
        assertEquals(1, respuesta.caidos());
        assertEquals(1, respuesta.lentos());
        assertEquals(1, respuesta.noDesplegados());
        assertEquals(1, respuesta.noObservables());
        assertEquals(AHORA, respuesta.instante());
        assertFalse(respuesta.desdeCache());
        assertEquals(
                List.of("torneos", "ms-subastas", "misiones", "ms-chatbot", "heroes"),
                respuesta.servicios().stream().map(EstadoDeServicio::servicio).toList(),
                "el orden de la pantalla es el de la configuracion");
    }

    @Test
    void cadaServicioSaleConSuNombreYSuMotivo() {
        EstadoDeServicio estado = estado(
                configuracion(Map.of("ms-chatbot", ConfiguracionDelSistema.NO_DESPLEGADO)), sondaQueAcepta())
                .consultar().servicios().get(0);

        assertEquals("ms-chatbot", estado.servicio());
        assertEquals(EstadoDeServicio.NO_DESPLEGADO, estado.estado());
        assertTrue(estado.detalle().contains("capacidad"), "el motivo tiene que decir por que");
    }

    /**
     * Un servicio de otro host sin sonda no es un servicio caido. Salir en rojo
     * diria que se rompio algo cuando lo unico cierto es que nadie lo sondea.
     */
    @Test
    void unServicioDeOtroHostSinSondaNoSePintaComoCaido() {
        EstadoDeServicio estado = estado(
                configuracion(Map.of("heroes", ConfiguracionDelSistema.NO_OBSERVABLE)), sondaQueAcepta())
                .consultar().servicios().get(0);

        assertEquals(EstadoDeServicio.NO_OBSERVABLE, estado.estado());
        assertTrue(estado.detalle().contains("otro host"));
    }

    @Test
    void unaUrlVaciaCuentaComoNoDesplegado() {
        assertEquals(
                EstadoDeServicio.NO_DESPLEGADO,
                estado(configuracion(Map.of("algo", "  ")), sondaQueAcepta()).consultar().servicios().get(0).estado());
    }

    @Test
    void elDetalleDelFalloLlegaTalCual() {
        EstadoDeServicio estado = estado(
                configuracion(Map.of("torneos", "http://srv-torneos:8083/actuator/health")), sondaQueAcepta())
                .consultar().servicios().get(0);

        assertEquals(EstadoDeServicio.CAIDO, estado.estado());
        assertEquals("Connection refused", estado.detalle());
    }

    @Test
    void sinServiciosConfiguradosNoRevienta() {
        RespuestaDelSistema respuesta = estado(new ConfiguracionDelSistema(null), sondaQueAcepta()).consultar();

        assertEquals(0, respuesta.total());
        assertTrue(respuesta.servicios().isEmpty());
    }

    @Test
    @DisplayName("una sonda que lanza sale CAIDO con su motivo y no tumba la ronda")
    void unaSondaQueLanzaSaleCaida() {
        Map<String, String> servicios = new LinkedHashMap<>();
        servicios.put("torneos", "http://srv-torneos:8083/actuator/health");
        servicios.put("correo", "http://srv-correo:8082/actuator/health");
        SondaDeSalud sonda = (servicio, url, instante) -> {
            if ("correo".equals(servicio)) {
                throw new IllegalStateException("sonda rota");
            }
            return Comprobacion.disponible(servicio, instante);
        };

        RespuestaDelSistema respuesta = estado(configuracion(servicios), sonda).consultar();

        assertEquals(EstadoDeServicio.OPERATIVO, respuesta.servicios().get(0).estado());
        assertEquals(EstadoDeServicio.CAIDO, respuesta.servicios().get(1).estado());
        assertEquals("la sonda falló: sonda rota", respuesta.servicios().get(1).detalle());
    }

    /**
     * El numero del informe de RFINAL-08: con las trece sondas que la pantalla
     * hacia en serie, cada una de 1 s, la respuesta tarda lo que una, no trece.
     */
    @Test
    @DisplayName("trece sondas de 1 s: la pantalla responde en ~1 s, no en 13 s")
    void treceSondasDeUnSegundoTardanUnSegundo() {
        Map<String, String> servicios = new LinkedHashMap<>();
        for (int i = 1; i <= 13; i++) {
            servicios.put("servicio-" + i, "http://srv-" + i + ":8080/actuator/health");
        }
        SondaDeSalud deUnSegundo = (servicio, url, instante) -> {
            dormir(1000);
            return Comprobacion.disponible(servicio, instante);
        };

        long inicio = System.nanoTime();
        RespuestaDelSistema respuesta = estado(configuracion(servicios), deUnSegundo).consultar();
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        System.out.printf("RFINAL-08 /admin/sistema/servicios: 13 sondas de 1000 ms en %d ms%n", ms);
        assertEquals(13, respuesta.operativos());
        assertTrue(ms < 2000, "13 sondas de 1 s tardaron " + ms + " ms");
    }

    @Test
    @DisplayName("una sonda colgada sale LENTO al vencer el plazo de la ronda y no retrasa a las demas")
    void unaSondaColgadaSaleLentaSinRetrasarAlResto() {
        ronda.close();
        ronda = RondaEnParalelo.acotada(4, Duration.ofMillis(400));
        Map<String, String> servicios = new LinkedHashMap<>();
        servicios.put("torneos", "http://srv-torneos:8083/actuator/health");
        servicios.put("colgado", "http://srv-colgado:8080/actuator/health");
        SondaDeSalud sonda = (servicio, url, instante) -> {
            if ("colgado".equals(servicio)) {
                dormir(5000);
            }
            return Comprobacion.disponible(servicio, instante);
        };

        long inicio = System.nanoTime();
        RespuestaDelSistema respuesta = estado(configuracion(servicios), sonda).consultar();
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertEquals(EstadoDeServicio.OPERATIVO, respuesta.servicios().get(0).estado());
        EstadoDeServicio colgado = respuesta.servicios().get(1);
        assertEquals(EstadoDeServicio.LENTO, colgado.estado());
        assertEquals("No respondió en 400 ms; no se espera más para no retrasar al resto.", colgado.detalle());
        assertEquals(1, respuesta.lentos());
        assertTrue(ms < 1500, "la ronda tardo " + ms + " ms");
    }

    @Test
    @DisplayName("la espera agotada que reporta la propia sonda tambien es LENTO, con su motivo")
    void esperaAgotadaDeLaSondaEsLento() {
        SondaDeSalud sonda = (servicio, url, instante) ->
                Comprobacion.sinRespuesta(servicio, instante, "sin respuesta en 1500 ms");

        EstadoDeServicio estado = estado(
                configuracion(Map.of("salas-partidas", "http://srv-salas-partidas:8084/actuator/health")), sonda)
                .consultar().servicios().get(0);

        assertEquals(EstadoDeServicio.LENTO, estado.estado());
        assertEquals("sin respuesta en 1500 ms", estado.detalle());
    }

    @Test
    @DisplayName("dentro de la vigencia la segunda consulta reutiliza la ronda y lo dice")
    void laSegundaConsultaReutilizaLaRonda() {
        AtomicInteger sondeos = new AtomicInteger();
        SondaDeSalud cuenta = (servicio, url, instante) -> {
            sondeos.incrementAndGet();
            return Comprobacion.disponible(servicio, instante);
        };
        Map<String, String> servicios = new LinkedHashMap<>();
        servicios.put("torneos", "http://srv-torneos:8083/actuator/health");
        servicios.put("correo", "http://srv-correo:8082/actuator/health");
        EstadoDelSistema estado = new EstadoDelSistema(configuracion(servicios), cuenta, ronda,
                new ResultadoReciente<>(Duration.ofSeconds(60)), RELOJ);

        RespuestaDelSistema primera = estado.consultar();
        RespuestaDelSistema segunda = estado.consultar();

        assertFalse(primera.desdeCache());
        assertTrue(segunda.desdeCache());
        assertEquals(primera.instante(), segunda.instante(), "la hora es la de la ronda que se reutiliza");
        assertEquals(primera.servicios(), segunda.servicios());
        assertEquals(2, sondeos.get(), "una sola ronda: una sonda por servicio");
    }
}

package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.AlertasEnBitacora;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.RegistroDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFINAL-08 — el tablero tecnico (HU-MET-004) recolecta de todos a la vez.
 *
 * <p>En DEV el 4-oct {@code GET /tecnicas} tardaba 7293 ms: siete servicios por
 * cuatro lecturas de Actuator, las veintiocho en serie.
 */
@DisplayName("Tablero tecnico en vivo: en paralelo, con tope y con vigencia (RFINAL-08)")
class TableroEnVivoTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private RondaEnParalelo ronda = RondaEnParalelo.acotada(16, Duration.ofSeconds(3));

    @AfterEach
    void cerrar() {
        ronda.close();
    }

    private static ConfiguracionDeDisponibilidad siete() {
        Map<String, String> servicios = new LinkedHashMap<>();
        for (String s : new String[] {"comentarios", "correo", "torneos", "salas-partidas", "notificaciones",
                "moderacion-sanciones", "admin-parametros"}) {
            servicios.put(s, "http://srv-" + s + ":8080/actuator/health");
        }
        return new ConfiguracionDeDisponibilidad(servicios, 99.95, 30000);
    }

    private static MonitorDeDisponibilidad monitorSano(ConfiguracionDeDisponibilidad configuracion) {
        MonitorDeDisponibilidad monitor = new MonitorDeDisponibilidad(configuracion,
                (servicio, url, instante) -> Comprobacion.disponible(servicio, instante),
                new RegistroDeDisponibilidad(), new AlertasEnBitacora(), RELOJ);
        monitor.comprobarTodos();
        return monitor;
    }

    private TableroEnVivo tablero(ConfiguracionDeDisponibilidad configuracion, RecolectorDeMetricas recolector,
                                  Duration vigencia) {
        return new TableroEnVivo(configuracion, recolector, monitorSano(configuracion), new PropiedadesDeLatencia(),
                ronda, new ResultadoReciente<>(vigencia), RELOJ);
    }

    private static MetricasDeServicio sanas(String servicio) {
        return new MetricasDeServicio(servicio, 0.1, 100d, 10, 5d, 9d, 0, null);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("siete recolecciones de 1 s: el tablero responde en ~1 s, no en 7 s, y en el orden configurado")
    void sieteDeUnSegundoTardanUnSegundo() {
        RecolectorDeMetricas deUnSegundo = (servicio, url) -> {
            dormir(1000);
            return sanas(servicio);
        };

        long inicio = System.nanoTime();
        TableroTecnico t = tablero(siete(), deUnSegundo, Duration.ZERO).actual();
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        System.out.printf("RFINAL-08 /tecnicas: 7 recolecciones de 1000 ms en %d ms%n", ms);
        assertThat(t.servicios()).extracting(MetricasDeServicio::servicio)
                .containsExactly("comentarios", "correo", "torneos", "salas-partidas", "notificaciones",
                        "moderacion-sanciones", "admin-parametros");
        assertThat(t.brechas()).isEmpty();
        assertThat(ms).isLessThan(2000);
    }

    @Test
    @DisplayName("un servicio que no termina a tiempo es una brecha con su motivo y no retrasa al resto")
    void unoColgadoEsBrechaSinRetrasar() {
        ronda.close();
        ronda = RondaEnParalelo.acotada(8, Duration.ofMillis(400));
        RecolectorDeMetricas recolector = (servicio, url) -> {
            if ("correo".equals(servicio)) {
                dormir(5000);
            }
            return sanas(servicio);
        };

        long inicio = System.nanoTime();
        TableroTecnico t = tablero(siete(), recolector, Duration.ZERO).actual();
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(t.servicios().get(1).brecha()).isEqualTo("no respondió en 400 ms");
        assertThat(t.brechas()).containsExactly("correo: no respondió en 400 ms");
        assertThat(t.servicios().get(0).recolectado()).isTrue();
        assertThat(ms).isLessThan(1500);
    }

    @Test
    @DisplayName("un recolector que lanza deja una brecha, no un 500")
    void unRecolectorQueLanzaDejaBrecha() {
        RecolectorDeMetricas recolector = (servicio, url) -> {
            if ("torneos".equals(servicio)) {
                throw new IllegalStateException("recolector roto");
            }
            return sanas(servicio);
        };

        TableroTecnico t = tablero(siete(), recolector, Duration.ZERO).actual();

        assertThat(t.brechas()).containsExactly("torneos: recolector roto");
    }

    @Test
    @DisplayName("dentro de la vigencia el tablero se reutiliza y lo dice; JSON y texto salen de la misma ronda")
    void dentroDeLaVigenciaSeReutiliza() {
        AtomicInteger recolecciones = new AtomicInteger();
        RecolectorDeMetricas cuenta = (servicio, url) -> {
            recolecciones.incrementAndGet();
            return sanas(servicio);
        };
        TableroEnVivo enVivo = tablero(siete(), cuenta, Duration.ofSeconds(60));

        TableroTecnico primero = enVivo.actual();
        TableroTecnico segundo = enVivo.actual();

        assertThat(primero.desdeCache()).isFalse();
        assertThat(segundo.desdeCache()).isTrue();
        assertThat(segundo.generadoEn()).isEqualTo(primero.generadoEn());
        assertThat(segundo.comoTexto()).isEqualTo(primero.comoTexto());
        assertThat(recolecciones.get()).isEqualTo(7);
    }
}

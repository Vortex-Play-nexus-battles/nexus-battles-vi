package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * HU-DIS-001 — la ronda de comprobaciones y las alertas.
 *
 * <p>Con sonda y reloj falsos, sin red ni esperas: se comprueba que se miden
 * TODOS los servicios del bloque (CA-01), que cada resultado queda registrado
 * (CA-02) y que la alerta salta cuando toca y no cuando no (CA-03).
 */
class MonitorDeDisponibilidadTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    /** Reloj que avanza solo cuando la prueba lo pide. */
    private static final class RelojFalso extends Clock {
        private Instant ahora = T0;

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }
    }

    /** Sonda falsa: se le dice que servicios estan caidos en cada momento. */
    private static final class SondaFalsa implements SondaDeSalud {
        private Set<String> caidos = Set.of();
        private final List<String> consultados = new ArrayList<>();

        @Override
        public Comprobacion comprobar(String servicio, String url, Instant instante) {
            consultados.add(servicio);
            return caidos.contains(servicio)
                    ? Comprobacion.caido(servicio, instante, "simulado")
                    : Comprobacion.disponible(servicio, instante);
        }
    }

    /** Alertas falsas: guardan lo que se dispararia. */
    private static final class AlertasFalsas implements Alertas {
        private final List<String> caidos = new ArrayList<>();
        private final List<Double> bajoUmbral = new ArrayList<>();

        @Override
        public void servicioCaido(String servicio, String detalle) {
            caidos.add(servicio);
        }

        @Override
        public void disponibilidadBajoUmbral(double porcentaje, double umbral) {
            bajoUmbral.add(porcentaje);
        }
    }

    private final RelojFalso reloj = new RelojFalso();
    private final SondaFalsa sonda = new SondaFalsa();
    private final AlertasFalsas alertas = new AlertasFalsas();
    private final RegistroDeDisponibilidad registro = new RegistroDeDisponibilidad();

    private MonitorDeDisponibilidad monitorCon(String... servicios) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String servicio : servicios) {
            mapa.put(servicio, "http://localhost/actuator/health");
        }
        ConfiguracionDeDisponibilidad configuracion =
                new ConfiguracionDeDisponibilidad(mapa, 99.95, 30_000);
        return new MonitorDeDisponibilidad(configuracion, sonda, registro, alertas, reloj);
    }

    @Test
    void compruebaTodosLosServiciosDelBloqueEnCadaRonda() {
        // CP-01: el 100 % de los servicios desplegados reporta su disponibilidad.
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas", "correo", "notificaciones");

        List<Comprobacion> ronda = monitor.comprobarTodos();

        assertThat(sonda.consultados)
                .containsExactly("salas-partidas", "correo", "notificaciones");
        assertThat(ronda).hasSize(3);
        assertThat(monitor.estadoActual()).hasSize(3);
    }

    @Test
    void alertaLaPrimeraVezQueUnServicioSanoDejaDeResponder() {
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");
        monitor.comprobarTodos();

        sonda.caidos = Set.of("salas-partidas");
        reloj.avanzar(Duration.ofMinutes(1));
        monitor.comprobarTodos();

        assertThat(alertas.caidos).containsExactly("salas-partidas");
    }

    @Test
    void noRepiteLaAlertaMientrasElServicioSigueCaido() {
        // Alertar en cada ronda convertiria la alerta en ruido.
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");
        monitor.comprobarTodos();
        sonda.caidos = Set.of("salas-partidas");

        for (int ronda = 0; ronda < 5; ronda++) {
            reloj.avanzar(Duration.ofMinutes(1));
            monitor.comprobarTodos();
        }

        assertThat(alertas.caidos).containsExactly("salas-partidas");
    }

    @Test
    void vuelveAAlertarSiElServicioSeRecuperaYCaeDeNuevo() {
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");
        monitor.comprobarTodos();

        sonda.caidos = Set.of("salas-partidas");
        reloj.avanzar(Duration.ofMinutes(1));
        monitor.comprobarTodos();

        sonda.caidos = Set.of();
        reloj.avanzar(Duration.ofMinutes(1));
        monitor.comprobarTodos();

        sonda.caidos = Set.of("salas-partidas");
        reloj.avanzar(Duration.ofMinutes(1));
        monitor.comprobarTodos();

        assertThat(alertas.caidos).containsExactly("salas-partidas", "salas-partidas");
    }

    @Test
    void noAlertaEnLaPrimeraRondaPorNoTenerHistorial() {
        // Un servicio que ya estaba caido cuando arranca el monitor no genera
        // una alerta de "acaba de caerse": no es informacion nueva.
        sonda.caidos = Set.of("salas-partidas");
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");

        monitor.comprobarTodos();

        assertThat(alertas.caidos).isEmpty();
        assertThat(monitor.estadoActual().get(0).disponible()).isFalse();
    }

    @Test
    void unServicioCaidoNoImpideMedirElResto() {
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas", "correo");
        sonda.caidos = Set.of("salas-partidas");

        monitor.comprobarTodos();

        assertThat(monitor.estadoActual())
                .extracting(Comprobacion::servicio)
                .containsExactly("salas-partidas", "correo");
    }

    @Test
    void elInformeDisparaLaAlertaCuandoLaDisponibilidadCaeBajoElUmbral() {
        // CP-03: cuando la disponibilidad medida cae por debajo del 99,95 %,
        // se dispara una alerta.
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");
        monitor.comprobarTodos();

        sonda.caidos = Set.of("salas-partidas");
        reloj.avanzar(Duration.ofMinutes(1));
        monitor.comprobarTodos();

        sonda.caidos = Set.of();
        reloj.avanzar(Duration.ofHours(2));
        monitor.comprobarTodos();

        InformeDeDisponibilidad informe = monitor.informe(T0, reloj.instant());

        assertThat(informe.cumpleElUmbral()).isFalse();
        assertThat(alertas.bajoUmbral).hasSize(1);
    }

    @Test
    void elInformeNoAlertaCuandoSeCumpleElUmbral() {
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");
        monitor.comprobarTodos();
        reloj.avanzar(Duration.ofDays(30));

        InformeDeDisponibilidad informe = monitor.informeMensual();

        assertThat(informe.cumpleElUmbral()).isTrue();
        assertThat(alertas.bajoUmbral).isEmpty();
    }

    @Test
    void elInformeMensualCubreLosUltimosTreintaDias() {
        // DEC-01 habla de un umbral mensual.
        MonitorDeDisponibilidad monitor = monitorCon("salas-partidas");
        reloj.avanzar(Duration.ofDays(45));

        InformeDeDisponibilidad informe = monitor.informeMensual();

        assertThat(informe.periodo()).isEqualTo(Duration.ofDays(30));
        assertThat(informe.hasta()).isEqualTo(reloj.instant());
    }
}

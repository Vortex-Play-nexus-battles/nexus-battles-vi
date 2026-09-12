package com.nexusbattles.plataforma.resiliencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * HU-DIS-003 — inyeccion de fallos sobre el corta circuitos.
 *
 * <p>Estas pruebas son CP-01 y CP-03 al nivel que se puede automatizar sin
 * contenedores: se inyecta el fallo haciendo que la dependencia lance, y se
 * comprueba que el llamante sigue sirviendo y que las demas dependencias no se
 * enteran. Apagar contenedores de verdad (SCRUM-1148) es otra prueba, y es de
 * entorno.
 */
class CortaCircuitosTest {

    private static final Instant INICIO = Instant.parse("2026-09-11T10:00:00Z");

    /** Reloj que se mueve a mano: nada de dormir el hilo de la prueba. */
    private static final class RelojDeMano extends Clock {
        private Instant ahora = INICIO;

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

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
    }

    private final RelojDeMano reloj = new RelojDeMano();
    private final RegistroDeDegradacion registro = new RegistroDeDegradacion();

    private CortaCircuitos corta(String dependencia, String seccion) {
        return new CortaCircuitos(dependencia, seccion, 3, Duration.ofSeconds(30), reloj, registro);
    }

    private static <T> java.util.function.Supplier<T> queFalla() {
        return () -> {
            throw new IllegalStateException("connection refused");
        };
    }

    @Test
    void mientrasLaDependenciaRespondeElCortaNiSeNota() {
        CortaCircuitos inventario = corta("inventario", "Inventario");

        assertThat(inventario.ejecutar(() -> "espada", () -> "sin datos")).isEqualTo("espada");
        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.CERRADO);
        assertThat(registro.sinDegradaciones()).isTrue();
    }

    @Test
    void unFalloAisladoNoAbreElCircuito() {
        // La red pierde un paquete de vez en cuando. Abrir por eso dejaria sin
        // servicio una seccion que en realidad funciona.
        CortaCircuitos inventario = corta("inventario", "Inventario");

        inventario.ejecutar(queFalla(), () -> "contingencia");

        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.CERRADO);
        assertThat(registro.sinDegradaciones()).isTrue();
    }

    @Test
    void trasVariosFallosSeguidosElCircuitoSeAbreYDejaDeLlamar() {
        // CP-01: esto es lo que evita la caida total. Seguir llamando a un
        // servicio caido consume un hilo y un tiempo de espera por intento, y
        // el que acaba sin hilos es el que llama.
        AtomicInteger intentos = new AtomicInteger();
        CortaCircuitos inventario = corta("inventario", "Inventario");

        for (int i = 0; i < 3; i++) {
            inventario.ejecutar(() -> {
                intentos.incrementAndGet();
                throw new IllegalStateException("connection refused");
            }, () -> "contingencia");
        }
        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.ABIERTO);

        for (int i = 0; i < 50; i++) {
            inventario.ejecutar(() -> {
                intentos.incrementAndGet();
                return "no deberia llegar";
            }, () -> "contingencia");
        }

        assertThat(intentos.get())
                .as("con el circuito abierto no se vuelve a llamar a la dependencia")
                .isEqualTo(3);
    }

    @Test
    void conElCircuitoAbiertoElLlamanteSigueRespondiendoConLaContingencia() {
        // CA-01 y CA-03: el llamante NUNCA ve la excepcion de la dependencia,
        // asi que sigue sirviendo sus otras funciones con normalidad.
        CortaCircuitos inventario = corta("inventario", "Inventario");

        String respuesta = null;
        for (int i = 0; i < 5; i++) {
            respuesta = inventario.ejecutar(queFalla(), () -> "Inventario no disponible temporalmente");
        }

        assertThat(respuesta).isEqualTo("Inventario no disponible temporalmente");
    }

    @Test
    void laCaidaDeUnaDependenciaNoAfectaALasDemas() {
        // CP-03 literal: «cuando se consultan los demas modulos, entonces
        // siguen respondiendo con normalidad».
        CortaCircuitos inventario = corta("inventario", "Inventario");
        CortaCircuitos correo = corta("correo", "Correo");

        for (int i = 0; i < 5; i++) {
            inventario.ejecutar(queFalla(), () -> "contingencia");
        }

        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.ABIERTO);
        assertThat(correo.estado()).isEqualTo(EstadoDelCorta.CERRADO);
        assertThat(correo.ejecutar(() -> "enviado", () -> "contingencia")).isEqualTo("enviado");
        assertThat(registro.activas()).extracting(RegistroDeDegradacion.Degradacion::dependencia)
                .containsExactly("inventario");
    }

    @Test
    void pasadaLaEsperaSeDejaPasarUnaSolaLlamadaDePrueba() {
        AtomicInteger intentos = new AtomicInteger();
        CortaCircuitos inventario = corta("inventario", "Inventario");
        for (int i = 0; i < 3; i++) {
            inventario.ejecutar(queFalla(), () -> "contingencia");
        }

        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.SEMIABIERTO);

        for (int i = 0; i < 10; i++) {
            inventario.ejecutar(() -> {
                intentos.incrementAndGet();
                throw new IllegalStateException("sigue caido");
            }, () -> "contingencia");
        }

        // Una sola, no diez: si pasaran todas, una avalancha caeria sobre un
        // servicio que quiza aun se esta levantando.
        assertThat(intentos.get()).isEqualTo(1);
    }

    @Test
    void siLaLlamadaDePruebaVaBienElCircuitoSeCierraYLaSeccionVuelve() {
        CortaCircuitos inventario = corta("inventario", "Inventario");
        for (int i = 0; i < 3; i++) {
            inventario.ejecutar(queFalla(), () -> "contingencia");
        }
        assertThat(registro.estaDegradada("inventario")).isTrue();

        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(inventario.ejecutar(() -> "espada", () -> "contingencia")).isEqualTo("espada");

        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.CERRADO);
        assertThat(registro.sinDegradaciones()).isTrue();
    }

    @Test
    void siLaLlamadaDePruebaFallaSeVuelveAAbrirYSeEsperaDeNuevo() {
        CortaCircuitos inventario = corta("inventario", "Inventario");
        for (int i = 0; i < 3; i++) {
            inventario.ejecutar(queFalla(), () -> "contingencia");
        }

        reloj.avanzar(Duration.ofSeconds(30));
        inventario.ejecutar(queFalla(), () -> "contingencia");

        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.ABIERTO);
        reloj.avanzar(Duration.ofSeconds(29));
        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.ABIERTO);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(inventario.estado()).isEqualTo(EstadoDelCorta.SEMIABIERTO);
    }

    @Test
    void unExitoDespuesDeUnFalloSueltoReiniciaLaCuenta() {
        CortaCircuitos inventario = corta("inventario", "Inventario");

        inventario.ejecutar(queFalla(), () -> "contingencia");
        inventario.ejecutar(queFalla(), () -> "contingencia");
        inventario.ejecutar(() -> "espada", () -> "contingencia");
        inventario.ejecutar(queFalla(), () -> "contingencia");
        inventario.ejecutar(queFalla(), () -> "contingencia");

        assertThat(inventario.estado())
                .as("son fallos SEGUIDOS, no fallos acumulados desde que arranco el servicio")
                .isEqualTo(EstadoDelCorta.CERRADO);
    }

    @Test
    void cuandoNoHayContingenciaRazonableSeLanzaDependenciaDegradadaConLaSeccion() {
        // CA-02: sin el nombre de la seccion, el frontend solo podria decir
        // «algo fallo», y el criterio pide que el jugador vea QUE funcion esta
        // limitada.
        CortaCircuitos inventario = corta("inventario", "Inventario");

        assertThatThrownBy(() -> inventario.ejecutarOFallar(queFalla()))
                .isInstanceOf(DependenciaDegradada.class)
                .hasFieldOrPropertyWithValue("seccion", "Inventario")
                .hasFieldOrPropertyWithValue("dependencia", "inventario")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void conElCircuitoAbiertoLaDegradadaNoLlevaCausaPorqueNoHuboLlamada() {
        CortaCircuitos inventario = corta("inventario", "Inventario");
        for (int i = 0; i < 3; i++) {
            inventario.ejecutar(queFalla(), () -> "contingencia");
        }

        assertThatThrownBy(() -> inventario.ejecutarOFallar(() -> "no deberia llegar"))
                .isInstanceOf(DependenciaDegradada.class)
                .hasNoCause();
    }

    @Test
    void unaConfiguracionInvalidaEsUnError() {
        assertThatThrownBy(() -> new CortaCircuitos("d", "s", 0, Duration.ofSeconds(1), reloj, registro))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CortaCircuitos("d", "s", 3, Duration.ZERO, reloj, registro))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

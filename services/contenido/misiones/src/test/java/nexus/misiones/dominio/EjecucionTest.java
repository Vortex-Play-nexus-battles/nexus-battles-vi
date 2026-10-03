package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El ciclo de vida de una ejecucion (7.8.7): solo sale de En progreso, a
 * Completada o Fallida al vencer, o a Abandonada al cancelar; cualquier otra
 * transicion se rechaza y conserva el estado (HU-MIS-010).
 */
class EjecucionTest {

    static final Instant INICIO = Instant.parse("2026-09-25T10:00:00Z");
    static final HeroeEnMision HEROE = Misiones.HEROE;

    static Ejecucion enCurso() {
        return Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(12), 99L, null);
    }

    static ResultadoDeMision exito() {
        return new ResultadoDeMision(true, true, 19, true, 300, 20, 60, 2, List.of(), List.of(), List.of(),
                80, 120.5, List.of(1, 2));
    }

    static RecompensasDeEjecucion recompensas(int creditos, boolean conEpicaEntregable) {
        return new RecompensasDeEjecucion(creditos,
                List.of(new RecompensasDeEjecucion.ObjetoGanado("Espada de una mano", "p-espada", 1)),
                conEpicaEntregable
                        ? List.of(new RecompensasDeEjecucion.EpicaGanada("Golpe de defensa", "Master afin", "p-ep"))
                        : List.of(),
                120.5, List.of(), List.of(), true);
    }

    @Test
    @DisplayName("el progreso es el tiempo transcurrido sobre la duracion, de 0 a 1")
    void progreso() {
        Ejecucion ejecucion = enCurso();

        assertThat(ejecucion.progreso(INICIO)).isZero();
        assertThat(ejecucion.progreso(INICIO.plus(Duration.ofHours(3)))).isEqualTo(0.25);
        assertThat(ejecucion.progreso(INICIO.plus(Duration.ofHours(30)))).isEqualTo(1.0);
        assertThat(ejecucion.vencida(INICIO.plus(Duration.ofHours(11)))).isFalse();
        assertThat(ejecucion.vencida(INICIO.plus(Duration.ofHours(12)))).isTrue();
    }

    @Test
    @DisplayName("al terminar con exito queda Completada y con sus pasos de entrega pendientes")
    void terminarConExito() {
        Ejecucion ejecucion = enCurso();

        ejecucion.terminar(exito(), recompensas(60, true), true, INICIO.plus(Duration.ofHours(12)));

        assertThat(ejecucion.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(ejecucion.pasosPendientes()).containsExactly(
                PasoDeLiquidacion.LIBERACION, PasoDeLiquidacion.CREDITOS, PasoDeLiquidacion.BOTIN,
                PasoDeLiquidacion.EPICA, PasoDeLiquidacion.CORREO, PasoDeLiquidacion.CORREO_EPICA);
        assertThat(ejecucion.liquidacionPendiente()).isTrue();
    }

    @Test
    @DisplayName("sin creditos, sin objetos ni epica y sin correo, solo queda liberar al heroe")
    void soloLiberar() {
        Ejecucion ejecucion = enCurso();
        RecompensasDeEjecucion nada = new RecompensasDeEjecucion(0, List.of(), List.of(), 10, List.of(), List.of(),
                false);

        ejecucion.terminar(exito(), nada, false, INICIO.plus(Duration.ofHours(12)));

        assertThat(ejecucion.pasosPendientes()).containsExactly(PasoDeLiquidacion.LIBERACION);
    }

    @Test
    @DisplayName("cancelar lleva a Abandonada y solo deja pendiente liberar al heroe, sin recompensas")
    void cancelar() {
        Ejecucion ejecucion = enCurso();

        ejecucion.cancelar(INICIO.plus(Duration.ofHours(1)));

        assertThat(ejecucion.estado()).isEqualTo(EstadoEjecucion.ABANDONADA);
        assertThat(ejecucion.pasosPendientes()).containsExactly(PasoDeLiquidacion.LIBERACION);
        assertThat(ejecucion.recompensas()).isNull();
        assertThat(ejecucion.terminadaEn()).isEqualTo(INICIO.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("no se cancela ni se termina lo que ya termino, y el estado se conserva")
    void transicionesNoPermitidas() {
        Ejecucion ejecucion = enCurso();
        ejecucion.cancelar(INICIO.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> ejecucion.cancelar(INICIO.plus(Duration.ofHours(2))))
                .isInstanceOf(TransicionNoPermitida.class);
        assertThatThrownBy(() -> ejecucion.terminar(exito(), recompensas(1, false), false, INICIO))
                .isInstanceOf(TransicionNoPermitida.class);
        assertThat(ejecucion.estado()).isEqualTo(EstadoEjecucion.ABANDONADA);
    }

    @Test
    @DisplayName("un paso hecho sale de los pendientes; uno fallido tambien, con su motivo")
    void pasos() {
        Ejecucion ejecucion = enCurso();
        ejecucion.terminar(exito(), recompensas(60, false), false, INICIO.plus(Duration.ofHours(12)));

        ejecucion.pasoHecho(PasoDeLiquidacion.LIBERACION);
        ejecucion.pasoFallido(PasoDeLiquidacion.BOTIN, "el catalogo no tiene el producto");

        assertThat(ejecucion.pasosPendientes()).containsExactly(PasoDeLiquidacion.CREDITOS);
        assertThat(ejecucion.estadoDe(PasoDeLiquidacion.BOTIN)).isEqualTo(EstadoDePaso.FALLIDO);
        assertThat(ejecucion.motivoDe(PasoDeLiquidacion.BOTIN)).isEqualTo("el catalogo no tiene el producto");

        ejecucion.pasoHecho(PasoDeLiquidacion.CREDITOS);
        assertThat(ejecucion.liquidacionPendiente()).isFalse();
    }

    @Test
    @DisplayName("reintentar mas tarde espera cada vez el doble, con tope")
    void esperaExponencial() {
        Ejecucion ejecucion = enCurso();
        ejecucion.cancelar(INICIO);
        Duration base = Duration.ofSeconds(30);

        ejecucion.reintentarMasTarde(INICIO, base, "inventario no responde");
        assertThat(ejecucion.proximoIntento()).isEqualTo(INICIO.plusSeconds(30));
        ejecucion.reintentarMasTarde(INICIO, base, "inventario no responde");
        assertThat(ejecucion.proximoIntento()).isEqualTo(INICIO.plusSeconds(60));
        for (int i = 0; i < 20; i++) {
            ejecucion.reintentarMasTarde(INICIO, base, "inventario no responde");
        }
        assertThat(ejecucion.proximoIntento()).isEqualTo(INICIO.plus(Ejecucion.ESPERA_MAXIMA_ENTRE_INTENTOS));
        assertThat(ejecucion.ultimoError()).isEqualTo("inventario no responde");
    }

    @Test
    @DisplayName("la progresion que devuelve el inventario al liberar queda en la ejecucion")
    void progresion() {
        Ejecucion ejecucion = enCurso();
        ejecucion.terminar(exito(), recompensas(0, false), false, INICIO.plus(Duration.ofHours(12)));

        ejecucion.registrarProgresion(2, 20.5);

        assertThat(ejecucion.nivelAlcanzado()).isEqualTo(2);
        assertThat(ejecucion.experienciaAcumulada()).isEqualTo(20.5);
    }

    // ----------------------------------------------- reserva de la simulacion (HU-SIM-007)

    private static final Instant VENCE = INICIO.plus(Duration.ofHours(12));

    @Test
    @DisplayName("solo se puede reservar para simular una ejecucion en progreso, vencida y sin reserva vigente")
    void reclamable() {
        Ejecucion ejecucion = enCurso();

        assertThat(ejecucion.reclamable(VENCE.minusSeconds(1))).as("antes del plazo").isFalse();
        assertThat(ejecucion.reclamable(VENCE)).as("vencida y libre").isTrue();

        ejecucion.reservarParaSimular(VENCE);
        assertThat(ejecucion.reclamable(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION).minusSeconds(1)))
                .as("reservada por otra vuelta").isFalse();
        assertThat(ejecucion.reclamable(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION)))
                .as("el arriendo vencio").isTrue();

        ejecucion.cancelar(VENCE);
        assertThat(ejecucion.reclamable(VENCE.plus(Duration.ofDays(1)))).as("ya no esta en progreso").isFalse();
    }

    @Test
    @DisplayName("reservar cuenta el intento y fija hasta cuando es de quien la tomo")
    void reservar() {
        Ejecucion ejecucion = enCurso();
        assertThat(ejecucion.intentosDeSimulacion()).isZero();
        assertThat(ejecucion.simulacionReservadaHasta()).isNull();

        ejecucion.reservarParaSimular(VENCE);

        assertThat(ejecucion.intentosDeSimulacion()).isEqualTo(1);
        assertThat(ejecucion.simulacionReservadaHasta()).isEqualTo(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION));
        assertThat(Ejecucion.ARRIENDO_DE_SIMULACION).isEqualTo(Duration.ofMinutes(5));
        assertThat(ejecucion.reservaVigente(VENCE.plusSeconds(10))).isTrue();
    }

    @Test
    @DisplayName("tras un fallo de la simulacion la espera crece al doble con cada intento, con tope, y queda el error")
    void simulacionFallida() {
        Ejecucion ejecucion = enCurso();
        Duration base = Duration.ofSeconds(30);

        ejecucion.reservarParaSimular(VENCE);
        ejecucion.simulacionFallida(VENCE, base, "heroes no responde");
        assertThat(ejecucion.simulacionReservadaHasta()).isEqualTo(VENCE.plusSeconds(30));
        assertThat(ejecucion.ultimoErrorDeSimulacion()).isEqualTo("heroes no responde");

        ejecucion.reservarParaSimular(VENCE.plusSeconds(30));
        ejecucion.simulacionFallida(VENCE.plusSeconds(30), base, "heroes no responde");
        assertThat(ejecucion.simulacionReservadaHasta()).isEqualTo(VENCE.plusSeconds(30).plusSeconds(60));

        for (int i = 0; i < 25; i++) {
            ejecucion.reservarParaSimular(VENCE);
            ejecucion.simulacionFallida(VENCE, base, "heroes no responde");
        }
        assertThat(ejecucion.simulacionReservadaHasta()).isEqualTo(VENCE.plus(Ejecucion.ESPERA_MAXIMA_ENTRE_INTENTOS));
        assertThat(ejecucion.estado()).as("un fallo no cambia el estado de la mision")
                .isEqualTo(EstadoEjecucion.EN_PROGRESO);
    }

    @Test
    @DisplayName("al terminar o cancelar se suelta la reserva y se olvida el error, pero se conserva la cuenta de intentos")
    void terminarSueltaLaReserva() {
        Ejecucion terminada = enCurso();
        terminada.reservarParaSimular(VENCE);
        terminada.simulacionFallida(VENCE, Duration.ofSeconds(30), "motor no responde");
        terminada.reservarParaSimular(VENCE.plusSeconds(30));

        terminada.terminar(exito(), recompensas(0, false), false, VENCE.plusSeconds(31));

        assertThat(terminada.simulacionReservadaHasta()).isNull();
        assertThat(terminada.ultimoErrorDeSimulacion()).isNull();
        assertThat(terminada.intentosDeSimulacion()).isEqualTo(2);

        Ejecucion cancelada = enCurso();
        cancelada.reservarParaSimular(VENCE);
        cancelada.cancelar(VENCE.plusSeconds(5));
        assertThat(cancelada.simulacionReservadaHasta()).isNull();
    }
}

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
    @DisplayName("un paso hecho puede llevar una nota (la epica que ya tenia); sin nota no queda nada")
    void pasoHechoConNota() {
        Ejecucion ejecucion = enCurso();
        ejecucion.terminar(exito(), recompensas(60, false), false, INICIO.plus(Duration.ofHours(12)));

        ejecucion.pasoHecho(PasoDeLiquidacion.EPICA, "ya-la-tenia");
        ejecucion.pasoHecho(PasoDeLiquidacion.CREDITOS);

        assertThat(ejecucion.estadoDe(PasoDeLiquidacion.EPICA)).isEqualTo(EstadoDePaso.HECHO);
        assertThat(ejecucion.notaDe(PasoDeLiquidacion.EPICA)).isEqualTo("ya-la-tenia");
        assertThat(ejecucion.notaDe(PasoDeLiquidacion.CREDITOS)).isNull();
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
    @DisplayName("con avisos: la finalizacion, la epica y, la primera vez que se completa, las desbloqueadas, al final")
    void terminarConAvisos() {
        Ejecucion ejecucion = enCurso();

        ejecucion.terminar(exito(), recompensas(60, true), true, true, INICIO.plus(Duration.ofHours(12)));

        assertThat(ejecucion.pasosPendientes()).containsExactly(
                PasoDeLiquidacion.LIBERACION, PasoDeLiquidacion.CREDITOS, PasoDeLiquidacion.BOTIN,
                PasoDeLiquidacion.EPICA, PasoDeLiquidacion.CORREO, PasoDeLiquidacion.CORREO_EPICA,
                PasoDeLiquidacion.AVISO, PasoDeLiquidacion.AVISO_EPICA, PasoDeLiquidacion.AVISO_DESBLOQUEO);
    }

    @Test
    @DisplayName("con avisos: ni una fallida ni una repetida desbloquean nada; sin epica no hay aviso de epica")
    void avisosSegunElResultado() {
        RecompensasDeEjecucion repetida = new RecompensasDeEjecucion(5, List.of(), List.of(), 10, List.of(),
                List.of(), false);
        Ejecucion completadaOtraVez = enCurso();
        completadaOtraVez.terminar(exito(), repetida, false, true, INICIO.plus(Duration.ofHours(12)));
        assertThat(completadaOtraVez.pasosPendientes()).containsExactly(
                PasoDeLiquidacion.LIBERACION, PasoDeLiquidacion.CREDITOS, PasoDeLiquidacion.AVISO);

        ResultadoDeMision derrota = new ResultadoDeMision(false, false, 3, false, 40, 5, 20, 0, List.of(), List.of(),
                List.of(), 0, 30, List.of());
        RecompensasDeEjecucion soloExperiencia = new RecompensasDeEjecucion(0, List.of(), List.of(), 30, List.of(),
                List.of(), true);
        Ejecucion fallida = enCurso();
        fallida.terminar(derrota, soloExperiencia, false, true, INICIO.plus(Duration.ofHours(12)));
        assertThat(fallida.estado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(fallida.pasosPendientes()).containsExactly(PasoDeLiquidacion.LIBERACION, PasoDeLiquidacion.AVISO);
    }

    @Test
    @DisplayName("una simulacion aplazada espera cada vez el doble, con tope de cinco minutos, y no se ofrece antes")
    void simulacionAplazada() {
        Ejecucion ejecucion = enCurso();
        Instant vence = INICIO.plus(Duration.ofHours(12));
        assertThat(ejecucion.listaParaSimular(vence.minusSeconds(1))).isFalse();
        assertThat(ejecucion.listaParaSimular(vence)).isTrue();

        ejecucion.simulacionAplazada(vence, Duration.ofSeconds(30), "heroes no responde");
        assertThat(ejecucion.proximoIntento()).isEqualTo(vence.plusSeconds(30));
        assertThat(ejecucion.listaParaSimular(vence.plusSeconds(29))).isFalse();
        assertThat(ejecucion.listaParaSimular(vence.plusSeconds(30))).isTrue();
        assertThat(ejecucion.ultimoError()).isEqualTo("heroes no responde");
        for (int i = 0; i < 10; i++) {
            ejecucion.simulacionAplazada(vence, Duration.ofSeconds(30), "heroes no responde");
        }
        assertThat(ejecucion.proximoIntento()).isEqualTo(vence.plus(Ejecucion.ESPERA_MAXIMA_ANTES_DE_SIMULAR));
        assertThat(ejecucion.estado()).as("el heroe sigue en mision").isEqualTo(EstadoEjecucion.EN_PROGRESO);
    }

    @Test
    @DisplayName("al terminar, los intentos de simular no cuentan para los reintentos de la entrega")
    void laLiquidacionEmpiezaDeCero() {
        Ejecucion ejecucion = enCurso();
        Instant vence = INICIO.plus(Duration.ofHours(12));
        for (int i = 0; i < 5; i++) {
            ejecucion.simulacionAplazada(vence, Duration.ofSeconds(30), "motor caido");
        }

        Instant terminada = vence.plus(Duration.ofMinutes(20));
        ejecucion.terminar(exito(), recompensas(60, false), false, terminada);

        assertThat(ejecucion.intentosDeLiquidacion()).isZero();
        assertThat(ejecucion.ultimoError()).isNull();
        assertThat(ejecucion.proximoIntento()).isEqualTo(terminada);
        ejecucion.reintentarMasTarde(terminada, Duration.ofSeconds(30), "ms-finanzas no responde");
        assertThat(ejecucion.proximoIntento()).isEqualTo(terminada.plusSeconds(30));
    }

    @Test
    @DisplayName("no se aplaza la simulacion de lo que ya termino")
    void noSeAplazaLoTerminado() {
        Ejecucion ejecucion = enCurso();
        ejecucion.cancelar(INICIO.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> ejecucion.simulacionAplazada(INICIO, Duration.ofSeconds(30), "x"))
                .isInstanceOf(TransicionNoPermitida.class);
    }

    // ----------------------------------------------- reserva de la simulacion (HU-SIM-007), sobre el aplazamiento de #851

    private static final Instant VENCE = INICIO.plus(Duration.ofHours(12));

    @Test
    @DisplayName("la reserva cuenta el intento y es un arriendo de cinco minutos: hasta que vence, nadie mas la ve lista para simular")
    void reservar() {
        Ejecucion ejecucion = enCurso();
        assertThat(ejecucion.intentosDeSimulacion()).isZero();
        assertThat(ejecucion.listaParaSimular(VENCE.minusSeconds(1))).as("antes del plazo").isFalse();
        assertThat(ejecucion.listaParaSimular(VENCE)).as("vencida y libre").isTrue();

        ejecucion.reservarParaSimular(VENCE);

        assertThat(Ejecucion.ARRIENDO_DE_SIMULACION).isEqualTo(Duration.ofMinutes(5));
        assertThat(ejecucion.intentosDeSimulacion()).isEqualTo(1);
        assertThat(ejecucion.proximoIntento()).isEqualTo(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION));
        assertThat(ejecucion.reservaVigente(VENCE.plusSeconds(10))).isTrue();
        assertThat(ejecucion.listaParaSimular(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION).minusSeconds(1)))
                .as("reservada por otra vuelta").isFalse();
        assertThat(ejecucion.listaParaSimular(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION)))
                .as("el arriendo vencio").isTrue();
        assertThat(ejecucion.reservaVigente(VENCE.plus(Ejecucion.ARRIENDO_DE_SIMULACION))).isFalse();
    }

    @Test
    @DisplayName("solo se reserva lo que esta en progreso")
    void noSeReservaLoTerminado() {
        Ejecucion ejecucion = enCurso();
        ejecucion.cancelar(VENCE);

        assertThatThrownBy(() -> ejecucion.reservarParaSimular(VENCE.plusSeconds(1)))
                .isInstanceOf(TransicionNoPermitida.class);
        assertThat(ejecucion.listaParaSimular(VENCE.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    @DisplayName("si la simulacion falla, manda el aplazamiento (30 s, 1, 2 y 4 min, tope 5): la espera reemplaza al arriendo, y la cuenta de reservas sigue")
    void elAplazamientoReemplazaAlArriendo() {
        Ejecucion ejecucion = enCurso();
        Duration base = Duration.ofSeconds(30);

        ejecucion.reservarParaSimular(VENCE);
        ejecucion.simulacionAplazada(VENCE, base, "heroes no responde");
        assertThat(ejecucion.proximoIntento()).isEqualTo(VENCE.plusSeconds(30));
        assertThat(ejecucion.ultimoError()).isEqualTo("heroes no responde");

        ejecucion.reservarParaSimular(VENCE.plusSeconds(30));
        ejecucion.simulacionAplazada(VENCE.plusSeconds(30), base, "heroes no responde");
        assertThat(ejecucion.proximoIntento()).isEqualTo(VENCE.plusSeconds(30).plusSeconds(60));
        assertThat(ejecucion.intentosDeSimulacion()).isEqualTo(2);
        assertThat(ejecucion.estado()).as("un fallo no cambia el estado de la mision")
                .isEqualTo(EstadoEjecucion.EN_PROGRESO);
    }

    @Test
    @DisplayName("al terminar o cancelar se suelta la reserva y se olvida el error, pero se conserva la cuenta de reservas")
    void terminarSueltaLaReserva() {
        Ejecucion terminada = enCurso();
        terminada.reservarParaSimular(VENCE);
        terminada.simulacionAplazada(VENCE, Duration.ofSeconds(30), "motor no responde");
        terminada.reservarParaSimular(VENCE.plusSeconds(30));

        terminada.terminar(exito(), recompensas(0, false), false, VENCE.plusSeconds(31));

        assertThat(terminada.proximoIntento()).as("la liquidacion empieza ya, no al vencer el arriendo")
                .isEqualTo(VENCE.plusSeconds(31));
        assertThat(terminada.ultimoError()).isNull();
        assertThat(terminada.intentosDeSimulacion()).isEqualTo(2);

        Ejecucion cancelada = enCurso();
        cancelada.reservarParaSimular(VENCE);
        cancelada.cancelar(VENCE.plusSeconds(5));
        assertThat(cancelada.proximoIntento()).isEqualTo(VENCE.plusSeconds(5));
        assertThat(cancelada.reservaVigente(VENCE.plusSeconds(6))).isFalse();
    }

    @Test
    @DisplayName("la simulacion viene fallando desde el primer intento fallido registrado y hasta que la ejecucion deja de estar en progreso")
    void simulacionFallando() {
        Instant vence = INICIO.plus(Duration.ofHours(12));
        Ejecucion ejecucion = enCurso();
        assertThat(ejecucion.simulacionFallando()).isFalse();

        ejecucion.reservarParaSimular(vence);
        assertThat(ejecucion.simulacionFallando()).as("reservar no es fallar").isFalse();

        ejecucion.simulacionAplazada(vence, Duration.ofSeconds(30), "heroes no responde");
        assertThat(ejecucion.simulacionFallando()).isTrue();

        ejecucion.terminar(exito(), recompensas(0, false), false, vence.plusSeconds(31));
        assertThat(ejecucion.simulacionFallando()).as("terminada, ya no falla").isFalse();
    }

    @Test
    @DisplayName("cancelar con la simulacion fallando queda sin penalizacion; sin fallo, con ella")
    void cancelarSinPenalizacion() {
        Instant vence = INICIO.plus(Duration.ofHours(12));
        Ejecucion fallando = enCurso();
        fallando.reservarParaSimular(vence);
        fallando.simulacionAplazada(vence, Duration.ofSeconds(30), "heroes no responde");

        fallando.cancelar(vence.plusSeconds(5));

        assertThat(fallando.canceladaSinPenalizacion()).isTrue();
        assertThat(fallando.estado()).isEqualTo(EstadoEjecucion.ABANDONADA);
        assertThat(fallando.pasosPendientes()).containsExactly(PasoDeLiquidacion.LIBERACION);

        Ejecucion sana = enCurso();
        sana.reservarParaSimular(vence);
        sana.cancelar(vence.plusSeconds(5));
        assertThat(sana.canceladaSinPenalizacion()).isFalse();
        assertThat(enCurso().canceladaSinPenalizacion()).isFalse();
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
}

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
    static final HeroeEnMision HEROE =
            new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 1, 0, 8, 44, 11);

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
}

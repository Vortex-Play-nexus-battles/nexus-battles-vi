package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.simulacion.ContextoDelDuelo;
import nexus.misiones.dominio.simulacion.DecididaPor;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El modelo PROPONE y la regla ACOTA (decisiones.md, 2026-10-01): el modelo solo elige entre jugadas que la regla de
 * heroes ya admite en este turno (rotacion, paso del cursor, poder y recarga), y si no esta seguro, falla o no
 * hay contexto, decide la regla. Aqui se prueba con un doble de la regla que cuenta sus llamadas y un puntuador que
 * da el puntaje que la prueba diga a cada accion.
 */
class DecisorConModeloTest {

    private static final String A = "Embate sangriento";
    private static final String B = "Lanza de los dioses";
    private static final String C = "Golpe de tormenta";
    private static final String BASICO = DecisionDeTurno.ATAQUE_BASICO;

    private static final List<List<String>> TRES = List.of(List.of(C), List.of(B), List.of(A));
    private static final Map<String, Integer> COSTOS = Map.of(A, 4, B, 4, C, 6);

    private ReglaDeRotaciones regla;
    private PuntuadorPorAccion modelo;
    private DecisorConModelo decisor;

    @BeforeEach
    void armar() {
        regla = new ReglaDeRotaciones(COSTOS);
        modelo = new PuntuadorPorAccion();
        decisor = new DecisorConModelo(regla, modelo, 0.6);
    }

    private static EventoDeCombate.EstadoDeCombatiente estado(int vida, int vidaMaxima, int poder, int poderMaximo) {
        return new EventoDeCombate.EstadoDeCombatiente(vida, vidaMaxima, poder, poderMaximo, List.of(), List.of());
    }

    private static TurnoParaDecidir turno(int poder, List<List<String>> rotaciones, List<Integer> cursores,
                                          Map<String, Integer> usos, int ronda) {
        ContextoDelDuelo contexto = new ContextoDelDuelo(estado(40, 44, poder, 12), estado(30, 60, 8, 10),
                "Mago Fuego", 4);
        return new TurnoParaDecidir("Guerrero Armas", 8, rotaciones, ronda, poder, 40, usos, cursores, contexto);
    }

    private static TurnoParaDecidir turno(int poder) {
        return turno(poder, TRES, List.of(), Map.of(), 1);
    }

    private static List<String> acciones(DecisionDeTurno d) {
        return d.candidatas().stream().map(DecisionDeTurno.Candidata::accion).toList();
    }

    // ---------------------------------------------------------------- cuando decide la regla sola

    @Test
    @DisplayName("sin el contexto del duelo el modelo no decide a ciegas: la regla, con una sola llamada")
    void sinContexto() {
        TurnoParaDecidir sinContexto = new TurnoParaDecidir("Guerrero Armas", 8, TRES, 1, 12, 40, Map.of(), List.of());

        DecisionDeTurno d = decisor.decidir(sinContexto);

        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(d.versionDelModelo()).isNull();
        assertThat(regla.llamadas).hasSize(1);
        assertThat(modelo.llamadas).isZero();
    }

    @Test
    @DisplayName("sin rotaciones no hay nada que elegir: el ataque basico de la regla")
    void sinRotaciones() {
        DecisionDeTurno d = decisor.decidir(turno(12, List.of(), List.of(), Map.of(), 1));

        assertThat(d.esAtaqueBasico()).isTrue();
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(modelo.llamadas).isZero();
    }

    @Test
    @DisplayName("si ninguna rotacion es viable solo queda el ataque basico: no se consulta al modelo")
    void ningunaViable() {
        DecisionDeTurno d = decisor.decidir(turno(1));

        assertThat(d.esAtaqueBasico()).isTrue();
        assertThat(d.rotacion()).isNull();
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(d.candidatas()).isEmpty();
        assertThat(regla.llamadas).hasSize(1);
        assertThat(modelo.llamadas).isZero();
    }

    // ---------------------------------------------------------------- cuando decide el modelo

    @Test
    @DisplayName("el modelo puede preferir otra de las jugadas viables: sale esa, con su rotacion, costo y cursores")
    void modeloElige() {
        modelo.puntaje(A, 5f);

        DecisionDeTurno d = decisor.decidir(turno(12));

        assertThat(d.accion()).isEqualTo(A);
        assertThat(d.costoDePoder()).isEqualTo(4);
        assertThat(d.rotacion()).isEqualTo(3);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.MODELO);
        assertThat(d.versionDelModelo()).isEqualTo("prueba-1");
        // Solo avanza el cursor de la rotacion ejecutada (la 3); las otras quedan donde estaban.
        assertThat(d.cursoresSiguientes()).containsExactly(0, 0, 1);
    }

    @Test
    @DisplayName("las candidatas son lo que la regla admite en orden de prioridad, mas el ataque basico, con su puntaje")
    void candidatasConPuntaje() {
        modelo.puntaje(A, 5f);
        modelo.puntaje(B, 1f);

        DecisionDeTurno d = decisor.decidir(turno(12));

        assertThat(acciones(d)).containsExactly(C, B, A, BASICO);
        assertThat(d.candidatas()).extracting(DecisionDeTurno.Candidata::costoDePoder).containsExactly(6, 4, 4, 0);
        assertThat(d.candidatas()).extracting(DecisionDeTurno.Candidata::rotacion).containsExactly(1, 2, 3, null);
        assertThat(d.candidatas()).extracting(DecisionDeTurno.Candidata::puntaje).containsExactly(0.0, 1.0, 5.0, 0.0);
    }

    @Test
    @DisplayName("el modelo puede elegir el ataque basico aunque haya habilidades viables; los cursores no avanzan")
    void modeloEligeElBasico() {
        modelo.puntaje(BASICO, 6f);

        DecisionDeTurno d = decisor.decidir(turno(12, TRES, List.of(1, 0, 2), Map.of(), 1));

        assertThat(d.esAtaqueBasico()).isTrue();
        assertThat(d.costoDePoder()).isZero();
        assertThat(d.rotacion()).isNull();
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.MODELO);
        assertThat(d.cursoresSiguientes()).containsExactly(1, 0, 2);
    }

    @Test
    @DisplayName("si el modelo coincide con la regla y esta seguro, tambien lo decide el modelo (queda dicho)")
    void modeloCoincideConLaRegla() {
        modelo.puntaje(C, 5f);

        DecisionDeTurno d = decisor.decidir(turno(12));

        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.MODELO);
        assertThat(d.cursoresSiguientes()).containsExactly(1, 0, 0);
    }

    // ---------------------------------------------------------------- la regla acota

    @Test
    @DisplayName("el costo en poder acota: una habilidad que el poder no alcanza no es candidata, puntue lo que puntue")
    void respetaElPoder() {
        modelo.puntaje(C, 100f);   // cuesta 6 y el heroe tiene 5

        DecisionDeTurno d = decisor.decidir(turno(5));

        assertThat(acciones(d)).containsExactly(B, A, BASICO);
        assertThat(d.accion()).isNotEqualTo(C);
        assertThat(d.costoDePoder()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("la recarga acota: una habilidad usada el turno anterior no es candidata")
    void respetaLaRecarga() {
        modelo.puntaje(B, 100f);

        DecisionDeTurno d = decisor.decidir(turno(12, TRES, List.of(), Map.of(B, 4), 5));   // B se uso en el turno 4

        assertThat(acciones(d)).containsExactly(C, A, BASICO);
        assertThat(d.accion()).isNotEqualTo(B);
    }

    @Test
    @DisplayName("el orden dentro de la rotacion acota: el paso que le toca al cursor, no cualquiera de la rotacion")
    void respetaElCursor() {
        List<List<String>> rotaciones = List.of(List.of(A, B), List.of(C));
        modelo.puntaje(A, 100f);   // el cursor de la rotacion 1 va en el paso 1 (B): A no le toca ahora

        DecisionDeTurno d = decisor.decidir(turno(12, rotaciones, List.of(1, 0), Map.of(), 1));

        assertThat(acciones(d)).containsExactly(B, C, BASICO);
        assertThat(acciones(d)).doesNotContain(A);
    }

    @Test
    @DisplayName("la prioridad desempata: con los mismos puntajes sale lo que habria salido sin modelo")
    void empateLoGanaLaRegla() {
        DecisionDeTurno d = decisor.decidir(turno(12));   // todos puntuan 0

        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(d.versionDelModelo()).isEqualTo("prueba-1");
        assertThat(acciones(d)).containsExactly(C, B, A, BASICO);
    }

    @Test
    @DisplayName("con confianza baja (la mejor no destaca) decide la regla")
    void confianzaBaja() {
        modelo.puntaje(A, 0.5f);   // 4 candidatas: la probabilidad de A es ~0,35, menos que 0,6

        DecisionDeTurno d = decisor.decidir(turno(12));

        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
    }

    @Test
    @DisplayName("el umbral de confianza es configurable")
    void umbralConfigurable() {
        modelo.puntaje(A, 0.5f);

        DecisionDeTurno d = new DecisorConModelo(regla, modelo, 0.3).decidir(turno(12));

        assertThat(d.accion()).isEqualTo(A);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.MODELO);
    }

    @Test
    @DisplayName("la misma habilidad en dos rotaciones es una sola candidata: la de mayor prioridad")
    void sinDuplicados() {
        modelo.puntaje(BASICO, 0f);

        DecisionDeTurno d = decisor.decidir(turno(12, List.of(List.of(A), List.of(B), List.of(A)), List.of(), Map.of(), 1));

        assertThat(acciones(d)).containsExactly(A, B, BASICO);
    }

    // ---------------------------------------------------------------- el volumen de llamadas a heroes

    @Test
    @DisplayName("tres rotaciones viables cuestan tres llamadas a la regla; ocurre solo con el modelo encendido")
    void llamadasConTresViables() {
        decisor.decidir(turno(12));

        assertThat(regla.llamadas).hasSize(3);
        // La 2.a mira solo las rotaciones que siguen a la elegida, con sus cursores alineados.
        assertThat(regla.llamadas.get(1).rotaciones()).containsExactly(List.of(B), List.of(A));
        assertThat(regla.llamadas.get(2).rotaciones()).containsExactly(List.of(A));
    }

    @Test
    @DisplayName("si la regla ya eligio la ultima rotacion no hay mas que mirar: una llamada")
    void unaLlamadaSiEligeLaUltima() {
        decisor.decidir(turno(4, TRES, List.of(), Map.of(C, 1, B, 1), 2));   // solo A es viable (C y B en recarga)

        assertThat(regla.llamadas).hasSize(1);
    }

    @Test
    @DisplayName("si tras la elegida ninguna otra es viable, basta una llamada mas")
    void seDetieneCuandoNoHayMasViables() {
        // Poder 4: C (6) no alcanza; B (4) es la elegida; despues A tampoco es viable porque esta en recarga.
        decisor.decidir(turno(4, TRES, List.of(), Map.of(A, 1), 2));

        assertThat(regla.llamadas).hasSize(2);
    }

    @Test
    @DisplayName("las consultas extra pasan los cursores de las rotaciones que quedan, no los de las anteriores")
    void cursoresAlineadosEnLasConsultasExtra() {
        decisor.decidir(turno(12, TRES, List.of(2, 1, 0), Map.of(), 1));

        assertThat(regla.llamadas.get(1).cursores()).containsExactly(1, 0);
        assertThat(regla.llamadas.get(2).cursores()).containsExactly(0);
    }

    // ---------------------------------------------------------------- nunca peor que la regla sola

    @Test
    @DisplayName("si el modelo lanza una excepcion decide la regla, sin propagar nada")
    void modeloQueFalla() {
        modelo.fallo = new IllegalStateException("ONNX Runtime fallo");

        DecisionDeTurno d = decisor.decidir(turno(12));

        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(d.versionDelModelo()).isNull();
        assertThat(d.candidatas()).isEmpty();
    }

    @Test
    @DisplayName("si el modelo devuelve algo que no es un numero o no es una por candidata, decide la regla")
    void salidasInvalidas() {
        modelo.puntajes.put(A, Float.NaN);
        assertThat(decisor.decidir(turno(12)).decididaPor()).isEqualTo(DecididaPor.REGLA);

        modelo.puntajes.clear();
        modelo.puntajes.put(A, Float.POSITIVE_INFINITY);
        assertThat(decisor.decidir(turno(12)).accion()).isEqualTo(C);

        modelo.puntajes.clear();
        modelo.devolverUnoDeMenos = true;
        DecisionDeTurno d = decisor.decidir(turno(12));
        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
    }

    @Test
    @DisplayName("si heroes no contesta las consultas extra, vale lo que ya contesto la primera")
    void fallaLaConsultaExtra() {
        regla.fallarEnLaLlamada = 2;
        regla.fallo = new IllegalStateException("heroes no responde");

        DecisionDeTurno d = decisor.decidir(turno(12));

        assertThat(d.accion()).isEqualTo(C);
        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(modelo.llamadas).isZero();
    }

    @Test
    @DisplayName("si heroes no contesta la primera, el fallo sube como siempre: no es cosa del modelo taparlo")
    void fallaLaPrimeraConsulta() {
        regla.fallarEnLaLlamada = 1;
        regla.fallo = new IllegalStateException("heroes no responde");

        assertThatThrownBy(() -> decisor.decidir(turno(12))).isSameAs(regla.fallo);
    }

    @Test
    @DisplayName("un decisor de la regla que no dice de que rotacion salio: solo se compara con el basico")
    void reglaSinRotacion() {
        DecisionDeTurno sinRotacion = new DecisionDeTurno(A, 4, List.of(1, 0, 0));
        DecisorConModelo d = new DecisorConModelo(t -> sinRotacion, modelo, 0.6);
        modelo.puntaje(BASICO, 9f);

        DecisionDeTurno decision = d.decidir(turno(12));

        assertThat(decision.esAtaqueBasico()).isTrue();
        assertThat(decision.decididaPor()).isEqualTo(DecididaPor.MODELO);
    }

    @Test
    @DisplayName("heroes devuelve el nombre exacto de la Tabla 7 aunque la rotacion lo traiga de otra forma: sigue siendo legal")
    void nombresSinTildesNiMayusculas() {
        DecisionDeTurno canonica = new DecisionDeTurno(A, 4, List.of(1), 1, DecididaPor.REGLA, null, List.of());
        DecisorConModelo d = new DecisorConModelo(t -> canonica, modelo, 0.6);
        modelo.puntaje(A, 9f);

        DecisionDeTurno decision = d.decidir(turno(12, List.of(List.of("EMBATE SANGRIENTO")), List.of(), Map.of(), 1));

        assertThat(decision.accion()).isEqualTo(A);
        assertThat(decision.decididaPor()).isEqualTo(DecididaPor.MODELO);
    }

    @Test
    @DisplayName("la confianza minima tiene que ser una probabilidad")
    void confianzaInvalida() {
        assertThatThrownBy(() -> new DecisorConModelo(regla, modelo, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecisorConModelo(regla, modelo, 1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- doble del modelo

    /**
     * Un modelo que puntua cada candidata segun su accion (el one-hot del vector de caracteristicas), con 0 para
     * las que la prueba no mencione.
     */
    static final class PuntuadorPorAccion implements Puntuador {
        final Map<String, Float> puntajes = new HashMap<>();
        int llamadas;
        RuntimeException fallo;
        boolean devolverUnoDeMenos;

        void puntaje(String accion, float valor) {
            puntajes.put(accion, valor);
        }

        @Override
        public float[] puntuar(float[][] caracteristicas) {
            llamadas++;
            if (fallo != null) {
                throw fallo;
            }
            float[] salida = new float[devolverUnoDeMenos ? caracteristicas.length - 1 : caracteristicas.length];
            for (int i = 0; i < salida.length; i++) {
                for (int a = 0; a < Caracteristicas.ACCIONES.size(); a++) {
                    if (caracteristicas[i][27 + a] == 1f) {
                        salida[i] = puntajes.getOrDefault(Caracteristicas.ACCIONES.get(a), 0f);
                    }
                }
            }
            return salida;
        }

        @Override
        public String version() {
            return "prueba-1";
        }
    }
}

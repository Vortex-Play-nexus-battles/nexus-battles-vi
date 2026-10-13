package nexus.combate.reglas;

import nexus.combate.reglas.SimuladorDeCombates.Estrategia;
import nexus.combate.reglas.SimuladorDeCombates.Metricas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La maquina en partidas completas contra el motor real (D-41, auditoria del
 * 4-oct). Es la version ligera, para la integracion continua: pocas partidas
 * con semillas fijas, asi que el resultado es siempre el mismo. El informe con
 * cientos de partidas es {@link InformeDeSimulacionTest}.
 *
 * <p>Lo innegociable, en cualquier cruce: cero jugadas ilegales, cero dano a
 * uno mismo (el reflejo de Pinchos o «Toma y lleva» es contractual y se cuenta
 * aparte) y cero fuego amigo.
 */
class SimulacionDeLaMaquinaTest {

    private static final List<String> ATACANTES = SimuladorDeCombates.PROTOTIPOS.subList(0, 6);

    private final SimuladorDeCombates sim = new SimuladorDeCombates(120);

    @Test
    @DisplayName("8×8 prototipos en los niveles 1 y 8: ni una jugada ilegal, ni auto-daño, ni fuego amigo")
    void matriz8x8() {
        Metricas m = new Metricas();
        for (int nivel : List.of(1, 8)) {
            for (String a : SimuladorDeCombates.PROTOTIPOS) {
                for (String b : SimuladorDeCombates.PROTOTIPOS) {
                    m.sumar(sim.duelo(a, Estrategia.NORMAL, b, Estrategia.NORMAL, nivel,
                            31L * nivel + a.hashCode() * 17L + b.hashCode()));
                }
            }
        }
        assertAll(
                () -> assertEquals(128, m.partidas),
                () -> assertEquals(0, m.ilegales, "jugadas ilegales"),
                () -> assertEquals(0, m.autoDano, "auto-daño"),
                () -> assertEquals(0, m.fuegoAmigo, "fuego amigo"));
    }

    @Test
    @DisplayName("3 contra 3 con sanadores, en las tres dificultades: nunca golpea a un compañero ni a sí misma")
    void cooperativo() {
        Metricas m = new Metricas();
        Estrategia[][] cruces = {
                {Estrategia.FACIL, Estrategia.DIFICIL}, {Estrategia.NORMAL, Estrategia.NORMAL},
                {Estrategia.DIFICIL, Estrategia.FACIL}};
        for (Estrategia[] cruce : cruces) {
            for (int nivel : List.of(1, 4, 8)) {
                m.sumar(sim.partida(
                        List.of(SimuladorDeCombates.heroe("A1", "Guerrero Tanque", nivel, 1),
                                SimuladorDeCombates.heroe("A2", "Mago Hielo", nivel, 1),
                                SimuladorDeCombates.heroe("A3", "Médico", nivel, 1)), cruce[0],
                        List.of(SimuladorDeCombates.heroe("B1", "Pícaro Veneno", nivel, 2),
                                SimuladorDeCombates.heroe("B2", "Guerrero Armas", nivel, 2),
                                SimuladorDeCombates.heroe("B3", "Chamán", nivel, 2)), cruce[1],
                        true, 97L * nivel + cruce[0].ordinal()));
            }
        }
        assertAll(
                () -> assertEquals(0, m.ilegales, "jugadas ilegales"),
                () -> assertEquals(0, m.autoDano, "auto-daño"),
                () -> assertEquals(0, m.fuegoAmigo, "fuego amigo"),
                () -> assertTrue(m.sanaciones > 0, "los sanadores sanan"));
    }

    @Test
    @DisplayName("juega mejor que la regla fija anterior (D-B7-12), y NORMAL mejor que FÁCIL")
    void mejorQueLaReglaFija() {
        Metricas contraLaFija = espejo(Estrategia.NORMAL, Estrategia.REGLA_FIJA, 8);
        Metricas normalContraFacil = espejo(Estrategia.NORMAL, Estrategia.FACIL, 8);
        Metricas dificilContraFacil = espejo(Estrategia.DIFICIL, Estrategia.FACIL, 8);
        assertAll(
                () -> assertTrue(contraLaFija.victoriasA > contraLaFija.victoriasB,
                        "NORMAL " + contraLaFija.victoriasA + " - regla fija " + contraLaFija.victoriasB),
                () -> assertTrue(normalContraFacil.victoriasA > normalContraFacil.victoriasB,
                        "NORMAL " + normalContraFacil.victoriasA + " - FÁCIL " + normalContraFacil.victoriasB),
                () -> assertTrue(dificilContraFacil.victoriasA > dificilContraFacil.victoriasB,
                        "DIFÍCIL " + dificilContraFacil.victoriasA + " - FÁCIL " + dificilContraFacil.victoriasB),
                () -> assertEquals(0, contraLaFija.ilegales + normalContraFacil.ilegales
                        + dificilContraFacil.ilegales, "jugadas ilegales"));
    }

    @Test
    @DisplayName("usa sus recursos: especiales, épicas, defensas y sanaciones")
    void usaSusRecursos() {
        Metricas m = espejo(Estrategia.NORMAL, Estrategia.NORMAL, 4);
        assertAll(
                () -> assertTrue(m.especiales > 0, "acciones especiales"),
                () -> assertTrue(m.epicas > 0, "épicas"),
                () -> assertTrue(m.defensas > 0, "defensas (Mano de piedra, Defensa feroz)"),
                () -> assertTrue(m.sanaciones > 0, "sanaciones (Segundo impulso)"),
                () -> assertTrue(m.especiales + m.epicas < m.acciones, "y también el ataque básico"));
    }

    /** Mismo prototipo y nivel para los dos bandos, en los seis prototipos que atacan y los niveles 1, 4 y 8. */
    private Metricas espejo(Estrategia a, Estrategia b, int partidasPorCasilla) {
        Metricas m = new Metricas();
        for (int nivel : List.of(1, 4, 8)) {
            for (String p : ATACANTES) {
                for (int i = 0; i < partidasPorCasilla; i++) {
                    m.sumar(sim.duelo(p, a, p, b, nivel, 1009L * i + 37L * nivel + p.hashCode()));
                }
            }
        }
        return m;
    }
}

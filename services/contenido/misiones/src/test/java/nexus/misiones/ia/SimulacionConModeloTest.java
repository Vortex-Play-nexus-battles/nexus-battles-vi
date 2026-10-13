package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.configuracion.ConfiguracionDeIa;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.DecididaPor;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.PerfilDeCombate;
import nexus.misiones.dominio.simulacion.Rival;
import nexus.misiones.dominio.simulacion.Simulacion;
import nexus.misiones.dominio.simulacion.SimuladorDeMision;
import nexus.misiones.dominio.simulacion.TipoDeRival;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * La IA con modelo, de punta a punta con el simulador, el motor de combate (doble) y la red de verdad (ONNX
 * Runtime con el modelo SINTETICO de prueba): los criterios de aceptacion de HU-SIM-008 vistos desde los eventos
 * que quedan registrados.
 *
 * <ul>
 *   <li>Criterio 1: el comportamiento sale de un modelo de aprendizaje profundo (hay jugadas {@code MODELO}).</li>
 *   <li>Criterio 2: respeta la rotacion y el costo en poder: nunca una accion fuera de las rotaciones, nunca sin
 *       poder, nunca la que esta en recarga.</li>
 *   <li>Apagado o sin modelo, la simulacion es identica a la de hoy, evento por evento.</li>
 * </ul>
 */
class SimulacionConModeloTest {

    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    private static final String MISION = "templo-olvidado";
    private static final Formula ATAQUE = new Formula(10, 1, 6);
    private static final Formula DANO = new Formula(2, 1, 4);

    private static final HeroeEnMision HEROE = new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 8, 0, 12, 60, 11);
    private static final PerfilDeCombate PERFIL = new PerfilDeCombate(
            new EstadisticasDeCombate(12, 60, 11, ATAQUE, DANO, null), List.of(), List.of());

    private static final List<List<String>> ESTRATEGIA_DEL_HEROE = List.of(
            List.of("Golpe de tormenta"), List.of("Lanza de los dioses"), List.of("Embate sangriento"));
    private static final List<List<String>> ESTRATEGIA_DEL_ENEMIGO = List.of(
            List.of("Golpe con escudo"), List.of("Mano de piedra"));
    private static final Map<String, Integer> COSTOS = Map.of("Golpe de tormenta", 6, "Lanza de los dioses", 4,
            "Embate sangriento", 4, "Golpe con escudo", 2, "Mano de piedra", 4);

    @TempDir
    Path carpeta;

    private Path modeloDePrueba() throws IOException {
        for (String nombre : List.of("modelo.onnx", "modelo.json")) {
            try (InputStream in = getClass().getResourceAsStream("/ia/" + nombre)) {
                Files.write(carpeta.resolve(nombre), in.readAllBytes());
            }
        }
        return carpeta.resolve("modelo.onnx");
    }

    private static List<Rival> rivales() {
        return List.of(
                new Rival("Guardian", TipoDeRival.REGULAR, "Guerrero Tanque", 8, 70, 11, 10, ESTRATEGIA_DEL_ENEMIGO, null),
                new Rival("Sombra", TipoDeRival.REGULAR, "Guerrero Tanque", 8, 70, 11, 10, ESTRATEGIA_DEL_ENEMIGO, null),
                new Rival("Eterno", TipoDeRival.JEFE, "Guerrero Tanque", 8, 90, 11, 10, ESTRATEGIA_DEL_ENEMIGO, null));
    }

    private static Simulacion simular(DecisorDeTurno decisor) {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 9;
        motor.danoDeLosEnemigos = 3;
        motor.costos.putAll(COSTOS);
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, motor, dado -> 1);
        return simulador.simular(EJECUCION, MISION, HEROE, PERFIL, ESTRATEGIA_DEL_HEROE, rivales(),
                new AzarConSemilla(42L), 42L);
    }

    private static Set<String> accionesPermitidas(EventoDeCombate e) {
        Set<String> permitidas = new HashSet<>(List.of(DecisionDeTurno.ATAQUE_BASICO));
        (e.actor().lado() == EventoDeCombate.Lado.HEROE ? ESTRATEGIA_DEL_HEROE : ESTRATEGIA_DEL_ENEMIGO)
                .forEach(permitidas::addAll);
        return permitidas;
    }

    // ---------------------------------------------------------------- apagado = como hoy

    @Test
    @DisplayName("con la bandera apagada, sin ruta, sin archivo o con un modelo danado la simulacion es identica a la de hoy")
    void apagadoEsIdentico() throws IOException {
        ReglaDeRotaciones regla = new ReglaDeRotaciones(COSTOS);
        Simulacion deHoy = simular(regla);
        Path danado = carpeta.resolve("danado.onnx");
        Files.writeString(danado, "no es un modelo");

        List<DecisorDeTurno> variantes = List.of(
                ConfiguracionDeIa.elegirDecisor(regla, false, modeloDePrueba().toString(), 0.6),
                ConfiguracionDeIa.elegirDecisor(regla, true, "", 0.6),
                ConfiguracionDeIa.elegirDecisor(regla, true, carpeta.resolve("no-esta.onnx").toString(), 0.6),
                ConfiguracionDeIa.elegirDecisor(regla, true, danado.toString(), 0.6));

        assertThat(deHoy.eventos()).isNotEmpty();
        for (DecisorDeTurno variante : variantes) {
            Simulacion otra = simular(variante);
            assertThat(otra.eventos()).isEqualTo(deHoy.eventos());
            assertThat(otra.resultado()).isEqualTo(deHoy.resultado());
        }
        assertThat(deHoy.eventos()).allSatisfy(e -> {
            if (e.jugada() != null) {
                assertThat(e.jugada().decididaPor()).isEqualTo(DecididaPor.REGLA);
                assertThat(e.jugada().versionDelModelo()).isNull();
                assertThat(e.jugada().candidatas()).isEmpty();
            }
        });
    }

    // ---------------------------------------------------------------- encendido

    @Test
    @DisplayName("con el modelo encendido hay jugadas que decide el modelo, con su version y sus candidatas")
    void decideElModelo() throws IOException {
        try (DecisorConModelo decisor = new DecisorConModelo(new ReglaDeRotaciones(COSTOS),
                PuntuadorOnnx.cargar(modeloDePrueba()), 0.4)) {
            Simulacion simulacion = simular(decisor);

            List<EventoDeCombate.Jugada> jugadas = simulacion.eventos().stream().map(EventoDeCombate::jugada)
                    .filter(j -> j != null).toList();
            List<EventoDeCombate.Jugada> delModelo = jugadas.stream()
                    .filter(j -> j.decididaPor() == DecididaPor.MODELO).toList();
            assertThat(delModelo).as("jugadas que decidio el modelo de %d", jugadas.size()).isNotEmpty();
            assertThat(delModelo).allSatisfy(j -> {
                assertThat(j.versionDelModelo()).isEqualTo("sintetico-v1");
                assertThat(j.candidatas()).hasSizeGreaterThanOrEqualTo(2);
                assertThat(j.candidatas()).allSatisfy(c -> assertThat(c.puntaje()).isNotNull());
                assertThat(j.candidatas()).extracting(DecisionDeTurno.Candidata::accion).contains(j.ejecutada());
            });
        }
    }

    @Test
    @DisplayName("criterio 2: nunca sale una accion fuera de las rotaciones, ni sin poder, ni en recarga, ni rechazada")
    void respetaRotacionPoderYRecarga() throws IOException {
        try (DecisorConModelo decisor = new DecisorConModelo(new ReglaDeRotaciones(COSTOS),
                PuntuadorOnnx.cargar(modeloDePrueba()), 0.4)) {
            Simulacion simulacion = simular(decisor);

            Map<String, String> ultimaEspecialDeCadaUno = new HashMap<>();
            for (EventoDeCombate e : simulacion.eventos()) {
                if (e.jugada() == null) {
                    continue;
                }
                EventoDeCombate.Jugada j = e.jugada();
                assertThat(accionesPermitidas(e)).as("secuencia %d", e.secuencia()).contains(j.ejecutada());
                assertThat(j.enValorBase()).as("secuencia %d: jugada sin poder suficiente", e.secuencia()).isFalse();
                assertThat(j.costoDePoder()).isLessThanOrEqualTo(e.antes().actor().poder());
                assertThat(j.rechazadas()).as("el motor no rechazo nada").isEmpty();

                // Recarga: la misma habilidad especial no sale en dos turnos seguidos del mismo combatiente.
                String quien = e.encuentro() + "/" + e.actor().lado();
                if (!DecisionDeTurno.ATAQUE_BASICO.equals(j.ejecutada())) {
                    assertThat(ultimaEspecialDeCadaUno.put(quien, j.ejecutada()))
                            .as("secuencia %d repite %s sin recargar", e.secuencia(), j.ejecutada())
                            .isNotEqualTo(j.ejecutada());
                } else {
                    ultimaEspecialDeCadaUno.remove(quien);
                }
            }
        }
    }

    @Test
    @DisplayName("es reproducible: la misma semilla y el mismo modelo dan los mismos eventos")
    void reproducible() throws IOException {
        Path onnx = modeloDePrueba();
        List<List<EventoDeCombate>> corridas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            try (DecisorConModelo decisor = new DecisorConModelo(new ReglaDeRotaciones(COSTOS),
                    PuntuadorOnnx.cargar(onnx), 0.4)) {
                corridas.add(simular(decisor).eventos());
            }
        }

        assertThat(corridas.get(0)).isEqualTo(corridas.get(1));
    }
}

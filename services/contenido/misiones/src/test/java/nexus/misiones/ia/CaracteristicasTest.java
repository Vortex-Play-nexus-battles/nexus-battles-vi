package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.simulacion.ContextoDelDuelo;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * La definicion de caracteristicas (version 1) debe ser LA MISMA que usa el entrenamiento en Python
 * ({@code ia/nexus_ia/caracteristicas.py}). Los casos dorados los genera Python
 * ({@code python -m nexus_ia.dorados}) y aqui se comprueba que Java calcula, numero por numero, lo mismo.
 */
class CaracteristicasTest {

    private static final Path DORADOS = Path.of("ia", "pruebas", "features-dorados.json");
    private static final float TOLERANCIA = 1e-6f;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dorados() throws IOException {
        return JsonMapper.builder().build().readValue(Files.readString(DORADOS, StandardCharsets.UTF_8), Map.class);
    }

    // ---------------------------------------------------------------- Python y Java coinciden

    @Test
    @DisplayName("la version, la dimension y los vocabularios son los de Python")
    void mismaDefinicion() throws IOException {
        Map<String, Object> d = dorados();

        assertThat(Caracteristicas.VERSION).isEqualTo(((Number) d.get("version")).intValue());
        assertThat(Caracteristicas.DIMENSION).isEqualTo(((Number) d.get("dimension")).intValue());
        assertThat(Caracteristicas.PROTOTIPOS).containsExactlyElementsOf((List<String>) d.get("prototipos"));
        assertThat(Caracteristicas.ACCIONES).containsExactlyElementsOf((List<String>) d.get("acciones"));
    }

    @Test
    @DisplayName("cada caso dorado de Python da el mismo vector en Java, incluidos los bordes")
    @SuppressWarnings("unchecked")
    void casosDorados() throws IOException {
        List<Map<String, Object>> casos = (List<Map<String, Object>>) dorados().get("casos");
        assertThat(casos).hasSizeGreaterThanOrEqualTo(38);

        for (int i = 0; i < casos.size(); i++) {
            Map<String, Object> caso = casos.get(i);
            Caracteristicas.Situacion s = situacion((Map<String, Object>) caso.get("situacion"));
            float[] obtenido = Caracteristicas.de(s, (String) caso.get("accion"), ((Number) caso.get("costo")).intValue());
            List<Number> esperado = (List<Number>) caso.get("vector");

            assertThat(obtenido).as("caso %d (%s)", i, caso.get("accion")).hasSize(esperado.size());
            for (int j = 0; j < esperado.size(); j++) {
                assertThat(obtenido[j]).as("caso %d (%s), posicion %d", i, caso.get("accion"), j)
                        .isCloseTo(esperado.get(j).floatValue(), org.assertj.core.data.Offset.offset(TOLERANCIA));
            }
        }
    }

    private static Caracteristicas.Situacion situacion(Map<String, Object> m) {
        return new Caracteristicas.Situacion((String) m.get("prototipo"), entero(m, "nivel"), entero(m, "turno"),
                entero(m, "vida"), entero(m, "vida_maxima"), entero(m, "poder"), entero(m, "poder_maximo"),
                entero(m, "efectos"), (String) m.get("oponente_prototipo"), entero(m, "oponente_nivel"),
                entero(m, "oponente_vida"), entero(m, "oponente_vida_maxima"), entero(m, "oponente_poder"),
                entero(m, "oponente_poder_maximo"), entero(m, "oponente_efectos"));
    }

    private static int entero(Map<String, Object> m, String clave) {
        return ((Number) m.get(clave)).intValue();
    }

    // ---------------------------------------------------------------- de un turno a una situacion

    private static EventoDeCombate.EstadoDeCombatiente estado(int vida, int vidaMaxima, int poder, int poderMaximo,
                                                               int efectos) {
        List<EventoDeCombate.EfectoVigente> lista = new java.util.ArrayList<>();
        for (int i = 0; i < efectos; i++) {
            lista.add(new EventoDeCombate.EfectoVigente("Sangrado", "DANO_POR_TURNO", 2, 1));
        }
        return new EventoDeCombate.EstadoDeCombatiente(vida, vidaMaxima, poder, poderMaximo, List.of(), lista);
    }

    @Test
    @DisplayName("la situacion sale del turno y de su contexto: lo mismo que el entrenamiento lee del evento")
    void situacionDelTurno() {
        ContextoDelDuelo contexto = new ContextoDelDuelo(estado(22, 44, 6, 12, 1), estado(30, 60, 4, 8, 2),
                "Mago Fuego", 8);
        TurnoParaDecidir turno = new TurnoParaDecidir("Guerrero Armas", 4, List.of(), 3, 6, 22, Map.of(), List.of(),
                contexto);

        Caracteristicas.Situacion s = Caracteristicas.Situacion.de(turno).orElseThrow();

        assertThat(s).isEqualTo(new Caracteristicas.Situacion("Guerrero Armas", 4, 3, 22, 44, 6, 12, 1, "Mago Fuego",
                8, 30, 60, 4, 8, 2));
    }

    @Test
    @DisplayName("sin el contexto del duelo no hay situacion: el modelo no puede decidir a ciegas")
    void sinContexto() {
        TurnoParaDecidir turno = new TurnoParaDecidir("Guerrero Armas", 4, List.of(), 3, 6, 22, Map.of(), List.of());

        assertThat(Caracteristicas.Situacion.de(turno)).isEmpty();
    }

    // ---------------------------------------------------------------- lo basico

    @Test
    @DisplayName("el vector tiene la dimension declarada y el ataque basico es la accion cero")
    void forma() {
        Caracteristicas.Situacion s = new Caracteristicas.Situacion("Médico", 8, 1, 44, 44, 12, 12, 0, "Mago Hielo", 8,
                40, 40, 10, 10, 0);

        float[] v = Caracteristicas.de(s, "Ataque básico", 0);

        assertThat(v).hasSize(Caracteristicas.DIMENSION).hasSize(54);
        assertThat(v[27]).isEqualTo(1f);
        assertThat(Caracteristicas.ACCIONES.getFirst()).isEqualTo("Ataque básico");
        assertThat(Caracteristicas.ACCIONES).hasSize(25);
        assertThat(Caracteristicas.PROTOTIPOS).hasSize(8);
    }

    @Test
    @DisplayName("una accion o un prototipo desconocidos dejan su bloque en ceros, sin lanzar")
    void desconocidos() {
        Caracteristicas.Situacion s = new Caracteristicas.Situacion("Dragon", 1, 1, 10, 10, 2, 2, 0, null, 1, 10, 10, 2,
                2, 0);

        float[] v = Caracteristicas.de(s, "Rugido", 3);

        float suma = 0;
        for (int i = 11; i < 52; i++) {
            suma += v[i];
        }
        assertThat(suma).isZero();
    }

    @Test
    @DisplayName("un costo negativo no se admite")
    void costoNegativo() {
        Caracteristicas.Situacion s = new Caracteristicas.Situacion("Médico", 8, 1, 44, 44, 12, 12, 0, "Mago Hielo", 8,
                40, 40, 10, 10, 0);

        assertThatThrownBy(() -> Caracteristicas.de(s, "Ataque básico", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

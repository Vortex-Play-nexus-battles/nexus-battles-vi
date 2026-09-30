package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadisticasDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El perfil y el estado de combate como JSON en su columna (V14, B7).
 *
 * <p>Tres garantias: lo que se escribe se lee igual, dos escrituras del mismo
 * estado dan el mismo texto (la ficha de la sala se compara por valor) y un
 * texto ilegible se lee como ausente en vez de dejar la partida ilegible.
 */
@DisplayName("JsonDeCombate · perfil y estado de combate en su columna (V14)")
class JsonDeCombateTest {

    private static final EstadisticasDeCombate ESTADISTICAS = new EstadisticasDeCombate(10, 44, 11,
            new EstadisticasDeCombate.Formula(10, 1, 6), new EstadisticasDeCombate.Formula(0, 1, 4), null);

    @Test
    @DisplayName("el perfil y el estado vuelven tal como se escribieron")
    void idaYVuelta() {
        PerfilDeCombate perfil = new PerfilDeCombate(2, ESTADISTICAS, List.of("Escudo de dragón"),
                List.of("Golpe de defensa"));
        EstadoDeCombate estado = new EstadoDeCombate(4, 10, 2, Map.of("Golpe con escudo", 1, "Mano de piedra", 0),
                List.of(new EstadoDeCombate.Efecto("CORTADA", "Cortada", "BONO_DANO", 2, 1, false, "x")),
                new EstadoDeCombate.GolpeRecibido("y", 5), Map.of("Golpe con escudo", 1),
                List.of(new EstadoDeCombate.AccionDisponible("Reanimación", "Reanimación", "REANIMACION", false, null,
                        true, 1, 8, false, "Se aprende en el nivel 8.")),
                ESTADISTICAS);

        assertAll(
                () -> assertEquals(perfil, JsonDeCombate.perfil(JsonDeCombate.escribir(perfil))),
                () -> assertEquals(estado, JsonDeCombate.estado(JsonDeCombate.escribir(estado))));
    }

    @Test
    @DisplayName("el mismo estado da siempre el mismo texto: las claves de los mapas van ordenadas")
    void textoEstable() {
        java.util.LinkedHashMap<String, Integer> deLaZALaA = new java.util.LinkedHashMap<>();
        deLaZALaA.put("z", 3);
        deLaZALaA.put("m", 2);
        deLaZALaA.put("a", 1);
        java.util.LinkedHashMap<String, Integer> deLaAALaZ = new java.util.LinkedHashMap<>();
        deLaAALaZ.put("a", 1);
        deLaAALaZ.put("m", 2);
        deLaAALaZ.put("z", 3);
        String uno = JsonDeCombate.escribir(
                new EstadoDeCombate(4, 10, 2, deLaZALaA, List.of(), null, deLaZALaA, List.of(), null));
        String otro = JsonDeCombate.escribir(
                new EstadoDeCombate(4, 10, 2, deLaAALaZ, List.of(), null, deLaAALaZ, List.of(), null));

        assertAll(
                () -> assertEquals(uno, otro),
                () -> assertEquals(true, uno.indexOf("\"a\"") < uno.indexOf("\"z\""), uno));
    }

    @Test
    @DisplayName("nulo, vacio o ilegible se leen como ausentes; escribir nulo es nulo")
    void ausentes() {
        assertAll(
                () -> assertNull(JsonDeCombate.escribir(null)),
                () -> assertNull(JsonDeCombate.perfil(null)),
                () -> assertNull(JsonDeCombate.perfil("   ")),
                () -> assertNull(JsonDeCombate.estado("{no es json")),
                () -> assertNull(JsonDeCombate.perfil("{\"nivel\":99}"),
                        "un nivel fuera de 1..8 no pasa la validacion del dominio: se trata como ausente"));
    }
}

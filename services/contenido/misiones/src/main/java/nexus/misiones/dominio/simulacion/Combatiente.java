package nexus.misiones.dominio.simulacion;

import java.util.List;
import java.util.Map;

/**
 * Un combatiente tal como lo ve el motor de combate: es a la vez ENTRADA y
 * SALIDA. El motor no guarda nada (motor-combate.yaml, «sin estado»): recibe el
 * estado de todos, lo transforma y lo devuelve, y quien lleva el combate —aqui
 * el simulador— guarda lo que devolvio para la llamada siguiente.
 *
 * @param estadisticas       nulas = las del catalogo de heroes en ese nivel, sin equipo
 * @param poderActual        nulo = su poder maximo (inicio del combate)
 * @param turnosJugados      turnos propios ya jugados; las cargas se cuentan sobre este contador
 * @param cargas             por accion, el valor de {@code turnosJugados} cuando se uso por ultima vez
 * @param equipamiento       nombres de las armas, armaduras e items equipados (Tablas 8 a 19)
 * @param epicas             epicas de la Tabla 20 que puede usar (hasta ocho)
 * @param ultimoDanoRecibido el ultimo golpe recibido, o nulo
 * @param recargas           solo de salida: por accion en carga, los turnos propios que le faltan
 * @param acciones           solo de salida: los codigos de lo que tiene, de la basica a la epica
 */
public record Combatiente(
        String id,
        String prototipo,
        int nivel,
        EstadisticasDeCombate estadisticas,
        int vidaActual,
        Integer poderActual,
        int turnosJugados,
        Map<String, Integer> cargas,
        List<EfectoActivo> efectos,
        List<String> equipamiento,
        List<String> epicas,
        GolpeRecibido ultimoDanoRecibido,
        Map<String, Integer> recargas,
        List<String> acciones) {

    public Combatiente {
        cargas = cargas == null ? Map.of() : Map.copyOf(cargas);
        efectos = efectos == null ? List.of() : List.copyOf(efectos);
        equipamiento = equipamiento == null ? List.of() : List.copyOf(equipamiento);
        epicas = epicas == null ? List.of() : List.copyOf(epicas);
        recargas = recargas == null ? Map.of() : Map.copyOf(recargas);
        acciones = acciones == null ? List.of() : List.copyOf(acciones);
    }

    /** Un combatiente que empieza un combate: poder al maximo, sin cargas ni efectos. */
    public static Combatiente alEmpezar(String id, String prototipo, int nivel, EstadisticasDeCombate estadisticas,
                                        int vidaActual, List<String> equipamiento, List<String> epicas) {
        return new Combatiente(id, prototipo, nivel, estadisticas, vidaActual, null, 0, Map.of(), List.of(),
                equipamiento, epicas, null, Map.of(), List.of());
    }

    public boolean enPie() {
        return vidaActual > 0;
    }

    /** Lo ultimo que recibio y de quien («Pare de fuego» lo retorna). */
    public record GolpeRecibido(String de, int cantidad) {
    }
}

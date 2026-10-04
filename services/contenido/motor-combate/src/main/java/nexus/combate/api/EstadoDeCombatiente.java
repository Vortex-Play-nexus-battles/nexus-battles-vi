package nexus.combate.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import nexus.combate.reglas.EstadoDeAccion;
import nexus.combate.reglas.TipoDeEfecto;

import java.util.List;
import java.util.Map;

/**
 * Espejo del esquema {@code Combatiente} de {@code motor-combate.yaml} 1.2.0.
 *
 * <p>Es a la vez ENTRADA y SALIDA: quien lleva la partida guarda lo que el motor
 * devuelve y lo manda tal cual en la llamada siguiente. {@code recargas} y
 * {@code acciones} son solo de salida; si vuelven en una peticion, se ignoran y
 * se recalculan.
 *
 * <p>Todo en tipos con caja: un campo ausente llega nulo y la traduccion decide
 * su valor por omision (nivel 1, poder maximo, cero turnos...). Un primitivo
 * convertiria «no lo mandaste» en un cero silencioso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoDeCombatiente(
        String id,
        Integer equipo,
        String prototipo,
        Integer nivel,
        EstadisticasDeCombate estadisticas,
        Integer vidaActual,
        Integer poderActual,
        Integer turnosJugados,
        Map<String, Integer> cargas,
        List<Efecto> efectos,
        List<String> equipamiento,
        List<String> epicas,
        GolpeRecibido ultimoDanoRecibido,
        Map<String, Integer> recargas,
        List<EstadoDeAccion> acciones) {

    /** Esquema {@code EstadisticasDeCombate}: en su nivel y con el equipamiento plano aplicado. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstadisticasDeCombate(Integer poder, Integer vida, Integer defensa,
                                        FormulaDetalle ataque, FormulaDetalle dano, FormulaDetalle sanar) {
    }

    /** Esquema {@code FormulaDetalle}: base + cantidadDados d caras. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FormulaDetalle(Integer base, Integer cantidadDados, Integer caras) {
    }

    /** Esquema {@code EfectoActivo}. {@code hastaSuTurno} es de salida: lo decide el tipo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Efecto(String codigo, String nombre, TipoDeEfecto tipo, Integer valor, Integer turnos,
                         Boolean hastaSuTurno, String origen) {
    }

    /** El ultimo golpe recibido y de quien («Pare de fuego» lo retorna). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GolpeRecibido(String de, Integer cantidad) {
    }
}

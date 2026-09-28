package nexus.combate.reglas;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Como juega la maquina — decision D-B7-12.
 *
 * <p>§7.6 pide que la IA use «algoritmos de aprendizaje profundo ... a partir de
 * los datos almacenados de todas las partidas». Eso queda FUERA de este bloque
 * (D-B7-13): no hay modelo, ni datos de entrenamiento, ni forma honesta de
 * simularlo. Lo que si pide el documento, y se cumple, es que «las normas de
 * combate son identicas, independientemente de si el adversario es controlado
 * por la inteligencia artificial o por otro jugador» (§6.1.3): la maquina juega
 * con su heroe real, paga poder, respeta cargas y no ataca a sus companeros,
 * porque su accion pasa por el mismo {@link MotorDeAcciones} que la de un
 * humano.
 *
 * <p>La politica es simple y DETERMINISTA para un mismo estado:
 * <ol>
 *   <li>Si su heroe ataca: la accion especial de ATAQUE mas cara que pueda
 *       jugar ya (desbloqueada, fuera de carga y con poder para pagarla);
 *       empate, la primera en el orden de la Tabla 7. Si no hay ninguna, el
 *       ataque basico.</li>
 *   <li>Objetivo: el rival en pie con MENOS vida; empate, el primero en el
 *       orden de los combatientes.</li>
 *   <li>Si su heroe es un sanador: sanacion basica sobre quien tenga menos
 *       vida en proporcion de su bando (el mismo incluido).</li>
 * </ol>
 * Las acciones de defensa no las usa. Es la regla mas corta que juega con las
 * reglas de verdad; cualquier otra seria una estrategia, y esa la decide el PO.
 */
public final class PoliticaDeLaMaquina {

    private PoliticaDeLaMaquina() {
    }

    /** Lo que decidio: la accion y a quien. */
    public record Decision(String accion, String objetivo) {
    }

    static Decision decidir(String idEjecutor, Mesa mesa, boolean porEquipos, Reglamento reglamento) {
        Contendiente yo = mesa.de(idEjecutor);
        FichaDeCombate ficha = mesa.ficha(idEjecutor);
        List<Contendiente> todos = mesa.todos();

        if (!yo.estadisticasResueltas().ataca()) {
            Contendiente masHerido = todos.stream()
                    .filter(c -> c.enPie() && (c.id().equals(idEjecutor) || yo.esCompaneroDe(c, porEquipos)))
                    .min(Comparator.comparingDouble(c -> (double) c.vida() / c.vidaMaxima()))
                    .orElse(yo);
            return new Decision(Reglamento.SANACION_BASICA, masHerido.id());
        }

        // max() de un flujo ordenado se queda con el PRIMERO de los empatados
        // (BinaryOperator.maxBy): a igual coste gana el orden de la Tabla 7.
        Optional<AccionDelCatalogo> especial = ficha.acciones().stream()
                .filter(a -> reglamento.tipoDe(a).filter(t -> t == TipoDeAccion.ATAQUE).isPresent())
                .filter(a -> yo.nivel() >= a.nivelRequerido())
                .filter(a -> fueraDeCarga(yo, a))
                .filter(a -> a.alcanzaCon(yo.poder()))
                .max(Comparator.comparingInt(a -> costeDe(a, yo)));

        Contendiente objetivo = todos.stream()
                .filter(c -> c.enPie() && yo.esRivalDe(c, porEquipos))
                .min(Comparator.comparingInt(Contendiente::vida))
                .orElse(null);
        return new Decision(especial.map(AccionDelCatalogo::nombre).orElse(Reglamento.ATAQUE_BASICO),
                objetivo == null ? null : objetivo.id());
    }

    private static int costeDe(AccionDelCatalogo accion, Contendiente yo) {
        return accion.todoElPoder() ? yo.poder() : accion.costoPoder();
    }

    private static boolean fueraDeCarga(Contendiente yo, AccionDelCatalogo accion) {
        Integer ultimo = yo.cargas().get(accion.nombre());
        int carga = accion.turnosDeCarga() > 0 ? accion.turnosDeCarga() : Reglamento.TURNOS_DE_CARGA_ACCION;
        return ultimo == null || MotorDeAcciones.turnosQueFaltan(carga, ultimo, yo.turnosJugados()) == 0;
    }
}

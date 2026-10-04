package nexus.combate.api;

import nexus.combate.reglas.Contendiente;
import nexus.combate.reglas.DanoRecibido;
import nexus.combate.reglas.EfectoActivo;
import nexus.combate.reglas.Estadisticas;
import nexus.combate.reglas.EstadoDeAccion;
import nexus.combate.reglas.Formula;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Del JSON del contrato al combatiente del dominio, y vuelta.
 *
 * <p>Aqui se decide que significa un campo AUSENTE (nivel 1, poder maximo, cero
 * turnos, sin cargas ni efectos) y se rechaza con 400 lo que el dominio no
 * podria ni construir: un efecto sin tipo, una carga nula, un nombre de objeto
 * vacio. Las cotas del juego —nivel de 1 a 8, vida no negativa— las valida el
 * propio dominio, y su {@code IllegalArgumentException} tambien sale como 400.
 */
final class TraductorDeCombate {

    /** §6.1.3: el modo cooperativo es «de hasta seis participantes». */
    static final int MAXIMO_DE_COMBATIENTES = 6;

    private TraductorDeCombate() {
    }

    static List<Contendiente> aDominio(List<EstadoDeCombatiente> combatientes, int minimo) {
        if (combatientes == null || combatientes.size() < minimo) {
            throw new PeticionInvalida(minimo == 1
                    ? "Hace falta al menos el combatiente que empieza."
                    : "Un combate necesita al menos " + minimo + " combatientes.");
        }
        if (combatientes.size() > MAXIMO_DE_COMBATIENTES) {
            throw new PeticionInvalida("Una partida admite hasta " + MAXIMO_DE_COMBATIENTES + " combatientes.");
        }
        List<Contendiente> lista = new ArrayList<>();
        for (EstadoDeCombatiente c : combatientes) {
            if (c == null) {
                throw new PeticionInvalida("Hay un combatiente vacio en la lista.");
            }
            lista.add(aDominio(c));
        }
        return lista;
    }

    static Contendiente aDominio(EstadoDeCombatiente c) {
        if (c.vidaActual() == null) {
            throw new PeticionInvalida("Falta la vida actual de " + c.id() + ".");
        }
        return new Contendiente(
                c.id(),
                c.equipo(),
                c.prototipo(),
                c.nivel() == null ? 1 : c.nivel(),
                estadisticas(c.id(), c.estadisticas()),
                c.vidaActual(),
                // Nulo: empieza con todo su poder. El motor lo acota a su maximo.
                c.poderActual() == null ? Integer.MAX_VALUE : c.poderActual(),
                c.turnosJugados() == null ? 0 : c.turnosJugados(),
                cargas(c.id(), c.cargas()),
                efectos(c.id(), c.efectos()),
                nombres(c.id(), "equipamiento", c.equipamiento()),
                nombres(c.id(), "epicas", c.epicas()),
                golpe(c.id(), c.ultimoDanoRecibido()));
    }

    static List<EstadoDeCombatiente> deDominio(List<Contendiente> combatientes,
                                               Map<String, List<EstadoDeAccion>> acciones,
                                               Map<String, Map<String, Integer>> recargas) {
        return combatientes.stream()
                .map(c -> deDominio(c, acciones.getOrDefault(c.id(), List.of()),
                        recargas.getOrDefault(c.id(), Map.of())))
                .toList();
    }

    static EstadoDeCombatiente deDominio(Contendiente c, List<EstadoDeAccion> acciones,
                                         Map<String, Integer> recargas) {
        Estadisticas e = c.estadisticasResueltas();
        return new EstadoDeCombatiente(
                c.id(), c.equipo(), c.prototipo(), c.nivel(),
                new EstadoDeCombatiente.EstadisticasDeCombate(e.poder(), e.vida(), e.defensa(),
                        formula(e.ataque()), formula(e.dano()), formula(e.sanar())),
                c.vida(), c.poder(), c.turnosJugados(),
                new LinkedHashMap<>(c.cargas()),
                c.efectos().stream()
                        .map(f -> new EstadoDeCombatiente.Efecto(f.codigo(), f.nombre(), f.tipo(), f.valor(),
                                f.turnos(), f.hastaSuTurno(), f.origen()))
                        .toList(),
                c.equipamiento(), c.epicas(),
                c.ultimoDanoRecibido() == null ? null
                        : new EstadoDeCombatiente.GolpeRecibido(c.ultimoDanoRecibido().de(),
                        c.ultimoDanoRecibido().cantidad()),
                new LinkedHashMap<>(recargas),
                acciones);
    }

    // ------------------------------------------------------------------ piezas

    private static Estadisticas estadisticas(String id, EstadoDeCombatiente.EstadisticasDeCombate e) {
        if (e == null) {
            return null;
        }
        if (e.poder() == null || e.vida() == null || e.defensa() == null) {
            throw new PeticionInvalida("Las estadisticas de " + id + " necesitan poder, vida y defensa.");
        }
        return new Estadisticas(e.poder(), e.vida(), e.defensa(),
                formula(id, e.ataque()), formula(id, e.dano()), formula(id, e.sanar()));
    }

    private static Formula formula(String id, EstadoDeCombatiente.FormulaDetalle f) {
        if (f == null) {
            return null;
        }
        if (f.base() == null || f.cantidadDados() == null || f.caras() == null) {
            throw new PeticionInvalida("Una formula de " + id + " necesita base, cantidadDados y caras.");
        }
        return new Formula(f.base(), f.cantidadDados(), f.caras());
    }

    private static EstadoDeCombatiente.FormulaDetalle formula(Formula f) {
        return f == null ? null : new EstadoDeCombatiente.FormulaDetalle(f.base(), f.cantidadDados(), f.caras());
    }

    private static Map<String, Integer> cargas(String id, Map<String, Integer> cargas) {
        if (cargas == null) {
            return Map.of();
        }
        Map<String, Integer> copia = new LinkedHashMap<>();
        cargas.forEach((accion, turno) -> {
            if (accion == null || accion.isBlank() || turno == null || turno < 0) {
                throw new PeticionInvalida("Las cargas de " + id + " llevan una accion y un turno no negativo.");
            }
            copia.put(accion, turno);
        });
        return copia;
    }

    private static List<EfectoActivo> efectos(String id, List<EstadoDeCombatiente.Efecto> efectos) {
        if (efectos == null) {
            return List.of();
        }
        List<EfectoActivo> lista = new ArrayList<>();
        for (EstadoDeCombatiente.Efecto e : efectos) {
            if (e == null || e.tipo() == null || e.valor() == null || e.turnos() == null) {
                throw new PeticionInvalida("Un efecto de " + id + " necesita codigo, tipo, valor y turnos.");
            }
            lista.add(new EfectoActivo(e.codigo(), e.nombre(), e.tipo(), e.valor(), e.turnos(), e.origen()));
        }
        return lista;
    }

    private static List<String> nombres(String id, String campo, List<String> nombres) {
        if (nombres == null) {
            return List.of();
        }
        if (nombres.stream().anyMatch(n -> n == null || n.isBlank())) {
            throw new PeticionInvalida("En " + campo + " de " + id + " hay un nombre vacio.");
        }
        return nombres;
    }

    private static DanoRecibido golpe(String id, EstadoDeCombatiente.GolpeRecibido golpe) {
        if (golpe == null) {
            return null;
        }
        if (golpe.cantidad() == null) {
            throw new PeticionInvalida("El ultimo golpe recibido por " + id + " necesita su cantidad.");
        }
        return new DanoRecibido(golpe.de(), golpe.cantidad());
    }
}

package nexus.combate.reglas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Un combatiente tal como lo ve el motor: su heroe y su estado en la partida.
 *
 * <p>El motor no guarda nada: recibe este estado, lo transforma y lo devuelve.
 * Quien lo guarda entre llamadas es {@code salas-partidas}. Por eso aqui esta
 * todo lo que una regla puede necesitar: vida, poder, cargas, efectos, el
 * ultimo golpe recibido y el equipo que lleva.
 *
 * <p>Inmutable: cada cambio produce un combatiente nuevo.
 *
 * @param id                 identificador en la partida
 * @param equipo             equipo en combate cooperativo; nulo sin equipos
 * @param prototipo          prototipo del catalogo de heroes («Guerrero Tanque»)
 * @param nivel              1 a 8 (§6.1.1)
 * @param estadisticas       en su nivel; nulas hasta que el motor las resuelva
 * @param vida               vida actual
 * @param poder              poder actual
 * @param turnosJugados      turnos propios ya jugados (las cargas se cuentan aqui)
 * @param cargas             por accion o epica, {@code turnosJugados} cuando se uso
 * @param efectos            efectos activos
 * @param equipamiento       nombres de armas e items equipados (Tablas 8 a 19)
 * @param epicas             epicas que puede usar (Tabla 20)
 * @param ultimoDanoRecibido ultimo golpe recibido, o nulo
 */
public record Contendiente(
        String id,
        Integer equipo,
        String prototipo,
        int nivel,
        Estadisticas estadisticas,
        int vida,
        int poder,
        int turnosJugados,
        Map<String, Integer> cargas,
        List<EfectoActivo> efectos,
        List<String> equipamiento,
        List<String> epicas,
        DanoRecibido ultimoDanoRecibido) {

    public static final int NIVEL_MINIMO = 1;
    public static final int NIVEL_MAXIMO = 8;

    public Contendiente {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Un combatiente necesita su identificador.");
        }
        if (prototipo == null || prototipo.isBlank()) {
            throw new IllegalArgumentException("Un combatiente necesita el prototipo de su heroe.");
        }
        if (nivel < NIVEL_MINIMO || nivel > NIVEL_MAXIMO) {
            throw new IllegalArgumentException("El nivel va de 1 a 8 (§6.1.1): " + nivel);
        }
        if (vida < 0 || poder < 0 || turnosJugados < 0) {
            throw new IllegalArgumentException("Vida, poder y turnos jugados no pueden ser negativos.");
        }
        cargas = cargas == null ? Map.of() : Map.copyOf(cargas);
        efectos = efectos == null ? List.of() : List.copyOf(efectos);
        equipamiento = equipamiento == null ? List.of() : List.copyOf(equipamiento);
        epicas = epicas == null ? List.of() : List.copyOf(epicas);
    }

    /** Sigue en pie: con vida. */
    public boolean enPie() {
        return vida > 0;
    }

    /** Vida maxima; exige las estadisticas ya resueltas. */
    public int vidaMaxima() {
        return estadisticasResueltas().vida();
    }

    /** Poder maximo; exige las estadisticas ya resueltas. */
    public int poderMaximo() {
        return estadisticasResueltas().poder();
    }

    public Estadisticas estadisticasResueltas() {
        return Objects.requireNonNull(estadisticas,
                "Las estadisticas de " + id + " no se han resuelto todavia.");
    }

    /** Si es del mismo bando que otro. Sin equipos, cada uno es su propio bando. */
    public boolean esCompaneroDe(Contendiente otro, boolean porEquipos) {
        if (otro.id.equals(id)) {
            return false;
        }
        return porEquipos && equipo != null && equipo.equals(otro.equipo);
    }

    /** Si es rival de otro: no es el mismo ni su companero. */
    public boolean esRivalDe(Contendiente otro, boolean porEquipos) {
        return !otro.id.equals(id) && !esCompaneroDe(otro, porEquipos);
    }

    /** La suma de los valores de sus efectos activos de un tipo. */
    public int sumaDe(TipoDeEfecto tipo) {
        return efectos.stream().filter(e -> e.tipo() == tipo).mapToInt(EfectoActivo::valor).sum();
    }

    public boolean tiene(TipoDeEfecto tipo) {
        return efectos.stream().anyMatch(e -> e.tipo() == tipo);
    }

    public Contendiente conEstadisticas(Estadisticas nuevas) {
        return new Contendiente(id, equipo, prototipo, nivel, nuevas, vida, poder, turnosJugados,
                cargas, efectos, equipamiento, epicas, ultimoDanoRecibido);
    }

    /** Otra vida, sin bajar de cero ni pasar de la maxima. */
    public Contendiente conVida(int nueva) {
        int acotada = Math.max(0, estadisticas == null ? nueva : Math.min(nueva, vidaMaxima()));
        return new Contendiente(id, equipo, prototipo, nivel, estadisticas, acotada, poder, turnosJugados,
                cargas, efectos, equipamiento, epicas, ultimoDanoRecibido);
    }

    /** Otro poder, sin bajar de cero ni pasar del maximo. */
    public Contendiente conPoder(int nuevo) {
        int acotado = Math.max(0, estadisticas == null ? nuevo : Math.min(nuevo, poderMaximo()));
        return new Contendiente(id, equipo, prototipo, nivel, estadisticas, vida, acotado, turnosJugados,
                cargas, efectos, equipamiento, epicas, ultimoDanoRecibido);
    }

    public Contendiente conEfectos(List<EfectoActivo> nuevos) {
        return new Contendiente(id, equipo, prototipo, nivel, estadisticas, vida, poder, turnosJugados,
                cargas, nuevos, equipamiento, epicas, ultimoDanoRecibido);
    }

    /**
     * Anade un efecto. Si ya llevaba uno con el mismo codigo y el mismo origen,
     * lo RENUEVA en vez de acumularlo: dos sangrados de la misma espada no
     * sangran el doble, se alargan.
     */
    public Contendiente conEfecto(EfectoActivo nuevo) {
        List<EfectoActivo> lista = new ArrayList<>();
        for (EfectoActivo e : efectos) {
            if (!(e.codigo().equals(nuevo.codigo()) && Objects.equals(e.origen(), nuevo.origen()))) {
                lista.add(e);
            }
        }
        lista.add(nuevo);
        return conEfectos(lista);
    }

    public Contendiente conCarga(String codigo, int turno) {
        Map<String, Integer> nuevas = new LinkedHashMap<>(cargas);
        nuevas.put(codigo, turno);
        return new Contendiente(id, equipo, prototipo, nivel, estadisticas, vida, poder, turnosJugados,
                nuevas, efectos, equipamiento, epicas, ultimoDanoRecibido);
    }

    public Contendiente conTurnosJugados(int turnos) {
        return new Contendiente(id, equipo, prototipo, nivel, estadisticas, vida, poder, turnos,
                cargas, efectos, equipamiento, epicas, ultimoDanoRecibido);
    }

    public Contendiente conUltimoDanoRecibido(DanoRecibido golpe) {
        return new Contendiente(id, equipo, prototipo, nivel, estadisticas, vida, poder, turnosJugados,
                cargas, efectos, equipamiento, epicas, golpe);
    }
}

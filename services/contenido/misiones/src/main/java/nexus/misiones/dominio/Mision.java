package nexus.misiones.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * La definicion de una mision (seccion 7.8.3): lo que la hace ser esa mision,
 * igual para todos los jugadores. Lo que depende del jugador —si la tiene
 * bloqueada, en curso o completada— no vive aqui sino en sus
 * {@link Ejecucion}es.
 *
 * <p>Las misiones se publican en una semilla versionada del repositorio, no
 * las crea nadie en caliente: el documento pide a cada equipo «disenar dos
 * misiones completas» (7.8.4), que es contenido, no datos de usuario.
 */
public record Mision(
        String id,
        Origen origen,
        String nombre,
        Categoria categoria,
        String descripcionBreve,
        String imagen,
        Dificultad dificultad,
        double duracionHoras,
        Integer nivelRecomendado,
        List<String> requisitosPrevios,
        String narrativa,
        String escenario,
        List<Objetivo> objetivos,
        List<GrupoDeEnemigos> enemigos,
        Jefe jefe,
        List<MasterDeMision> masters,
        RecompensasDeMision recompensas,
        boolean destacada,
        Instant disponibleHasta,
        Intentos intentos) {

    /** Seccion 7.8.2: «duracion extendida (24-72 horas)». */
    public static final double EXPLORACION_MINIMA_HORAS = 24;
    public static final double EXPLORACION_MAXIMA_HORAS = 72;

    /**
     * §6.1.1: «El nivel inicial de todos los personajes es uno (1) y puede
     * incrementarse hasta el nivel 8». Una mision que recomiende otro nivel no
     * la puede jugar nadie (D-42: el ejemplo del documento decia 15).
     */
    public static final int NIVEL_MINIMO = 1;
    public static final int NIVEL_MAXIMO = 8;

    /** Tambien es una ruta: `/api/v1/misiones/{misionId}`. */
    private static final Pattern IDENTIFICADOR = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");

    /**
     * Rutas literales que cuelgan de `/api/v1/misiones/`: una mision con uno de
     * estos identificadores quedaria tapada por ellas.
     */
    private static final List<String> RESERVADOS =
            List.of("destacadas", "en-curso", "historial", "estrategias", "ejecuciones");

    public Mision {
        if (id == null || !IDENTIFICADOR.matcher(id).matches() || RESERVADOS.contains(id)) {
            throw new IllegalArgumentException("Identificador de mision invalido: «" + id + "».");
        }
        Objects.requireNonNull(origen, "La mision «" + id + "» no dice de donde sale.");
        exigirTexto(nombre, id, "nombre");
        Objects.requireNonNull(categoria, "La mision «" + id + "» necesita categoria.");
        exigirTexto(descripcionBreve, id, "descripcionBreve");
        Objects.requireNonNull(dificultad, "La mision «" + id + "» necesita dificultad.");
        if (!(duracionHoras > 0)) {
            throw new IllegalArgumentException("La mision «" + id + "» necesita una duracion positiva.");
        }
        if (nivelRecomendado != null && (nivelRecomendado < NIVEL_MINIMO || nivelRecomendado > NIVEL_MAXIMO)) {
            throw new IllegalArgumentException("La mision «" + id + "» recomienda el nivel " + nivelRecomendado
                    + ", y un heroe solo va del " + NIVEL_MINIMO + " al " + NIVEL_MAXIMO + " (seccion 6.1.1).");
        }
        if (categoria == Categoria.EXPLORACION
                && (duracionHoras < EXPLORACION_MINIMA_HORAS || duracionHoras > EXPLORACION_MAXIMA_HORAS)) {
            throw new IllegalArgumentException(
                    "La exploracion «" + id + "» debe durar de 24 a 72 horas (seccion 7.8.2).");
        }
        exigirTexto(narrativa, id, "narrativa");
        requisitosPrevios = requisitosPrevios == null ? List.of() : List.copyOf(requisitosPrevios);
        objetivos = objetivos == null ? List.of() : List.copyOf(objetivos);
        if (objetivos.stream().noneMatch(Objetivo::principal)) {
            throw new IllegalArgumentException("La mision «" + id + "» necesita al menos un objetivo principal.");
        }
        enemigos = enemigos == null ? List.of() : List.copyOf(enemigos);
        if (enemigos.isEmpty() && jefe == null) {
            throw new IllegalArgumentException("La mision «" + id + "» no tiene ningun combate.");
        }
        masters = masters == null ? List.of() : List.copyOf(masters);
        Objects.requireNonNull(recompensas, "La mision «" + id + "» necesita su tabla de recompensas.");
        if (categoria == Categoria.DESAFIO && intentos == null) {
            throw new IllegalArgumentException(
                    "El desafio «" + id + "» necesita su limite de intentos (seccion 7.8.2).");
        }
        if (categoria != Categoria.DESAFIO && intentos != null) {
            throw new IllegalArgumentException("Solo los desafios limitan intentos: «" + id + "».");
        }
    }

    /** Combates regulares: uno por enemigo. */
    public int encuentrosRegulares() {
        return enemigos.stream().mapToInt(GrupoDeEnemigos::cantidad).sum();
    }

    /** «Numero de encuentros» (7.8.3), sin contar un Master que aparezca. */
    public int encuentros() {
        return encuentrosRegulares() + (jefe == null ? 0 : 1);
    }

    public long duracionEnMilisegundos(long milisegundosPorHora) {
        return Math.round(duracionHoras * milisegundosPorHora);
    }

    /** Sigue publicada: no es de tiempo limitado, o su plazo no ha vencido. */
    public boolean vigente(Instant ahora) {
        return disponibleHasta == null || ahora.isBefore(disponibleHasta);
    }

    /** Los tramos del filtro de duracion de la interfaz (§7.8.9). */
    public boolean enTramo(String tramo) {
        if (tramo == null || tramo.isBlank()) {
            return true;
        }
        return switch (tramo.toUpperCase(Locale.ROOT)) {
            case "HASTA_12" -> duracionHoras <= 12;
            case "DE_12_A_24" -> duracionHoras > 12 && duracionHoras <= 24;
            case "MAS_DE_24" -> duracionHoras > 24;
            default -> throw new IllegalArgumentException("Tramo de duracion desconocido: " + tramo);
        };
    }

    /**
     * «Recompensas destacadas» de la tarjeta (7.8.9): los creditos base, lo
     * garantizado y el botin, en ese orden, en palabras del jugador.
     */
    public List<String> recompensasDestacadas() {
        List<String> destacadas = new ArrayList<>();
        if (recompensas.creditos() > 0) {
            destacadas.add(recompensas.creditos() + " créditos");
        }
        for (RecompensasDeMision.ObjetoDeRecompensa objeto : recompensas.garantizadas()) {
            destacadas.add(objeto.cantidad() + " " + objeto.nombre());
        }
        for (RecompensasDeMision.ObjetoPotencial botin : recompensas.potenciales()) {
            destacadas.add(botin.nombre() + " (" + Math.round(botin.probabilidad() * 100) + " %)");
        }
        return destacadas.size() > 3 ? List.copyOf(destacadas.subList(0, 3)) : List.copyOf(destacadas);
    }

    private static void exigirTexto(String valor, String id, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("La mision «" + id + "» necesita " + campo + ".");
        }
    }
}

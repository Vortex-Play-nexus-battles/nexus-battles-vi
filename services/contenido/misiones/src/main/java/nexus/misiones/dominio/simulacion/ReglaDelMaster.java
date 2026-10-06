package nexus.misiones.dominio.simulacion;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cuanto de su prototipo conserva un Master: la regla de equilibrio de HU-SIM-006 (decision del PO del 5-oct).
 *
 * <p><b>La regla, en una frase.</b> El Master pelea con una fraccion de la vida y la defensa completas de su prototipo
 * en su nivel (heroe + 2, RG-107); la fraccion la fija el nivel del heroe y vale lo que hace falta para que un heroe
 * del nivel recomendado de la mision le gane aproximadamente la mitad de las veces.
 *
 * <p><b>Por que existe.</b> Desde D-42 los regulares llevan vida y defensa provisionales bajas (12 a 30 de vida en el
 * Templo). El Master, con las estadisticas completas de su prototipo, tenia 288 de vida y un heroe de nivel 8 le ganaba
 * el 1,3 % de las veces: la unica fuente de epicas (RG-085) quedaba cerrada en la practica. La fraccion cambia solo
 * la vida y la defensa; el nivel del Master (RG-107), su ataque, su dano y su epica no se tocan, y el piso del criterio
 * 1 de HU-SIM-006 ({@link RefuerzoDeMaster}: por encima del regular mas fuerte de su mision) se aplica despues, asi que
 * ninguna fraccion deja a un Master por debajo de un regular.
 *
 * <p><b>Un dato, no un numero en el codigo.</b> Vive en {@value #RECURSO}, con su version y la justificacion y las tasas
 * medidas en sus {@code notas}. El simulador de motor-combate lee el mismo archivo y la prueba
 * {@code BalanceDelMasterTest} mide, con el motor real, que con esas fracciones el duelo queda cerca del 50 %. Si se
 * toca el motor, las estadisticas de heroes o las semillas de misiones, esa prueba dice si hay que recalibrar.
 *
 * <p>Una fraccion unica no alcanza: un heroe de nivel 1 enfrenta a un Master de nivel 3 (el triple de estadisticas) y
 * uno de nivel 7 u 8 a uno de nivel 8; ademas, desde el nivel 2 el Master ya tiene las habilidades del nivel 4 y el
 * heroe no. Por eso hay una fraccion por nivel del heroe.
 *
 * @param version                 la version de la regla ({@code AAAA-MM-DD} de la medicion)
 * @param fraccionPorNivelDelHeroe de 0 (exclusivo) a 1 (inclusivo): la parte de la vida y la defensa completas que
 *                                conserva el Master, por cada nivel del heroe de 1 a 8
 */
public record ReglaDelMaster(String version, Map<Integer, Double> fraccionPorNivelDelHeroe) {

    /** Donde esta publicada, junto a las demas semillas del servicio. */
    public static final String RECURSO = "semilla/refuerzo-del-master.json";

    /** La regla de antes de HU-SIM-006: el Master conserva todo en todos los niveles. */
    public static final ReglaDelMaster COMPLETA = completa();

    private static final Logger BITACORA = LoggerFactory.getLogger(ReglaDelMaster.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> CAMPOS = Set.of("version", "notas", "fraccionPorNivelDelHeroe");
    private static final int NIVEL_MINIMO = 1;
    private static final int NIVEL_MAXIMO = 8;

    /** La regla publicada, leida una sola vez. */
    private static final class Publicada {
        static final ReglaDelMaster REGLA = cargar();
    }

    public ReglaDelMaster {
        fraccionPorNivelDelHeroe = Map.copyOf(new TreeMap<>(fraccionPorNivelDelHeroe));
    }

    /**
     * La regla del archivo versionado. Un archivo ilegible o invalido no tumba el arranque: se anota como error y el
     * Master pelea completo, como antes de la regla; que el archivo del repositorio es valido lo garantiza
     * {@code ReglaDelMasterTest}.
     */
    public static ReglaDelMaster publicada() {
        return Publicada.REGLA;
    }

    private static ReglaDelMaster cargar() {
        try (InputStream entrada = ReglaDelMaster.class.getClassLoader().getResourceAsStream(RECURSO)) {
            if (entrada == null) {
                throw new IllegalArgumentException("No se encontro el archivo " + RECURSO + ".");
            }
            ReglaDelMaster regla = leer(entrada, RECURSO);
            BITACORA.info("Regla del Master (version {}): fraccion de la vida y la defensa por nivel del heroe {}.",
                    regla.version(), regla.fraccionPorNivelDelHeroe());
            return regla;
        } catch (IOException | IllegalArgumentException e) {
            BITACORA.error("La regla del Master no se cargo y el Master pelea con las estadisticas completas de su "
                    + "prototipo: {}", e.getMessage());
            return COMPLETA;
        }
    }

    /**
     * @param nombre como llamar al archivo en los mensajes de error
     * @throws IllegalArgumentException si no es un JSON, si no trae version y notas, si trae un campo que no existe o
     *                                  si no trae, para cada nivel de 1 a 8, una fraccion mayor que 0 y no mayor que 1
     */
    public static ReglaDelMaster leer(InputStream entrada, String nombre) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(entrada);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(nombre + " no es un JSON valido: " + e.getOriginalMessage(), e);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException(nombre + " no es un objeto JSON.");
        }
        for (String campo : raiz.propertyNames()) {
            if (!CAMPOS.contains(campo)) {
                throw new IllegalArgumentException(nombre + " trae un campo que no existe: «" + campo + "».");
            }
        }
        JsonNode version = raiz.get("version");
        if (version == null || !version.isString() || version.asString().isBlank()) {
            throw new IllegalArgumentException(nombre + " no trae la version de la regla.");
        }
        JsonNode notas = raiz.get("notas");
        if (notas == null || !notas.isArray() || notas.isEmpty()) {
            throw new IllegalArgumentException(nombre + " no trae las notas: la regla va con su justificacion.");
        }
        JsonNode fracciones = raiz.get("fraccionPorNivelDelHeroe");
        if (fracciones == null || !fracciones.isObject()) {
            throw new IllegalArgumentException(nombre + " no trae «fraccionPorNivelDelHeroe».");
        }
        Map<Integer, Double> porNivel = new HashMap<>();
        for (String clave : fracciones.propertyNames()) {
            int nivel;
            try {
                nivel = Integer.parseInt(clave);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(nombre + ": «" + clave + "» no es un nivel del heroe.");
            }
            if (nivel < NIVEL_MINIMO || nivel > NIVEL_MAXIMO) {
                throw new IllegalArgumentException(nombre + ": el nivel " + nivel + " no existe; los heroes van de "
                        + NIVEL_MINIMO + " a " + NIVEL_MAXIMO + " (6.1.1).");
            }
            porNivel.put(nivel, fraccion(fracciones.get(clave), nombre, nivel));
        }
        for (int nivel = NIVEL_MINIMO; nivel <= NIVEL_MAXIMO; nivel++) {
            if (!porNivel.containsKey(nivel)) {
                throw new IllegalArgumentException(nombre + " no trae la fraccion del nivel " + nivel + ".");
            }
        }
        return new ReglaDelMaster(version.asString(), porNivel);
    }

    private static double fraccion(JsonNode valor, String nombre, int nivel) {
        if (valor == null || !valor.isNumber()) {
            throw new IllegalArgumentException(nombre + ": la fraccion del nivel " + nivel + " no es un numero.");
        }
        double fraccion = valor.asDouble();
        if (!(fraccion > 0 && fraccion <= 1.0)) {
            throw new IllegalArgumentException(nombre + ": la fraccion del nivel " + nivel + " es " + fraccion
                    + " y debe ser mayor que 0 y no mayor que 1: un Master no pelea con mas que su prototipo.");
        }
        return fraccion;
    }

    private static ReglaDelMaster completa() {
        Map<Integer, Double> porNivel = new HashMap<>();
        for (int nivel = NIVEL_MINIMO; nivel <= NIVEL_MAXIMO; nivel++) {
            porNivel.put(nivel, 1.0);
        }
        return new ReglaDelMaster("completa", porNivel);
    }

    /** La fraccion que conserva un Master frente a un heroe de ese nivel. */
    public double fraccionPara(int nivelDelHeroe) {
        Double fraccion = fraccionPorNivelDelHeroe.get(nivelDelHeroe);
        if (fraccion == null) {
            throw new IllegalArgumentException("La regla del Master no tiene fraccion para el nivel " + nivelDelHeroe
                    + " del heroe: van de " + NIVEL_MINIMO + " a " + NIVEL_MAXIMO + ".");
        }
        return fraccion;
    }

    /** La vida que le queda al Master de la completa de su prototipo, antes del escalon y del piso del criterio 1. */
    public int vida(int vidaCompleta, int nivelDelHeroe) {
        return Math.max(1, (int) Math.round(vidaCompleta * fraccionPara(nivelDelHeroe)));
    }

    /** La defensa que le queda al Master de la completa de su prototipo, antes del escalon y del piso del criterio 1. */
    public int defensa(int defensaCompleta, int nivelDelHeroe) {
        return (int) Math.round(defensaCompleta * fraccionPara(nivelDelHeroe));
    }
}

package nexus.misiones.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import nexus.misiones.dominio.CatalogoDeEstrategiasDeEnemigos;
import nexus.misiones.dominio.EstrategiaPredefinida;
import nexus.misiones.ia.Tabla7Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las estrategias predefinidas de los enemigos (HU-SIM-004), leidas una vez al arrancar de
 * {@value #RECURSO}: datos versionados en el repositorio, revisados en un PR como cualquier otro contenido.
 *
 * <h2>Una estrategia mala no tumba el servicio</h2>
 *
 * A diferencia de la semilla de misiones (que es estricta y no arranca con una errata), aqui cada estrategia se valida
 * por separado contra la copia local de la Tabla 7 ({@link Tabla7Local}) y la que no pasa se descarta con su motivo
 * en {@link #rechazadas()} y en la bitacora: ese prototipo y tramo caen a la heuristica de siempre
 * ({@code RotacionesPorDefectoDeEnemigos}). Heroes no se consulta aqui: al arrancar puede no estar levantado, y la
 * validacion contra la regla de heroes ocurre en el primer uso ({@code EstrategiasPredefinidas}). Que la semilla del
 * repositorio no tenga rechazos lo garantiza una prueba, para que esto sea una red de seguridad y no una forma de
 * publicar contenido roto.
 *
 * <h2>Que se valida</h2>
 *
 * El prototipo es de la Tabla 7; el tramo es un desbloqueo (1, 4 u 8, RC-01) y no esta repetido para ese prototipo; la
 * estrategia tiene de una a tres rotaciones (7.8.5), ninguna vacia; cada paso es una accion de ese prototipo ya
 * desbloqueada en el tramo (se acepta sin tildes ni mayusculas y se guarda con el nombre exacto de la tabla); y el
 * ataque basico no es un paso, porque ya es el respaldo y dejaria sin alcanzar a las rotaciones que le siguen.
 */
public final class CatalogoDeEstrategiasDesdeSemilla implements CatalogoDeEstrategiasDeEnemigos {

    public static final String RECURSO = "semilla/estrategias-de-enemigos.json";

    private static final Logger BITACORA = LoggerFactory.getLogger(CatalogoDeEstrategiasDesdeSemilla.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> CAMPOS = Set.of("id", "prototipo", "desdeNivel", "rotaciones", "justificacion");
    private static final int ROTACIONES_MAXIMAS = 3;
    private static final String ATAQUE_BASICO = "Ataque básico";

    /** Una estrategia que no se publico, y por que. */
    public record Rechazo(String estrategia, String motivo) {
    }

    private final String version;
    private final List<EstrategiaPredefinida> estrategias;
    private final List<Rechazo> rechazadas;
    private final Map<String, EstrategiaPredefinida> porPrototipoYTramo;

    private CatalogoDeEstrategiasDesdeSemilla(String version, List<EstrategiaPredefinida> estrategias,
                                              List<Rechazo> rechazadas) {
        this.version = version;
        this.estrategias = List.copyOf(estrategias);
        this.rechazadas = List.copyOf(rechazadas);
        Map<String, EstrategiaPredefinida> indice = new LinkedHashMap<>();
        estrategias.forEach(e -> indice.put(clave(e.prototipo(), e.desdeNivel()), e));
        this.porPrototipoYTramo = Map.copyOf(indice);
    }

    /** Lee la semilla del classpath. Nunca lanza: lo que no se pueda leer queda en {@link #rechazadas()}. */
    public static CatalogoDeEstrategiasDesdeSemilla cargar() {
        try (InputStream entrada = CatalogoDeEstrategiasDesdeSemilla.class.getClassLoader()
                .getResourceAsStream(RECURSO)) {
            if (entrada == null) {
                return vacio(RECURSO, "No se encontro el archivo.", BITACORA::warn);
            }
            return leer(entrada, RECURSO);
        } catch (IOException e) {
            return vacio(RECURSO, "No se pudo leer: " + e.getMessage(), BITACORA::warn);
        }
    }

    static CatalogoDeEstrategiasDesdeSemilla leer(InputStream entrada, String nombre) {
        return leer(entrada, nombre, BITACORA::warn);
    }

    /** @param bitacora donde se anota cada estrategia rechazada (las pruebas la recogen; el servicio, el log) */
    static CatalogoDeEstrategiasDesdeSemilla leer(InputStream entrada, String nombre, Consumer<String> bitacora) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(entrada);
        } catch (JacksonException e) {
            return vacio(nombre, "No es un JSON valido: " + e.getOriginalMessage(), bitacora);
        } catch (RuntimeException e) {
            return vacio(nombre, "No se pudo leer: " + e.getMessage(), bitacora);
        }
        JsonNode lista = raiz == null ? null : raiz.get("estrategias");
        if (lista == null || !lista.isArray()) {
            return vacio(nombre, "No trae la lista «estrategias».", bitacora);
        }
        String version = raiz.has("version") ? raiz.get("version").asString("") : "";

        List<EstrategiaPredefinida> validas = new ArrayList<>();
        List<Rechazo> rechazos = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> tramos = new HashSet<>();
        int posicion = 0;
        for (JsonNode nodo : lista) {
            posicion++;
            String id = nodo.isObject() && nodo.hasNonNull("id") ? nodo.get("id").asString("") : "";
            String quien = id.isBlank() ? "(sin id, posicion " + posicion + ")" : id;
            try {
                EstrategiaPredefinida e = validar(nodo, ids, tramos);
                ids.add(e.id());
                tramos.add(clave(e.prototipo(), e.desdeNivel()));
                validas.add(e);
            } catch (Invalida invalida) {
                rechazos.add(new Rechazo(quien, invalida.getMessage()));
                bitacora.accept("La estrategia predefinida «" + quien + "» de " + nombre
                        + " no se usa y ese prototipo y tramo caen a la heuristica por defecto: "
                        + invalida.getMessage());
            }
        }
        avisarLoQueFalta(validas);
        BITACORA.info("Estrategias predefinidas de enemigos (version {}): {} validas, {} rechazadas.", version,
                validas.size(), rechazos.size());
        return new CatalogoDeEstrategiasDesdeSemilla(version, validas, rechazos);
    }

    private static CatalogoDeEstrategiasDesdeSemilla vacio(String nombre, String motivo, Consumer<String> bitacora) {
        bitacora.accept("Las estrategias predefinidas de enemigos (" + nombre + ") no se cargaron y todos los enemigos "
                + "usan la heuristica por defecto: " + motivo);
        return new CatalogoDeEstrategiasDesdeSemilla("", List.of(), List.of(new Rechazo(nombre, nombre + ": " + motivo)));
    }

    // ---------------------------------------------------------------- validacion de una estrategia

    private static final class Invalida extends Exception {
        Invalida(String motivo) {
            super(motivo);
        }
    }

    private static EstrategiaPredefinida validar(JsonNode nodo, Set<String> ids, Set<String> tramos)
            throws Invalida {
        if (!nodo.isObject()) {
            throw new Invalida("No es un objeto.");
        }
        for (String campo : nodo.propertyNames()) {
            if (!CAMPOS.contains(campo)) {
                throw new Invalida("Trae un campo que no existe: «" + campo + "».");
            }
        }
        String id = texto(nodo, "id");
        if (id == null || id.isBlank()) {
            throw new Invalida("No tiene id.");
        }
        if (ids.contains(id)) {
            throw new Invalida("El id ya lo usa otra estrategia.");
        }
        String prototipo = texto(nodo, "prototipo");
        if (prototipo == null || !Tabla7Local.PROTOTIPOS.contains(prototipo)) {
            throw new Invalida("«" + prototipo + "» no es un prototipo de la Tabla 7.");
        }
        JsonNode desde = nodo.get("desdeNivel");
        if (desde == null || !desde.isInt() || !Tabla7Local.NIVELES_DE_DESBLOQUEO.contains(desde.intValue())) {
            throw new Invalida("desdeNivel tiene que ser 1, 4 u 8: los niveles en que se desbloquean las habilidades"
                    + " (RC-01).");
        }
        int nivel = desde.intValue();
        if (tramos.contains(clave(prototipo, nivel))) {
            throw new Invalida("Ya hay otra estrategia para " + prototipo + " desde el nivel " + nivel + ".");
        }
        JsonNode rotaciones = nodo.get("rotaciones");
        if (rotaciones == null || !rotaciones.isArray() || rotaciones.isEmpty() || rotaciones.size() > ROTACIONES_MAXIMAS) {
            throw new Invalida("Una estrategia admite de una a tres rotaciones (7.8.5).");
        }
        List<List<String>> canonicas = new ArrayList<>();
        int numero = 0;
        for (JsonNode rotacion : rotaciones) {
            numero++;
            if (!rotacion.isArray() || rotacion.isEmpty()) {
                throw new Invalida("La rotacion " + numero + " no tiene pasos.");
            }
            List<String> pasos = new ArrayList<>();
            for (JsonNode paso : rotacion) {
                pasos.add(paso(paso, prototipo, nivel, numero));
            }
            canonicas.add(pasos);
        }
        return new EstrategiaPredefinida(id, prototipo, nivel, canonicas);
    }

    /** El paso con el nombre exacto de la Tabla 7, o la razon por la que no vale. */
    private static String paso(JsonNode paso, String prototipo, int nivel, int rotacion) throws Invalida {
        String escrito = paso.isString() ? paso.asString() : null;
        if (escrito == null || escrito.isBlank()) {
            throw new Invalida("La rotacion " + rotacion + " tiene un paso que no es un nombre de habilidad.");
        }
        if (ATAQUE_BASICO.equalsIgnoreCase(escrito.trim())) {
            throw new Invalida("La rotacion " + rotacion + " usa «" + ATAQUE_BASICO + "»: ya es el respaldo de toda"
                    + " estrategia (7.8.5) y como paso dejaria sin alcanzar a las rotaciones que le siguen.");
        }
        String exacto = Tabla7Local.canonica(prototipo, escrito).orElseThrow(() -> new Invalida(
                "La rotacion " + rotacion + " usa «" + escrito + "», que no es una habilidad de " + prototipo
                        + " en la Tabla 7."));
        if (!Tabla7Local.desbloqueadasEn(prototipo, nivel).contains(exacto)) {
            int desbloqueo = Tabla7Local.NIVELES_DE_DESBLOQUEO.get(Tabla7Local.accionesDe(prototipo).indexOf(exacto));
            throw new Invalida("La rotacion " + rotacion + " usa «" + exacto + "», que " + prototipo
                    + " todavia no tiene en el nivel " + nivel + " (la desbloquea en el " + desbloqueo + ").");
        }
        return exacto;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor != null && valor.isString() ? valor.asString() : null;
    }

    /** Lo que no se rechazo pero tampoco esta escrito: tambien cae a la heuristica, y conviene saberlo. */
    private static void avisarLoQueFalta(List<EstrategiaPredefinida> validas) {
        Set<String> escritas = new HashSet<>();
        validas.forEach(e -> escritas.add(clave(e.prototipo(), e.desdeNivel())));
        List<String> faltan = new ArrayList<>();
        for (String prototipo : Tabla7Local.PROTOTIPOS) {
            for (int tramo : Tabla7Local.NIVELES_DE_DESBLOQUEO) {
                if (!escritas.contains(clave(prototipo, tramo))) {
                    faltan.add(prototipo + " desde el nivel " + tramo);
                }
            }
        }
        if (!faltan.isEmpty()) {
            BITACORA.info("Sin estrategia predefinida (usan la heuristica por defecto): {}", faltan);
        }
    }

    private static String clave(String prototipo, int tramo) {
        return prototipo + "@" + tramo;
    }

    // ---------------------------------------------------------------- lo que se consulta

    public String version() {
        return version;
    }

    /** Las estrategias validas, en el orden del archivo. */
    public List<EstrategiaPredefinida> todas() {
        return estrategias;
    }

    /** Las que no se publicaron, con su motivo. Vacia si todo el archivo es valido. */
    public List<Rechazo> rechazadas() {
        return rechazadas;
    }

    @Override
    public Optional<EstrategiaPredefinida> para(String prototipo, int nivel) {
        int tramo = Tabla7Local.tramoDe(nivel);
        if (prototipo == null || tramo == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(porPrototipoYTramo.get(clave(prototipo, tramo)));
    }
}

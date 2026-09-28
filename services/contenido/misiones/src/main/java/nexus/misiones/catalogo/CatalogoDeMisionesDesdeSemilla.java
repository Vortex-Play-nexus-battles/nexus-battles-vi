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
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Origen;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El catalogo de misiones, leido una vez al arrancar desde las semillas
 * versionadas del repositorio.
 *
 * <h2>Por que una semilla y no una coleccion editable</h2>
 *
 * Las misiones son contenido (7.8.4: cada equipo «disena» las suyas), no datos
 * de usuario: se revisan en un PR como cualquier otro cambio, y un despliegue
 * publica exactamente lo que se reviso. Lo que si se guarda en base de datos
 * es lo de cada jugador: sus ejecuciones, estrategias y favoritas.
 *
 * <h2>Dos semillas, y la segunda marcada</h2>
 *
 * <ul>
 *   <li>{@value #DEL_DOCUMENTO}: lo que dice el documento (7.8.14 y Tabla 20),
 *       siempre.</li>
 *   <li>{@value #PROVISIONAL_DE_DEV}: una mision tecnica para el banco E2E y el
 *       desarrollo local, solo si se pide ({@code MISIONES_SEMILLA_PROVISIONAL}).
 *       Se exige que su origen sea {@link Origen#PROVISIONAL_DEV} y que su
 *       nombre empiece por {@value #PREFIJO_PROVISIONAL}: una mision inventada
 *       nunca pasa por contenido del juego.</li>
 * </ul>
 *
 * <p>La lectura es estricta: un campo que no existe (una errata en la semilla)
 * tumba el arranque en vez de publicarse a medias.
 */
public final class CatalogoDeMisionesDesdeSemilla implements CatalogoDeMisiones {

    public static final String DEL_DOCUMENTO = "semilla/misiones-del-documento.json";
    public static final String PROVISIONAL_DE_DEV = "semilla/misiones-provisionales-dev.json";
    public static final String PREFIJO_PROVISIONAL = "[PROVISIONAL DE DEV]";

    /** Estricta a proposito: en Jackson 3 un campo desconocido se ignora por omision. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final List<Mision> misiones;
    private final Map<String, Mision> porId;
    private final List<EpicaDeTabla20> tabla20;

    private CatalogoDeMisionesDesdeSemilla(List<Mision> misiones, List<EpicaDeTabla20> tabla20) {
        this.misiones = List.copyOf(misiones);
        Map<String, Mision> indice = new LinkedHashMap<>();
        misiones.forEach(m -> indice.put(m.id(), m));
        this.porId = Map.copyOf(indice);
        this.tabla20 = List.copyOf(tabla20);
    }

    /**
     * @param conProvisional si se suma la semilla provisional de desarrollo
     * @throws IllegalStateException si una semilla falta, no se lee o no es coherente
     */
    public static CatalogoDeMisionesDesdeSemilla cargar(boolean conProvisional) {
        SemillaDeMisiones documento = leer(DEL_DOCUMENTO);
        SemillaDeMisiones provisional = conProvisional ? leer(PROVISIONAL_DE_DEV) : null;
        return desde(documento, provisional);
    }

    /**
     * Arma el catalogo comprobando lo que el tipo no puede comprobar: ids
     * unicos, requisitos que existen, una fila de la Tabla 20 por tipo y las
     * marcas de la semilla provisional.
     *
     * @param provisional nula si no se carga
     */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones provisional) {
        List<Mision> todas = new ArrayList<>();
        for (Mision mision : documento.misiones()) {
            if (mision.origen() != Origen.DOCUMENTO) {
                throw new IllegalStateException("La semilla del documento solo publica misiones del documento: «"
                        + mision.id() + "» dice " + mision.origen() + ".");
            }
            todas.add(mision);
        }
        if (provisional != null) {
            for (Mision mision : provisional.misiones()) {
                if (mision.origen() != Origen.PROVISIONAL_DEV || !mision.nombre().startsWith(PREFIJO_PROVISIONAL)) {
                    throw new IllegalStateException("La mision provisional «" + mision.id()
                            + "» debe tener origen PROVISIONAL_DEV y su nombre empezar por " + PREFIJO_PROVISIONAL
                            + ".");
                }
                todas.add(mision);
            }
            if (!provisional.tabla20().isEmpty()) {
                throw new IllegalStateException("La Tabla 20 es del documento: la semilla provisional no la toca.");
            }
        }
        Set<String> ids = new HashSet<>();
        for (Mision mision : todas) {
            if (!ids.add(mision.id())) {
                throw new IllegalStateException("Dos misiones con el mismo identificador: «" + mision.id() + "».");
            }
        }
        for (Mision mision : todas) {
            for (String requisito : mision.requisitosPrevios()) {
                if (!ids.contains(requisito) || requisito.equals(mision.id())) {
                    throw new IllegalStateException("«" + mision.id() + "» pide completar «" + requisito
                            + "», que no es otra mision publicada.");
                }
            }
        }
        Set<String> tipos = new HashSet<>();
        for (EpicaDeTabla20 fila : documento.tabla20()) {
            if (!tipos.add(fila.prototipo())) {
                throw new IllegalStateException("La Tabla 20 repite el tipo «" + fila.prototipo() + "».");
            }
        }
        return new CatalogoDeMisionesDesdeSemilla(todas, documento.tabla20());
    }

    /** Lee una semilla del classpath, sin tolerar campos desconocidos. */
    static SemillaDeMisiones leer(String recurso) {
        try (InputStream entrada = CatalogoDeMisionesDesdeSemilla.class.getClassLoader()
                .getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontro la semilla " + recurso + ".");
            }
            return leer(entrada, recurso);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la semilla " + recurso + ".", e);
        }
    }

    static SemillaDeMisiones leer(InputStream entrada, String nombre) {
        try {
            return JSON.readValue(entrada, SemillaDeMisiones.class);
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("La semilla " + nombre + " no es valida: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Mision> todas() {
        return misiones;
    }

    @Override
    public Optional<Mision> buscar(String id) {
        return Optional.ofNullable(id == null ? null : porId.get(id));
    }

    @Override
    public List<EpicaDeTabla20> tabla20() {
        return tabla20;
    }
}

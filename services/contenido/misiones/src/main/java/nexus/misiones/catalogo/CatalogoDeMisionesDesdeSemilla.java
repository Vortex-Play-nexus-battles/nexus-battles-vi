package nexus.misiones.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.MasterDeMision;
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
 * <h2>Tres semillas, y la provisional marcada</h2>
 *
 * <ul>
 *   <li>{@value #DEL_DOCUMENTO}: lo que dice el documento (7.8.14 y Tabla 20),
 *       siempre.</li>
 *   <li>{@value #DE_PROGRESION}: las misiones de historia de nivel 1 a 7 que
 *       llevan a un heroe nuevo hasta el Templo (D-42), siempre. Se exige que
 *       su origen sea {@link Origen#PROGRESION} y que cada una diga su nivel
 *       recomendado.</li>
 *   <li>{@value #PROVISIONAL_DE_DEV}: una mision tecnica para el banco E2E y el
 *       desarrollo local, solo si se pide ({@code MISIONES_SEMILLA_PROVISIONAL}).
 *       Se exige que su origen sea {@link Origen#PROVISIONAL_DEV} y que su
 *       nombre empiece por {@value #PREFIJO_PROVISIONAL}: una mision inventada
 *       nunca pasa por contenido del juego.</li>
 *   <li>una semilla EXTRA, de disco y no del servicio, solo si se da su ruta
 *       ({@code MISIONES_SEMILLA_EXTRA}): la monta el banco E2E para tener una
 *       mision con un Master que siempre aparece. Tiene las mismas reglas que
 *       la provisional (origen {@link Origen#PROVISIONAL_DEV}, nombre marcado,
 *       sin Tabla 20). Su contenido no esta en el jar, asi que un entorno que
 *       no monte el archivo no puede publicarla; y si da la ruta de un archivo
 *       que no existe, el servicio no arranca.</li>
 * </ul>
 *
 * <p>La lectura es estricta: un campo que no existe (una errata en la semilla)
 * tumba el arranque en vez de publicarse a medias.
 */
public final class CatalogoDeMisionesDesdeSemilla implements CatalogoDeMisiones {

    public static final String DEL_DOCUMENTO = "semilla/misiones-del-documento.json";
    /** D-42: las misiones de historia de nivel 1 a 7 que llevan hasta el Templo. */
    public static final String DE_PROGRESION = "semilla/misiones-de-progresion.json";
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
        return cargar(conProvisional, null);
    }

    /**
     * @param conProvisional si se suma la semilla provisional de desarrollo
     * @param semillaExtra   ruta de una semilla de disco (la del banco E2E), o nula
     * @throws IllegalStateException si una semilla falta, no se lee o no es coherente, incluida
     *                               una ruta extra que no existe
     */
    public static CatalogoDeMisionesDesdeSemilla cargar(boolean conProvisional, Path semillaExtra) {
        SemillaDeMisiones documento = leer(DEL_DOCUMENTO);
        SemillaDeMisiones progresion = leer(DE_PROGRESION);
        SemillaDeMisiones provisional = conProvisional ? leer(PROVISIONAL_DE_DEV) : null;
        SemillaDeMisiones extra = semillaExtra == null ? null : leer(semillaExtra);
        return desde(documento, progresion, provisional, extra);
    }

    /** El catalogo sin la semilla de progresion, como era antes de D-42. */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones provisional) {
        return desde(documento, null, provisional);
    }

    /**
     * Arma el catalogo comprobando lo que el tipo no puede comprobar: ids
     * unicos, requisitos que existen, una fila de la Tabla 20 por tipo, las
     * marcas de la semilla provisional y que la de progresion sea una cadena
     * de historia con nivel recomendado.
     *
     * @param progresion  nula si no se carga
     * @param provisional nula si no se carga
     */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones progresion,
                                                SemillaDeMisiones provisional) {
        return desde(documento, progresion, provisional, null);
    }

    /**
     * Lo mismo, con la semilla extra del banco E2E.
     *
     * @param extra nula si no se carga; sigue las reglas de la provisional
     */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones progresion,
                                                SemillaDeMisiones provisional, SemillaDeMisiones extra) {
        List<Mision> todas = new ArrayList<>();
        for (Mision mision : documento.misiones()) {
            if (mision.origen() != Origen.DOCUMENTO) {
                throw new IllegalStateException("La semilla del documento solo publica misiones del documento: «"
                        + mision.id() + "» dice " + mision.origen() + ".");
            }
            todas.add(mision);
        }
        if (progresion != null) {
            if (!progresion.tabla20().isEmpty()) {
                throw new IllegalStateException("La Tabla 20 es del documento: la semilla de progresion no la toca.");
            }
            for (Mision mision : progresion.misiones()) {
                if (mision.origen() != Origen.PROGRESION) {
                    throw new IllegalStateException("La semilla de progresion solo publica misiones de progresion: «"
                            + mision.id() + "» dice " + mision.origen() + ".");
                }
                if (mision.nivelRecomendado() == null) {
                    throw new IllegalStateException("La mision de progresion «" + mision.id()
                            + "» necesita su nivel recomendado: es lo que ordena la progresion.");
                }
                todas.add(mision);
            }
        }
        agregarProvisionales(todas, provisional, "provisional");
        agregarProvisionales(todas, extra, "extra");
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
        Set<String> epicasDeLaTabla = new HashSet<>();
        for (EpicaDeTabla20 fila : documento.tabla20()) {
            if (!tipos.add(fila.prototipo())) {
                throw new IllegalStateException("La Tabla 20 repite el tipo «" + fila.prototipo() + "».");
            }
            if (!epicasDeLaTabla.add(clave(fila.epica().nombre()))) {
                throw new IllegalStateException("La Tabla 20 repite la épica «" + fila.epica().nombre()
                        + "»: cada tipo de héroe tiene la suya.");
            }
        }
        exigirEpicasExclusivas(todas);
        return new CatalogoDeMisionesDesdeSemilla(todas, documento.tabla20());
    }

    /** Las misiones de una semilla que no es del juego: marcadas, sin Tabla 20. */
    private static void agregarProvisionales(List<Mision> todas, SemillaDeMisiones semilla, String cual) {
        if (semilla == null) {
            return;
        }
        for (Mision mision : semilla.misiones()) {
            if (mision.origen() != Origen.PROVISIONAL_DEV || !mision.nombre().startsWith(PREFIJO_PROVISIONAL)) {
                throw new IllegalStateException("La mision " + cual + " «" + mision.id()
                        + "» debe tener origen PROVISIONAL_DEV y su nombre empezar por " + PREFIJO_PROVISIONAL
                        + ".");
            }
            todas.add(mision);
        }
        if (!semilla.tabla20().isEmpty()) {
            throw new IllegalStateException("La Tabla 20 es del documento: la semilla " + cual + " no la toca.");
        }
    }

    /**
     * «Posesión de una habilidad épica exclusiva» (7.8.4, HU-SIM-006): cada Master de una misión suelta una épica que
     * ningún otro Master suelta, sea por su nombre o por el producto del catálogo que entrega. El mismo Master
     * repetido en dos misiones con su misma épica es el mismo Master. Los Master afines de la Tabla 20 (uno por tipo
     * de héroe) quedan fuera: un Master de misión puede soltar la épica de una fila de la tabla (decisión del PO de
     * HU-MIS-012), y es la fila quien la comparte, no otro Master de misión.
     */
    private static void exigirEpicasExclusivas(List<Mision> misiones) {
        Map<String, MasterDeMision> dueno = new LinkedHashMap<>();
        for (Mision mision : misiones) {
            for (MasterDeMision master : mision.masters()) {
                List<String> claves = new ArrayList<>(List.of("épica " + clave(master.epica().nombre())));
                if (master.epica().entregable()) {
                    claves.add("producto " + master.epica().productoId().trim());
                }
                for (String clave : claves) {
                    MasterDeMision previo = dueno.putIfAbsent(clave, master);
                    if (previo != null && !mismoMaster(previo, master)) {
                        String repetido = clave.startsWith("producto ")
                                ? "El producto " + master.epica().productoId() + " del catálogo, la épica «"
                                        + master.epica().nombre() + "»,"
                                : "La épica «" + master.epica().nombre() + "»";
                        throw new IllegalStateException(repetido + " la sueltan dos Máster distintos, «"
                                + previo.nombre() + "» y «" + master.nombre() + "» (misión «" + mision.id()
                                + "»): cada Máster tiene su propia épica exclusiva (7.8.4).");
                    }
                }
            }
        }
    }

    private static boolean mismoMaster(MasterDeMision a, MasterDeMision b) {
        return clave(a.nombre()).equals(clave(b.nombre()));
    }

    /** El nombre sin tildes, mayúsculas ni espacios de más: «Velo de Sombras» y «velo  de sombras» son la misma. */
    private static String clave(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .trim().replaceAll("\\s+", " ");
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

    /** Lee una semilla de un archivo del disco; si no esta, el servicio no arranca. */
    static SemillaDeMisiones leer(Path archivo) {
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No se encontro la semilla extra " + archivo
                    + ": si no se va a usar, quitar MISIONES_SEMILLA_EXTRA.");
        }
        try (InputStream entrada = Files.newInputStream(archivo)) {
            return leer(entrada, archivo.toString());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la semilla " + archivo + ".", e);
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

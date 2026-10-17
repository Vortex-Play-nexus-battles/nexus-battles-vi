package nexus.misiones.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Epica;
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
 * <h2>Cuatro semillas, y la provisional marcada</h2>
 *
 * <ul>
 *   <li>{@value #DEL_DOCUMENTO}: lo que dice el documento (7.8.14 y Tabla 20) y
 *       la configuracion que comparten todas las misiones (la probabilidad de
 *       Master por dificultad), siempre.</li>
 *   <li>{@value #DE_PROGRESION}: las misiones de historia de nivel 1 a 7 que
 *       llevan a un heroe nuevo hasta el Templo (D-42), siempre. Se exige que
 *       su origen sea {@link Origen#PROGRESION} y que cada una diga su nivel
 *       recomendado.</li>
 *   <li>{@value #DEL_EQUIPO}: las misiones que disena el equipo (RF-MIS-56),
 *       siempre. Se exige que su origen sea {@link Origen#EQUIPO} y que
 *       cumplan lo que el documento pide a una mision del equipo (RF-MIS-57 y
 *       58): enemigos regulares, jefe final y al menos un Master con su
 *       epica.</li>
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
 * tumba el arranque en vez de publicarse a medias. Y lo mismo una mision cuyo
 * Master no aparece con la probabilidad de su dificultad (RF-MIS-59).
 */
public final class CatalogoDeMisionesDesdeSemilla implements CatalogoDeMisiones {

    public static final String DEL_DOCUMENTO = "semilla/misiones-del-documento.json";
    /** D-42: las misiones de historia de nivel 1 a 7 que llevan hasta el Templo. */
    public static final String DE_PROGRESION = "semilla/misiones-de-progresion.json";
    public static final String DEL_EQUIPO = "semilla/misiones-del-equipo.json";
    public static final String PROVISIONAL_DE_DEV = "semilla/misiones-provisionales-dev.json";
    public static final String PREFIJO_PROVISIONAL = "[PROVISIONAL DE DEV]";

    /** Dos probabilidades que difieren menos que esto son la misma (error de coma flotante). */
    private static final double TOLERANCIA = 1e-9;

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
        SemillaDeMisiones equipo = leer(DEL_EQUIPO);
        SemillaDeMisiones provisional = conProvisional ? leer(PROVISIONAL_DE_DEV) : null;
        SemillaDeMisiones extra = semillaExtra == null ? null : leer(semillaExtra);
        return desde(documento, progresion, equipo, provisional, extra);
    }

    /** El catalogo sin las semillas de progresion ni del equipo, como era antes de D-42 y de HU-MIS-012. */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones provisional) {
        return desde(documento, null, null, provisional);
    }

    /** El catalogo con la progresion pero sin las misiones del equipo, como era antes de HU-MIS-012. */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones progresion,
                                                SemillaDeMisiones provisional) {
        return desde(documento, progresion, null, provisional, null);
    }

    /** El catalogo sin la semilla extra del banco E2E. */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones progresion,
                                                SemillaDeMisiones equipo, SemillaDeMisiones provisional) {
        return desde(documento, progresion, equipo, provisional, null);
    }

    /**
     * Arma el catalogo comprobando lo que el tipo no puede comprobar: ids
     * unicos, requisitos que existen, una fila de la Tabla 20 por tipo, la
     * probabilidad de Master de cada mision contra su dificultad, que la de
     * progresion sea una cadena de historia con nivel recomendado, lo que el
     * documento exige a las misiones del equipo, las marcas de la semilla
     * provisional y de la extra, y la epica exclusiva de cada Master.
     *
     * @param progresion  nula si no se carga
     * @param equipo      nula si no se carga
     * @param provisional nula si no se carga
     * @param extra       nula si no se carga; sigue las reglas de la provisional
     */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones progresion,
                                                SemillaDeMisiones equipo, SemillaDeMisiones provisional,
                                                SemillaDeMisiones extra) {
        Map<Dificultad, Double> masterPorDificultad = exigirMasterPorDificultad(documento);
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
            exigirSinTabla(progresion, "de progresion");
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
        if (equipo != null) {
            exigirSinTabla(equipo, "del equipo");
            for (Mision mision : equipo.misiones()) {
                if (mision.origen() != Origen.EQUIPO) {
                    throw new IllegalStateException("La semilla del equipo solo publica misiones del equipo: «"
                            + mision.id() + "» dice " + mision.origen() + ".");
                }
                exigirLoQuePideElDocumento(mision, documento.tabla20());
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
        for (Mision mision : todas) {
            // Las misiones tecnicas (provisional y extra, siempre PROVISIONAL_DEV) no son contenido del juego: la del
            // banco E2E necesita un Master que siempre aparezca (100 %), y la regla es sobre lo que se juega.
            if (mision.origen() != Origen.PROVISIONAL_DEV) {
                exigirMasterConSuDificultad(mision, masterPorDificultad);
            }
            exigirEpicaDeLaTabla20(mision, documento.tabla20());
        }
        exigirEpicasExclusivas(todas);
        return new CatalogoDeMisionesDesdeSemilla(todas, documento.tabla20());
    }

    /** Las misiones de una semilla que no es del juego: marcadas, sin Tabla 20 ni tabla de Master. */
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
        exigirSinTabla(semilla, cual);
    }

    /**
     * La tabla de la semilla del documento: las cuatro dificultades, cada una
     * con una probabilidad mayor que cero y hasta uno. Va como PROPORCION
     * (0,15 = 15 %), la misma unidad que la {@code probabilidad} de cada
     * Master que se compara con ella; la Tabla 20, en cambio, va en porcentaje
     * ({@code probabilidadPorcentaje}: 4 = 4 %) y es otra cosa: la de que un
     * Master afin suelte su epica.
     */
    private static Map<Dificultad, Double> exigirMasterPorDificultad(SemillaDeMisiones documento) {
        Map<Dificultad, Double> tabla = documento.probabilidadDeMasterPorDificultad();
        for (Dificultad dificultad : Dificultad.values()) {
            Double probabilidad = tabla.get(dificultad);
            if (probabilidad == null) {
                throw new IllegalStateException("La semilla del documento no declara la probabilidad de Máster de la "
                        + "dificultad " + dificultad + ": sin ella no se puede comprobar ninguna mision.");
            }
            if (!(probabilidad > 0 && probabilidad <= 1)) {
                throw new IllegalStateException("La probabilidad de Máster de la dificultad " + dificultad + " ("
                        + probabilidad + ") debe ir de 0 (sin incluir) a 1.");
            }
        }
        return tabla;
    }

    /** Solo la semilla del documento declara la tabla: dos tablas no sabrian cual manda. */
    private static void exigirSinTabla(SemillaDeMisiones semilla, String cual) {
        if (!semilla.probabilidadDeMasterPorDificultad().isEmpty()) {
            throw new IllegalStateException("La semilla " + cual + " no trae su propia tabla de probabilidad de "
                    + "Máster por dificultad: es del documento y vale para todas las misiones.");
        }
    }

    /** RF-MIS-59: el Master aparece con la probabilidad que corresponde a la dificultad de su mision. */
    private static void exigirMasterConSuDificultad(Mision mision, Map<Dificultad, Double> masterPorDificultad) {
        double esperada = masterPorDificultad.get(mision.dificultad());
        for (MasterDeMision master : mision.masters()) {
            if (Math.abs(master.probabilidad() - esperada) > TOLERANCIA) {
                throw new IllegalStateException("La mision «" + mision.id() + "» es de dificultad "
                        + mision.dificultad() + " y su Máster «" + master.nombre() + "» aparece con "
                        + porcentaje(master.probabilidad()) + " %, pero esa dificultad lleva "
                        + porcentaje(esperada) + " %: la probabilidad del Máster debe corresponder a la "
                        + "dificultad declarada.");
            }
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

    /**
     * RG-108 y RF-MIS-57 a 59: una mision del equipo tiene enemigos regulares,
     * jefe final y al menos un Master con su epica, que sigue el formato de las
     * de la Tabla 20 (efecto general y efecto potenciado para su tipo).
     */
    private static void exigirLoQuePideElDocumento(Mision mision, List<EpicaDeTabla20> tabla20) {
        if (mision.enemigos().isEmpty()) {
            throw new IllegalStateException("La mision del equipo «" + mision.id()
                    + "» necesita enemigos regulares (RF-MIS-57).");
        }
        if (mision.jefe() == null) {
            throw new IllegalStateException("La mision del equipo «" + mision.id()
                    + "» necesita un jefe final (RF-MIS-57).");
        }
        if (mision.masters().isEmpty()) {
            throw new IllegalStateException("La mision del equipo «" + mision.id()
                    + "» necesita al menos un Máster con su habilidad epica (RF-MIS-58).");
        }
        for (MasterDeMision master : mision.masters()) {
            Epica epica = master.epica();
            Optional<EpicaDeTabla20> fila = tabla20.stream()
                    .filter(f -> f.prototipo().equals(master.prototipo())).findFirst();
            if (vacio(epica.efectoPotenciado())) {
                throw new IllegalStateException("La epica «" + epica.nombre() + "» del Máster «" + master.nombre()
                        + "» (" + mision.id() + ") no dice su efecto potenciado para " + master.prototipo() + ".");
            }
            boolean laTablaNoLoTiene = fila.isPresent() && vacio(fila.get().epica().efectoGeneral());
            if (vacio(epica.efectoGeneral()) && !laTablaNoLoTiene) {
                throw new IllegalStateException("La epica «" + epica.nombre() + "» del Máster «" + master.nombre()
                        + "» (" + mision.id() + ") no dice su efecto general.");
            }
        }
    }

    /**
     * Una epica con {@code productoId} se entrega al inventario, asi que no
     * puede ser un producto que no es. Hay dos clases de epica de Master:
     * <ul>
     *   <li>la de la Tabla 20 (por su producto o por su nombre): tiene que ser
     *       el producto de la fila del tipo del Master, o se entregaria el de
     *       otro tipo, o uno que no existe;</li>
     *   <li>la propia de una mision, que no esta en la tabla (como «Velo de
     *       Sombras», la del Master del Templo, 7.8.14): trae su producto del
     *       catalogo y su exclusividad la comprueba
     *       {@link #exigirEpicasExclusivas}.</li>
     * </ul>
     */
    private static void exigirEpicaDeLaTabla20(Mision mision, List<EpicaDeTabla20> tabla20) {
        for (MasterDeMision master : mision.masters()) {
            if (!master.epica().entregable()) {
                continue;
            }
            String producto = master.epica().productoId();
            boolean productoDeLaTabla = tabla20.stream().anyMatch(f -> producto.equals(f.epica().productoId()));
            boolean nombreDeLaTabla = tabla20.stream()
                    .anyMatch(f -> clave(f.epica().nombre()).equals(clave(master.epica().nombre())));
            if (!productoDeLaTabla && !nombreDeLaTabla) {
                continue;
            }
            boolean coincide = tabla20.stream()
                    .filter(f -> f.prototipo().equals(master.prototipo()))
                    .anyMatch(f -> producto.equals(f.epica().productoId()));
            if (!coincide) {
                throw new IllegalStateException("La epica «" + master.epica().nombre() + "» del Máster «"
                        + master.nombre() + "» (" + mision.id() + ") trae un producto que no es el de "
                        + master.prototipo() + " en la Tabla 20.");
            }
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** 0,15 -> «15»; 0,125 -> «12.5». */
    private static String porcentaje(double proporcion) {
        return BigDecimal.valueOf(proporcion).movePointRight(2).stripTrailingZeros().toPlainString();
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

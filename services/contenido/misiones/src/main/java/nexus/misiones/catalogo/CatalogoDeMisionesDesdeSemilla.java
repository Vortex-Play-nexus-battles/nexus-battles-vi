package nexus.misiones.catalogo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <h2>Tres semillas, y la tercera marcada</h2>
 *
 * <ul>
 *   <li>{@value #DEL_DOCUMENTO}: lo que dice el documento (7.8.14 y Tabla 20) y
 *       la configuracion que comparten todas las misiones (la probabilidad de
 *       Master por dificultad), siempre.</li>
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
 * </ul>
 *
 * <p>La lectura es estricta: un campo que no existe (una errata en la semilla)
 * tumba el arranque en vez de publicarse a medias. Y lo mismo una mision cuyo
 * Master no aparece con la probabilidad de su dificultad (RF-MIS-59).
 */
public final class CatalogoDeMisionesDesdeSemilla implements CatalogoDeMisiones {

    public static final String DEL_DOCUMENTO = "semilla/misiones-del-documento.json";
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
        SemillaDeMisiones documento = leer(DEL_DOCUMENTO);
        SemillaDeMisiones equipo = leer(DEL_EQUIPO);
        SemillaDeMisiones provisional = conProvisional ? leer(PROVISIONAL_DE_DEV) : null;
        return desde(documento, equipo, provisional);
    }

    /** El catalogo sin semilla del equipo, como era antes de HU-MIS-012. */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones provisional) {
        return desde(documento, null, provisional);
    }

    /**
     * Arma el catalogo comprobando lo que el tipo no puede comprobar: ids
     * unicos, requisitos que existen, una fila de la Tabla 20 por tipo, la
     * probabilidad de Master de cada mision contra su dificultad, lo que el
     * documento exige a las misiones del equipo y las marcas de la semilla
     * provisional.
     *
     * @param equipo      nula si no se carga
     * @param provisional nula si no se carga
     */
    static CatalogoDeMisionesDesdeSemilla desde(SemillaDeMisiones documento, SemillaDeMisiones equipo,
                                                SemillaDeMisiones provisional) {
        Map<Dificultad, Double> masterPorDificultad = exigirMasterPorDificultad(documento);
        List<Mision> todas = new ArrayList<>();
        for (Mision mision : documento.misiones()) {
            if (mision.origen() != Origen.DOCUMENTO) {
                throw new IllegalStateException("La semilla del documento solo publica misiones del documento: «"
                        + mision.id() + "» dice " + mision.origen() + ".");
            }
            todas.add(mision);
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
            exigirSinTabla(provisional, "provisional");
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
        for (Mision mision : todas) {
            exigirMasterConSuDificultad(mision, masterPorDificultad);
            exigirEpicaDeLaTabla20(mision, documento.tabla20());
        }
        return new CatalogoDeMisionesDesdeSemilla(todas, documento.tabla20());
    }

    /**
     * La tabla de la semilla del documento: las cuatro dificultades, cada una
     * con una probabilidad mayor que cero y hasta uno.
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
     * Una epica con {@code productoId} se entrega al inventario: tiene que ser
     * el producto de la fila de la Tabla 20 del tipo del Master, o se entregaria
     * un producto que no es.
     */
    private static void exigirEpicaDeLaTabla20(Mision mision, List<EpicaDeTabla20> tabla20) {
        for (MasterDeMision master : mision.masters()) {
            if (!master.epica().entregable()) {
                continue;
            }
            boolean coincide = tabla20.stream()
                    .filter(f -> f.prototipo().equals(master.prototipo()))
                    .anyMatch(f -> master.epica().productoId().equals(f.epica().productoId()));
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

package nexus.semilla;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import nexus.api.SolicitudCrearProducto;
import nexus.dominio.OrigenProducto;
import nexus.dominio.Producto;
import nexus.persistencia.ProductoRepository;
import nexus.semilla.CatalogoInicial.EntradaCatalogo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Siembra el catalogo inicial (heroes, armas, armaduras, items y epicas de las
 * reglas del curso) al arrancar, si {@code catalogo.semilla.habilitada} es true
 * — que desde B4 es el valor por omision: una base nueva produce SIEMPRE el
 * mismo catalogo, sin curl, POST ni SQL a mano.
 *
 * <h2>Semilla versionada (B4)</h2>
 *
 * El archivo declara la {@code version} de su contenido y cada documento
 * sembrado guarda {@code origen=SEMILLA} y la {@code semillaVersion} que lo
 * escribio. Al arrancar, por cada entrada:
 * <ul>
 *   <li><b>Falta</b>: se inserta ({@code insert}, nunca {@code save}: aun en
 *       carrera con otra instancia, un documento existente no se reemplaza; la
 *       base responde clave duplicada y cuenta como existente).</li>
 *   <li><b>Existe con una version menor y nadie lo edito</b>: se pone al dia
 *       con el contenido nuevo, conservando su estado, su fecha de alta, su
 *       promocion y sus reservas. El reemplazo es condicional a que la version
 *       del documento no haya cambiado desde que se leyo: si un administrador
 *       escribio entre medias, gana el administrador.</li>
 *   <li><b>Existe con una version menor y un administrador lo edito</b>
 *       ({@code modificadoPor}): se respeta y se deja un aviso en la bitacora
 *       en cada arranque, mientras la diferencia exista.</li>
 *   <li><b>Existe al dia, o no es de la semilla</b>: no se toca.</li>
 * </ul>
 * Los documentos sembrados antes de B4 no tienen marcas: se reconocen por su
 * identificador (derivado del slug) y cuentan como editados si su version es
 * mayor que 1, porque la semilla antigua siempre insertaba la 1 y solo la
 * edicion la subia.
 *
 * <p>Cada entrada pasa por las validaciones del alta. La que no las cumple se
 * rechaza y queda en la bitacora; el servicio arranca igual.
 */
@Component
public class SemillaDelCatalogo implements ApplicationRunner {

    private static final Logger BITACORA = LoggerFactory.getLogger(SemillaDelCatalogo.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProductoRepository repositorio;
    private final MapeadorDelCatalogo mapeador;
    private final Validator validador;
    private final boolean habilitada;
    private final Resource archivo;

    public SemillaDelCatalogo(
            ProductoRepository repositorio,
            MapeadorDelCatalogo mapeador,
            Validator validador,
            @Value("${catalogo.semilla.habilitada:true}") boolean habilitada,
            @Value("classpath:semilla/catalogo-inicial.json") Resource archivo) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
        this.validador = validador;
        this.habilitada = habilitada;
        this.archivo = archivo;
    }

    /** Lee el catalogo inicial (UTF-8). */
    public static CatalogoInicial leer(InputStream json) {
        return JSON.readValue(json, CatalogoInicial.class);
    }

    /**
     * Punto de entrada del arranque. Si la semilla falla (Mongo caido, JSON
     * ilegible), el servicio debe arrancar igual, asi que aqui se registra y no
     * se propaga. {@link #sembrar()} si propaga.
     */
    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            sembrar();
        } catch (RuntimeException e) {
            BITACORA.error("Semilla del catalogo fallida: el servicio arranca sin sembrar", e);
        }
    }

    /**
     * Siembra lo que falte, pone al dia lo que quedo atras y devuelve el resumen.
     *
     * @throws RuntimeException si falla la lectura del JSON o el acceso a la
     *         base (salvo clave duplicada, que cuenta como existente)
     */
    public ResultadoSemilla sembrar() {
        if (!habilitada) {
            return ResultadoSemilla.deshabilitada();
        }

        CatalogoInicial catalogo = leerArchivo();
        int version = catalogo.versionDelContenido();
        Instant ahora = Instant.now();
        List<String> insertados = new ArrayList<>();
        List<String> actualizados = new ArrayList<>();
        List<String> existentes = new ArrayList<>();
        List<String> respetados = new ArrayList<>();
        List<String> rechazados = new ArrayList<>();

        for (EntradaCatalogo entrada : catalogo.todas()) {
            String id = MapeadorDelCatalogo.identificador(entrada.id());
            Optional<SolicitudCrearProducto> solicitud = solicitudValida(entrada, catalogo, rechazados);
            if (solicitud.isEmpty()) {
                continue;
            }

            Optional<Producto> guardado = repositorio.findById(id);
            if (guardado.isEmpty()) {
                try {
                    repositorio.insert(mapeador.aProducto(id, solicitud.get(), ahora, version));
                    insertados.add(id);
                } catch (DuplicateKeyException e) {
                    existentes.add(id);
                }
                continue;
            }

            Producto actual = guardado.get();
            if (!esDeLaSemilla(actual) || versionSembrada(actual) >= version) {
                existentes.add(id);
            } else if (editadoPorAdministrador(actual)) {
                respetados.add(id);
            } else if (repositorio.reemplazarSemillaSiNoCambio(
                    mapeador.ponerAlDia(actual, solicitud.get(), ahora, version), actual.version())) {
                actualizados.add(id);
            } else {
                // Alguien escribio el producto entre la lectura y el reemplazo:
                // no se pisa; el siguiente arranque lo vuelve a mirar.
                existentes.add(id);
            }
        }

        BITACORA.info("Semilla del catalogo v{}: {} insertados, {} actualizados, {} al dia, {} respetados, {} rechazados",
                version, insertados.size(), actualizados.size(), existentes.size(),
                respetados.size(), rechazados.size());
        respetados.forEach(id -> BITACORA.warn(
                "Semilla del catalogo v{}: el producto {} lo edito un administrador; se respeta y no se pone al dia",
                version, id));
        rechazados.forEach(motivo -> BITACORA.warn("Semilla del catalogo, rechazado: {}", motivo));
        return new ResultadoSemilla(true, version, insertados, actualizados, existentes, respetados, rechazados);
    }

    /** La solicitud de alta de la entrada, o vacio (y anotado el motivo) si no la cumple. */
    private Optional<SolicitudCrearProducto> solicitudValida(
            EntradaCatalogo entrada, CatalogoInicial catalogo, List<String> rechazados) {
        SolicitudCrearProducto solicitud;
        try {
            solicitud = mapeador.aSolicitud(entrada, catalogo);
        } catch (IllegalArgumentException e) {
            rechazados.add(entrada.id() + ": " + e.getMessage());
            return Optional.empty();
        }
        Set<ConstraintViolation<SolicitudCrearProducto>> violaciones = validador.validate(solicitud);
        if (!violaciones.isEmpty()) {
            rechazados.add(entrada.id() + ": " + violaciones.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; ")));
            return Optional.empty();
        }
        return Optional.of(solicitud);
    }

    /** Sembrado (con marca) o anterior a las marcas; lo que dio de alta un administrador, no. */
    private static boolean esDeLaSemilla(Producto producto) {
        return producto.origen() != OrigenProducto.ADMINISTRACION;
    }

    /** La version de la semilla que escribio el producto; 0 si es anterior a B4. */
    private static int versionSembrada(Producto producto) {
        return producto.semillaVersion() == null ? 0 : producto.semillaVersion();
    }

    private static boolean editadoPorAdministrador(Producto producto) {
        boolean anteriorALasMarcas = producto.origen() == null && producto.semillaVersion() == null;
        return producto.editadoPorAdministrador() || (anteriorALasMarcas && producto.version() > 1);
    }

    private CatalogoInicial leerArchivo() {
        try (InputStream json = archivo.getInputStream()) {
            return leer(json);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catalogo inicial", e);
        }
    }
}

package nexus.recursos;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.persistencia.ProductoRepository;
import nexus.recursos.RecursoGraficoVista.UsoDeRecurso;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * La biblioteca de recursos gráficos del catálogo — HU-PRD-009, §7.2.3
 * «gestionar recursos gráficos (imágenes, iconos, animaciones)».
 *
 * <ul>
 *   <li><b>Subir</b>: se examina el archivo ({@link ExaminadorDeRecursos}) y
 *       se guarda con quién lo subió y, si se subió desde la ficha de un
 *       producto, asociado a él. El mismo archivo, de la misma clase y para el
 *       mismo producto, no se guarda dos veces: se devuelve el que había.</li>
 *   <li><b>Usar</b>: un recurso se pone como imagen de un producto con la
 *       modificación de siempre ({@code PATCH /productos/{id}} con su
 *       {@code url}), que deja respaldo, historial y versión de diseño. Por eso
 *       aquí no se toca ningún producto.</li>
 *   <li><b>Retirar</b>: sale de la biblioteca, nunca de la base (ver
 *       {@link RecursoGrafico}); no se puede retirar el que es la imagen actual
 *       de un producto.</li>
 * </ul>
 */
@Service
public class GestionarRecursosServicio {

    /** RNF-USA-001: dieciséis por página. */
    public static final int POR_PAGINA = 16;

    /** Lo que cabe en el motivo de un retiro, como el de la bitácora central. */
    public static final int MOTIVO_MAXIMO = 500;

    /** El nombre que se guarda del archivo, recortado. */
    static final int NOMBRE_MAXIMO = 80;

    /** Una subida: el recurso y si es nuevo o el mismo archivo que ya estaba. */
    public record Subida(RecursoGrafico recurso, boolean nuevo) {
    }

    private final RecursoGraficoRepository recursos;
    private final MongoTemplate mongo;
    private final ProductoRepository productos;
    private final ExaminadorDeRecursos examinador;
    private final Clock reloj;

    public GestionarRecursosServicio(
            RecursoGraficoRepository recursos,
            MongoTemplate mongo,
            ProductoRepository productos,
            ExaminadorDeRecursos examinador,
            Clock reloj) {
        this.recursos = recursos;
        this.mongo = mongo;
        this.productos = productos;
        this.examinador = examinador;
        this.reloj = reloj;
    }

    /**
     * @throws RecursoNoAdmitidoException  si el archivo no cumple su clase
     * @throws ProductoNoEncontradoException si se asocia a un producto que no existe
     */
    public Subida subir(byte[] bytes, ClaseDeRecurso clase, String productoId, String nombreOriginal, String autor) {
        String producto = productoId == null || productoId.isBlank() ? null : productoId.trim();
        if (producto != null && !productos.existsById(producto)) {
            throw new ProductoNoEncontradoException();
        }
        ExaminadorDeRecursos.RecursoExaminado examinado = examinador.examinar(bytes, clase);
        String huella = huellaDe(bytes);
        var existente = recursos.findFirstByHuellaAndClaseAndProductoIdAndRetiradoEnIsNull(huella, clase, producto);
        if (existente.isPresent()) {
            return new Subida(existente.get(), false);
        }
        RecursoGrafico nuevo = new RecursoGrafico(
                UUID.randomUUID().toString(),
                clase,
                examinado.formato(),
                bytes.length,
                examinado.ancho(),
                examinado.alto(),
                huella,
                bytes,
                producto,
                nombreSaneado(nombreOriginal, examinado.formato()),
                autor,
                Instant.now(reloj),
                null,
                null,
                null);
        return new Subida(recursos.insert(nuevo), true);
    }

    /**
     * Una página de la biblioteca, de lo más reciente a lo más antiguo, sin los
     * bytes. Con {@code productoId}, solo los asociados a ese producto.
     */
    public PaginaDeRecursos listar(ClaseDeRecurso clase, String productoId, boolean incluirRetirados,
                                   int pagina, int tamano) {
        Criteria criterio = new Criteria();
        List<Criteria> condiciones = new ArrayList<>();
        if (!incluirRetirados) {
            condiciones.add(Criteria.where("retiradoEn").is(null));
        }
        if (clase != null) {
            condiciones.add(Criteria.where("clase").is(clase));
        }
        if (productoId != null && !productoId.isBlank()) {
            condiciones.add(Criteria.where("productoId").is(productoId.trim()));
        }
        if (!condiciones.isEmpty()) {
            criterio = criterio.andOperator(condiciones.toArray(Criteria[]::new));
        }
        long total = mongo.count(new Query(criterio), RecursoGrafico.class);
        Query consulta = new Query(criterio)
                .with(Sort.by(Sort.Order.desc("subidoEn"), Sort.Order.desc("_id")))
                .skip((long) pagina * tamano)
                .limit(tamano);
        consulta.fields().exclude("contenido");
        List<RecursoGrafico> encontrados = mongo.find(consulta, RecursoGrafico.class);
        Map<String, List<UsoDeRecurso>> usos = usosDe(encontrados);
        List<RecursoGraficoVista> vistas = encontrados.stream()
                .map(r -> RecursoGraficoVista.de(r, usos.getOrDefault(r.url(), List.of())))
                .toList();
        int paginas = tamano == 0 ? 0 : (int) Math.ceil((double) total / tamano);
        return new PaginaDeRecursos(vistas, pagina, tamano, total, paginas);
    }

    /** El recurso con sus bytes, retirado o no (ver {@link RecursoGrafico}). */
    public RecursoGrafico obtener(String id) {
        return recursos.findById(id).orElseThrow(RecursoNoEncontradoException::new);
    }

    /**
     * Saca el recurso de la biblioteca. Retirar uno ya retirado no cambia nada.
     *
     * @throws RecursoEnUsoException si es la imagen actual de un producto
     * @throws MotivoInvalidoException si el motivo pasa de 500 caracteres
     */
    public RecursoGrafico retirar(String id, String autor, String motivo) {
        RecursoGrafico recurso = obtener(id);
        if (recurso.retirado()) {
            return recurso;
        }
        String limpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (limpio != null && limpio.length() > MOTIVO_MAXIMO) {
            throw new MotivoInvalidoException("El motivo admite como máximo " + MOTIVO_MAXIMO + " caracteres.");
        }
        productos.findFirstByImagen(recurso.url()).ifPresent(producto -> {
            throw new RecursoEnUsoException(producto.nombre());
        });
        return recursos.save(recurso.retirar(autor, Instant.now(reloj), limpio));
    }

    /** La vista de un recurso con sus usos actuales. */
    public RecursoGraficoVista vista(RecursoGrafico recurso) {
        return RecursoGraficoVista.de(recurso, usosDe(List.of(recurso)).getOrDefault(recurso.url(), List.of()));
    }

    private Map<String, List<UsoDeRecurso>> usosDe(List<RecursoGrafico> encontrados) {
        if (encontrados.isEmpty()) {
            return Map.of();
        }
        List<String> urls = encontrados.stream().map(RecursoGrafico::url).toList();
        Map<String, List<UsoDeRecurso>> usos = new LinkedHashMap<>();
        for (Producto producto : productos.findByImagenIn(urls)) {
            usos.computeIfAbsent(producto.imagen(), url -> new ArrayList<>())
                    .add(new UsoDeRecurso(producto.id(), producto.nombre()));
        }
        return usos;
    }

    static String huellaDe(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("la JVM no trae SHA-256", imposible);
        }
    }

    /**
     * El nombre del archivo, para reconocerlo en la biblioteca: sin ruta, sin
     * caracteres de control, con los espacios plegados y recortado. Si no queda
     * nada, «recurso» con la extensión de su formato.
     */
    static String nombreSaneado(String original, FormatoDeRecurso formato) {
        String nombre = Objects.requireNonNullElse(original, "");
        int barra = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
        nombre = nombre.substring(barra + 1)
                .replaceAll("[\\p{Cntrl}\\p{Cf}<>\"]", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (nombre.isEmpty()) {
            nombre = "recurso." + formato.name().toLowerCase(Locale.ROOT);
        }
        if (nombre.codePointCount(0, nombre.length()) > NOMBRE_MAXIMO) {
            nombre = nombre.substring(0, nombre.offsetByCodePoints(0, NOMBRE_MAXIMO)).strip();
        }
        return nombre;
    }
}

package nexus.recursos;

import java.io.IOException;
import java.net.URI;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recursos gráficos del catálogo — HU-PRD-009, productos.yaml 1.10.0.
 *
 * <ul>
 *   <li>{@code POST /api/v1/productos/recursos} (multipart: {@code archivo},
 *       {@code clase}, {@code productoId} opcional): administración. 201 si es
 *       nuevo, 200 si es el mismo archivo que ya estaba.</li>
 *   <li>{@code GET /api/v1/productos/recursos}: la biblioteca, 16 por página,
 *       administración.</li>
 *   <li>{@code GET /api/v1/productos/recursos/{id}}: los bytes, públicos —es
 *       la imagen que la tienda enseña—, con el tipo detectado, {@code nosniff},
 *       una política que no deja ejecutar nada y caché larga (el identificador
 *       no cambia nunca).</li>
 *   <li>{@code DELETE /api/v1/productos/recursos/{id}?motivo=}: retirar,
 *       administración.</li>
 * </ul>
 *
 * <p>Del multipart solo se toman los bytes y el nombre (para reconocerlo en la
 * biblioteca); el {@code Content-Type} que declara el navegador no llega al
 * servicio: el formato lo dice la firma de bytes.
 */
@RestController
@RequestMapping("/api/v1/productos/recursos")
public class RecursosController {

    private final GestionarRecursosServicio servicio;

    public RecursosController(GestionarRecursosServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecursoGraficoVista> subir(
            Authentication autenticacion,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo,
            @RequestParam(name = "clase", required = false) String clase,
            @RequestParam(name = "productoId", required = false) String productoId) throws IOException {

        GestionarRecursosServicio.Subida subida = servicio.subir(
                archivo == null ? null : archivo.getBytes(),
                claseDe(clase),
                productoId,
                archivo == null ? null : archivo.getOriginalFilename(),
                autenticacion.getName());
        RecursoGraficoVista vista = servicio.vista(subida.recurso());
        return subida.nuevo()
                ? ResponseEntity.created(URI.create(vista.url())).body(vista)
                : ResponseEntity.ok(vista);
    }

    @GetMapping
    public PaginaDeRecursos listar(
            @RequestParam(name = "clase", required = false) ClaseDeRecurso clase,
            @RequestParam(name = "productoId", required = false) String productoId,
            @RequestParam(name = "incluirRetirados", defaultValue = "false") boolean incluirRetirados,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "page debe ser 0 o mayor") int pagina,
            @RequestParam(name = "size", defaultValue = "16")
            @Min(value = 1, message = "size debe estar entre 1 y 50")
            @Max(value = 50, message = "size debe estar entre 1 y 50") int tamano) {
        return servicio.listar(clase, productoId, incluirRetirados, pagina, tamano);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> contenido(
            @PathVariable String id,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String siNoCoincide) {
        RecursoGrafico recurso = servicio.obtener(id);
        String etiqueta = "\"" + recurso.huella() + "\"";
        if (etiqueta.equals(siNoCoincide)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etiqueta).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(recurso.formato().tipoMime()))
                .contentLength(recurso.contenido().length)
                .eTag(etiqueta)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(recurso.contenido());
    }

    @DeleteMapping("/{id}")
    public RecursoGraficoVista retirar(
            Authentication autenticacion,
            @PathVariable String id,
            @RequestParam(name = "motivo", required = false) String motivo) {
        return servicio.vista(servicio.retirar(id, autenticacion.getName(), motivo));
    }

    /** La clase del multipart, o un 400 que dice cuáles hay. */
    static ClaseDeRecurso claseDe(String clase) {
        if (clase == null || clase.isBlank()) {
            return null;
        }
        try {
            return ClaseDeRecurso.valueOf(clase.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException desconocida) {
            throw new RecursoNoAdmitidoException(RecursoNoAdmitidoException.Motivo.CLASE_INVALIDA,
                    "La clase tiene que ser IMAGEN, ICONO o ANIMACION.");
        }
    }
}

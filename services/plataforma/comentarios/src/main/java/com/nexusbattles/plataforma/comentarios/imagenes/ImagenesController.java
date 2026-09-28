package com.nexusbattles.plataforma.comentarios.imagenes;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;

/**
 * Subir y servir las imagenes de los comentarios — contrato 1.4.0
 * ({@code /comentarios/imagenes}), 7.1 del documento del curso, B3.
 *
 * <p>Cuelga de {@code /api/v1/comentarios}, el prefijo propio de este servicio
 * que el borde ya le manda (ver el javadoc de {@code ModeracionController});
 * el borde le fija ademas un limite de cuerpo propio, mas estrecho que el
 * general.
 *
 * <h2>Subida</h2>
 *
 * <p>Del multipart solo se toman los bytes del campo {@code archivo}. Ni el
 * nombre del archivo ni el {@code Content-Type} que declara el navegador
 * llegan al servicio: el tipo lo dice la firma de bytes
 * ({@link ExaminadorDeImagenes}). Spring corta antes los cuerpos de mas de
 * 2 MB ({@code spring.servlet.multipart}), y el examinador lo vuelve a mirar.
 *
 * <h2>Descarga</h2>
 *
 * <p>Se sirve con las cabeceras que hacen inofensivo lo que se haya colado:
 * el tipo detectado (nunca el declarado), {@code X-Content-Type-Options:
 * nosniff} para que el navegador no adivine otro, y {@code
 * Content-Security-Policy: default-src 'none'} para que, aunque alguien abra
 * la URL directamente, ahi no se ejecute nada. La cache larga e inmutable solo
 * para las publicas: el id no cambia nunca, pero una privada (de un
 * comentario en revision) no tiene que quedarse en ninguna cache compartida.
 */
@RestController
@RequestMapping("/api/v1/comentarios/imagenes")
public class ImagenesController {

    /** Contrato 1.4.0: cache de larga duracion, «el id es inmutable». */
    static final String CACHE_PUBLICA = "public, max-age=31536000, immutable";

    /** Las que no son publicas no se guardan en ninguna cache. */
    static final String CACHE_PRIVADA = "private, no-store";

    private final ServicioDeImagenes servicio;

    public ImagenesController(ServicioDeImagenes servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImagenSubidaResponse> subir(
            @RequestPart(name = "archivo", required = false) MultipartFile archivo,
            @AuthenticationPrincipal Jwt autor) throws IOException {

        ServicioDeImagenes.ImagenGuardada guardada = servicio.subir(
                IdentidadDelToken.idDe(autor).toString(),
                archivo == null ? null : archivo.getBytes());

        return ResponseEntity.created(URI.create(guardada.url()))
                .body(new ImagenSubidaResponse(
                        guardada.id(), guardada.tipo().tipoMime(), guardada.tamano(), guardada.url()));
    }

    @GetMapping("/{imagenId}")
    public ResponseEntity<byte[]> obtener(@PathVariable String imagenId, Authentication autenticacion) {
        ServicioDeImagenes.ImagenServida imagen = servicio.obtener(imagenId, Solicitante.de(autenticacion));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.tipo().tipoMime()))
                .contentLength(imagen.datos().length)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .header(HttpHeaders.CACHE_CONTROL, imagen.publica() ? CACHE_PUBLICA : CACHE_PRIVADA)
                .body(imagen.datos());
    }

    /** {@code ImagenSubida} del contrato. */
    public record ImagenSubidaResponse(String id, String tipo, int tamano, String url) {
    }
}

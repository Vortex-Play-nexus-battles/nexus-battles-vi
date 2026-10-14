package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * ms-chatbot.yaml 1.14.0: como se sirve el contenido de una captura, al dueno
 * de la solicitud o a un administrador.
 *
 * <ul>
 *   <li>Con el tipo que se comprobo al subirla (PNG o JPEG) y
 *       {@code X-Content-Type-Options: nosniff}: el navegador no la
 *       interpreta como otra cosa.</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'; sandbox}: abierta
 *       sola en una pestana no puede ejecutar ni cargar nada.</li>
 *   <li>{@code Cache-Control: private, no-store}: es de una persona; ningun
 *       intermediario la guarda ni se la sirve a otra.</li>
 *   <li>{@code Content-Disposition: inline} con un nombre propio
 *       ({@code captura-<id>.png}), nunca el del archivo que se subio.</li>
 * </ul>
 */
public final class RespuestaDeCaptura {

    private RespuestaDeCaptura() {
        // Utilidad sin estado.
    }

    public static ResponseEntity<byte[]> de(CapturaDeSoporte captura) {
        String extension = ExaminadorDeCapturas.Formato.porTipoMime(captura.getTipo())
            .map(ExaminadorDeCapturas.Formato::extension).orElse("bin");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(captura.getTipo()))
            .contentLength(captura.getContenido().length)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .header("Cross-Origin-Resource-Policy", "same-origin")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename("captura-" + captura.getId() + "." + extension).build().toString())
            .body(captura.getContenido());
    }
}

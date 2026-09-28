package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.AvisoDeMensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Aviso en la bandeja del destinatario que no esta conectado — B6, contra
 * {@code POST /internal/notifications} de
 * {@code contracts/openapi/notificaciones.yaml} 1.1.0 (solo credencial de
 * servicio, que pone el {@link RestClient}).
 *
 * <p><b>El aviso no lleva el texto del mensaje.</b> Dice quien escribio, no que
 * escribio: la bandeja la guarda otro servicio y lo privado no tiene por que
 * quedar copiado alli. Para leerlo esta la vista de mensajes.
 *
 * <p><b>No bloquea el envio.</b> Va en otro hilo: quien escribe no espera a que
 * notificaciones conteste, y si no contesta, se pierde el aviso (queda en la
 * bitacora) y el mensaje sigue en el historial.
 *
 * <p><b>Uno por racha.</b> El {@code id} del aviso es el del primer no leido de
 * la racha, no el de cada mensaje: los siguientes de la misma racha repiten id
 * y notificaciones los descarta (409 = ya estaba, que tambien cubre un
 * reintento).
 */
public class ClienteNotificacionesDeMensajes implements AvisoDeMensajeDirecto {

    private static final Logger log = LoggerFactory.getLogger(ClienteNotificacionesDeMensajes.class);

    static final String RUTA = "/internal/notifications";
    static final String TIPO = "MENSAJE_PRIVADO";
    static final String TITULO = "Nuevo mensaje privado";

    private final RestClient http;
    private final String base;
    private final Executor ejecutor;

    /**
     * @param base     URL de notificaciones con su {@code /api/v1}; vacia = sin avisos
     * @param ejecutor donde se manda (en produccion, un hilo virtual por aviso)
     */
    public ClienteNotificacionesDeMensajes(RestClient http, String base, Executor ejecutor) {
        this.http = Objects.requireNonNull(http);
        this.base = base == null ? "" : base.strip().replaceAll("/+$", "");
        this.ejecutor = Objects.requireNonNull(ejecutor);
    }

    @Override
    public void avisar(MensajeDirecto mensaje, UUID primerNoLeido) {
        if (base.isEmpty()) {
            log.info("Sin NOTIFICACIONES_URL: el aviso del mensaje privado {} no sale", mensaje.id());
            return;
        }
        try {
            ejecutor.execute(() -> mandar(mensaje, primerNoLeido));
        } catch (RejectedExecutionException sinHilo) {
            log.warn("El aviso del mensaje privado {} no se pudo programar: {}", mensaje.id(), sinHilo.getMessage());
        }
    }

    private void mandar(MensajeDirecto mensaje, UUID primerNoLeido) {
        try {
            http.post()
                    .uri(base + RUTA)
                    .body(new Peticion(mensaje.destinatario().toString(), "mensaje-directo-" + primerNoLeido, TIPO,
                            TITULO, mensaje.apodoRemitente() + " te escribió un mensaje privado.",
                            mensaje.enviadoEn()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict yaEstaba) {
            // Un reintento: el aviso ya estaba en la bandeja.
        } catch (RestClientException ex) {
            log.warn("El aviso del mensaje privado {} no llego a notificaciones: {}",
                    mensaje.id(), ex.getClass().getSimpleName());
        }
    }

    /** {@code EmitirNotificacionRequest} del contrato. */
    record Peticion(String usuarioId, String id, String tipo, String titulo, String cuerpo, Instant creadaEn) {
    }
}

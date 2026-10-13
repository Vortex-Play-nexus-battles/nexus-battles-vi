package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.AvisoDeInvitacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Objects;

/**
 * La invitacion a una sala, como aviso en la bandeja del invitado —
 * salas-partidas.yaml 1.10.0 contra {@code POST /internal/notifications} de
 * {@code contracts/openapi/notificaciones.yaml} (credencial de servicio, que pone
 * el {@link RestClient}; no se toca su contrato).
 *
 * <p><b>El {@code id} del aviso lleva la sala</b> —{@code sala:{idSala}:invitacion:{idInvitado}}
 * y, en una sala privada, {@code :codigo:{CODIGO}}—, con la misma convencion que
 * los avisos del catalogo ({@code catalogo:{idDeAlerta}}): la bandeja lo usa
 * para ofrecer «Unirme» sin que notificaciones sepa nada de salas. Es estable
 * por sala e invitado, asi que la segunda invitacion a la misma persona es un
 * 409 de notificaciones («ya estaba») y no un segundo aviso.
 *
 * <p>A diferencia del aviso de un mensaje privado, este va en el mismo hilo: el
 * anfitrion espera a saber si la invitacion salio.
 */
public class ClienteNotificacionesDeInvitaciones implements AvisoDeInvitacion {

    private static final Logger log = LoggerFactory.getLogger(ClienteNotificacionesDeInvitaciones.class);

    static final String RUTA = "/internal/notifications";
    /** Tipo del aviso (maximo 40 caracteres en el contrato de notificaciones). */
    public static final String TIPO = "INVITACION_SALA";

    private final RestClient http;
    private final String base;

    /** @param base URL de notificaciones con su {@code /api/v1}; vacia = sin avisos (se dice, no se finge) */
    public ClienteNotificacionesDeInvitaciones(RestClient http, String base) {
        this.http = Objects.requireNonNull(http);
        this.base = base == null ? "" : base.strip().replaceAll("/+$", "");
    }

    @Override
    public boolean invitar(Invitacion invitacion) {
        if (base.isEmpty()) {
            throw new AvisoNoDisponible("Sin NOTIFICACIONES_URL la invitacion no tiene por donde salir.");
        }
        try {
            http.post()
                    .uri(base + RUTA)
                    .body(new Peticion(invitacion.idInvitado().toString(), idDelAviso(invitacion), TIPO,
                            titulo(invitacion), cuerpo(invitacion), invitacion.enviadaEn()))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Conflict yaEstaba) {
            return false;
        } catch (RestClientException fallo) {
            log.warn("La invitacion a la sala {} no llego a notificaciones: {}", invitacion.idSala(),
                    fallo.getClass().getSimpleName());
            throw new AvisoNoDisponible("Notificaciones no contesto.");
        }
    }

    /** {@code sala:{idSala}:invitacion:{idInvitado}[:codigo:{CODIGO}]}: estable por sala e invitado. */
    static String idDelAviso(Invitacion invitacion) {
        String id = "sala:" + invitacion.idSala() + ":invitacion:" + invitacion.idInvitado();
        return invitacion.codigo() == null ? id : id + ":codigo:" + invitacion.codigo();
    }

    static String titulo(Invitacion invitacion) {
        return invitacion.apodoAnfitrion() + " te invita a una batalla";
    }

    static String cuerpo(Invitacion invitacion) {
        StringBuilder texto = new StringBuilder("Sala ").append(nombreDe(invitacion.modalidad()));
        if (invitacion.privada()) {
            texto.append(" · privada");
        }
        if (invitacion.recompensa() > 0) {
            texto.append(" · ").append(invitacion.recompensa()).append(" créditos en juego");
        }
        texto.append('.');
        if (invitacion.codigo() != null) {
            texto.append(" Código de invitación: ").append(invitacion.codigo()).append('.');
        }
        return texto.toString();
    }

    private static String nombreDe(String modalidad) {
        return switch (modalidad == null ? "" : modalidad) {
            case "UNO_CONTRA_UNO" -> "1 contra 1";
            case "CONTRA_IA" -> "Solo contra la IA";
            case "HASTA_SEIS" -> "Hasta seis";
            default -> "de batalla";
        };
    }

    /** {@code EmitirNotificacionRequest} del contrato de notificaciones. */
    record Peticion(String usuarioId, String id, String tipo, String titulo, String cuerpo, Instant creadaEn) {
    }
}

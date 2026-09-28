package com.nexusbattles.plataforma.torneos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.torneos.torneo.AvisosAlJugador;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los canales con los que torneos avisa a un jugador, todos con la
 * credencial de servicio de torneos (ADR-005):
 *
 * <ul>
 *   <li>Bandeja: notificaciones.yaml, {@code POST /internal/notifications}.
 *       Idempotente por {@code id}: un 409 significa que el aviso ya estaba,
 *       y cuenta como entregado.</li>
 *   <li>Correo (opcional): el contacto sale de ms-identidad por {@code uid}
 *       ({@code GET /api/v1/internal/usuarios/{uid}/contacto},
 *       ms-identidad-admin.yaml) y el envio va a
 *       {@code POST /correos/torneo} (correo.yaml 1.5.0) con
 *       {@code Idempotency-Key}. Torneos no guarda correos de nadie (regla 7 y
 *       minimizacion de datos personales). Sin las dos URL configuradas no se
 *       crea ninguna operacion de correo.</li>
 * </ul>
 */
public class ClienteAvisos implements AvisosAlJugador {

    static final String TIPO = "TORNEO";

    private final RestClient http;
    private final String notificaciones;
    private final String identidad;
    private final String correo;

    public ClienteAvisos(RestClient http, String notificaciones, String identidad, String correo) {
        this.http = http;
        this.notificaciones = sinBarraFinal(notificaciones);
        this.identidad = sinBarraFinal(identidad);
        this.correo = sinBarraFinal(correo);
    }

    @Override
    public void notificar(UUID destinatario, String id, String titulo, String cuerpo, OffsetDateTime creadaEn) {
        try {
            http.post()
                    .uri(notificaciones + "/internal/notifications")
                    .body(new Aviso(destinatario.toString(), id, TIPO, titulo, cuerpo, creadaEn))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict yaEstaba) {
            // Mismo id ya en la bandeja: el aviso se entrego en un intento anterior.
        } catch (RestClientException fallo) {
            throw ClasificadorDeFallos.clasificar("avisar a " + destinatario, fallo, false);
        }
    }

    @Override
    public boolean correoConfigurado() {
        return !identidad.isBlank() && !correo.isBlank();
    }

    @Override
    public Correo enviarCorreo(UUID destinatario, UUID torneoId, String asunto, String mensaje, String claveIdempotente) {
        Contacto contacto;
        try {
            contacto = http.get()
                    .uri(identidad + "/api/v1/internal/usuarios/{uid}/contacto", destinatario)
                    .retrieve()
                    .body(Contacto.class);
        } catch (HttpClientErrorException.NotFound sinCuenta) {
            return Correo.SIN_CONTACTO;
        } catch (RestClientException fallo) {
            throw ClasificadorDeFallos.clasificar("pedir el contacto de " + destinatario, fallo, false);
        }
        if (contacto == null || contacto.email() == null || contacto.email().isBlank()) {
            return Correo.SIN_CONTACTO;
        }
        String apodo = contacto.apodo() == null || contacto.apodo().isBlank() ? "jugador" : contacto.apodo();
        try {
            http.post()
                    .uri(correo + "/correos/torneo")
                    .header("Idempotency-Key", claveIdempotente)
                    .body(new CorreoDeTorneo(contacto.email(), apodo, asunto, mensaje, torneoId.toString()))
                    .retrieve()
                    .toBodilessEntity();
            return Correo.ENVIADO;
        } catch (RestClientException fallo) {
            throw ClasificadorDeFallos.clasificar("enviar el correo del torneo a " + destinatario, fallo, false);
        }
    }

    private static String sinBarraFinal(String url) {
        return url == null ? "" : url.strip().replaceAll("/+$", "");
    }

    record Aviso(String usuarioId, String id, String tipo, String titulo, String cuerpo, OffsetDateTime creadaEn) { }

    record CorreoDeTorneo(String email, String apodo, String asunto, String mensaje, String torneoId) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contacto(String uid, String email, String apodo, String estado) { }
}

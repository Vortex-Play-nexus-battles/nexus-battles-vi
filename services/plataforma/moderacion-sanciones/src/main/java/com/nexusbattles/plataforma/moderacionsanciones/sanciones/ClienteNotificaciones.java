package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Objects;

/**
 * Adaptador de {@link EmisorDeAvisos} contra {@code POST /internal/notifications}
 * del modulo de notificaciones ({@code contracts/openapi/notificaciones.yaml}
 * 1.1.0). El {@link RestClient} llega con la credencial de servicio
 * (ADR-005): esa ruta es {@code ROLE_SERVICIO}.
 *
 * <p>201 y 409 son «entregado»: el 409 dice que el modulo ya tenia ese id
 * (un reintento anterior si llego). Cualquier otro 4xx es un rechazo; lo que
 * no responde se propaga para reintentar.
 */
public class ClienteNotificaciones implements EmisorDeAvisos {

    private final RestClient http;
    private final String base;

    public ClienteNotificaciones(RestClient http, String base) {
        this.http = Objects.requireNonNull(http);
        this.base = Objects.requireNonNull(base).replaceAll("/+$", "");
    }

    @Override
    public Resultado entregar(AvisoPendiente aviso) {
        try {
            http.post()
                    .uri(base + "/internal/notifications")
                    .body(new Peticion(aviso.usuarioId().toString(), aviso.id().toString(), aviso.tipo(),
                            aviso.titulo(), aviso.cuerpo(), aviso.creadoEn().toInstant()))
                    .retrieve()
                    .toBodilessEntity();
            return Resultado.ENTREGADO;
        } catch (HttpClientErrorException rechazo) {
            if (rechazo.getStatusCode().value() == 409) {
                return Resultado.ENTREGADO;
            }
            return Resultado.RECHAZADO;
        }
    }

    /** {@code EmitirNotificacionRequest} del contrato. */
    record Peticion(String usuarioId, String id, String tipo, String titulo, String cuerpo, Instant creadaEn) { }
}

package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * {@code POST /api/v1/correos/sancion} del servicio de correo
 * ({@code contracts/openapi/correo.yaml} 1.4.0), con la credencial de servicio
 * de este modulo: correo solo atiende a {@code rol=SERVICIO}.
 *
 * <p>Va siempre con {@code Idempotency-Key}: la clave estable de la salida
 * ({@code sancion-<id>-emision}, {@code apelacion-<id>-resolucion}). Si un
 * intento llego pero su respuesta se perdio, el reintento lleva la misma clave
 * y correo no encola un segundo correo. Un 202 significa «guardado en la cola
 * persistente de correo», no «entregado en la bandeja».
 */
public class ClienteCorreo {

    static final String RUTA = "/api/v1/correos/sancion";
    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    private final RestClient http;
    private final String base;

    public ClienteCorreo(RestClient http, String base) {
        this.http = Objects.requireNonNull(http);
        this.base = Objects.requireNonNull(base).replaceAll("/+$", "");
    }

    public void enviar(String claveDeIdempotencia, CorreoSancion correo) {
        http.post()
                .uri(base + RUTA)
                .header(CABECERA_IDEMPOTENCIA, Objects.requireNonNull(claveDeIdempotencia))
                .body(correo)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * {@code CorreoSancionRequest} del contrato.
     *
     * @param tipo               ADVERTENCIA, SUSPENSION, BANEO o APELACION_RESUELTA
     * @param hasta              fin de la suspension (o la nueva, si una apelacion la redujo)
     * @param apelableHasta      ultimo momento para apelar (solo en la emision)
     * @param resultadoApelacion MANTENIDA, REDUCIDA o REVERTIDA (solo APELACION_RESUELTA)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CorreoSancion(String email, String apodo, String tipo, String motivo, OffsetDateTime hasta,
                                OffsetDateTime apelableHasta, String resultadoApelacion) {
    }
}

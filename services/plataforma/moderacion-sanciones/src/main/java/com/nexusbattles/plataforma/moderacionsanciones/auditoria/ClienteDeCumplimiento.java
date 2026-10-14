package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * {@code POST /api/v1/admin/auditoria/eventos} de ms-cumplimiento
 * (ms-cumplimiento-auditoria.yaml 1.3.0), con la credencial de servicio de
 * este modulo: el endpoint solo admite {@code rol=SERVICIO}.
 */
public class ClienteDeCumplimiento {

    /** El resultado de una entrega. */
    public enum Resultado {
        /** 201 (nueva) o 200 (la referencia ya estaba). */
        REGISTRADO,
        /** 4xx: el destino la rechaza; reintentarla tal cual no la arreglaria. */
        RECHAZADO
    }

    private final RestClient http;
    private final String url;

    public ClienteDeCumplimiento(RestClient http, String url) {
        this.http = Objects.requireNonNull(http);
        this.url = Objects.requireNonNull(url);
    }

    /**
     * @throws org.springframework.web.client.RestClientException si el destino
     *         no responde o responde 5xx: la entrega se reintenta
     */
    public Resultado entregar(AsientoDeAuditoria asiento) {
        HttpStatusCode estado = http.post()
                .uri(url)
                .body(new Evento(asiento.tipoAccion(), asiento.administradorId(), asiento.afectado(),
                        asiento.valorAnterior(), asiento.valorNuevo(), asiento.motivo(), asiento.ipOrigen(),
                        asiento.ocurridoEn().toInstant().toString(), asiento.referencia()))
                .exchange((peticion, respuesta) -> respuesta.getStatusCode());
        if (estado.is2xxSuccessful()) {
            return Resultado.REGISTRADO;
        }
        if (estado.is4xxClientError()) {
            return Resultado.RECHAZADO;
        }
        throw new org.springframework.web.client.HttpServerErrorException(estado);
    }

    /** {@code RegistrarAuditoriaRequest} 1.3.0; {@code ocurridoEn} en ISO-8601 (UTC). */
    record Evento(String tipoAccion, String administradorId, String afectado, String valorAnterior,
                  String valorNuevo, String motivo, String ipOrigen, String ocurridoEn, String referencia) {
    }
}

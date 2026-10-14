package com.nexusbattles.plataforma.adminparametros.parametros;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * {@code POST /admin/auditoria/eventos} de ms-cumplimiento
 * (ms-cumplimiento-auditoria.yaml 1.3.0), con la credencial de servicio de
 * admin-parametros: el endpoint solo admite {@code rol=SERVICIO}.
 *
 * <p>SA-5: la version es el asiento. Viaja con la IP de quien cambio, el
 * momento del cambio ({@code ocurridoEn}) y la referencia
 * {@code admin-parametros:version-<id>}, que hace idempotente el reintento.
 */
public class ClienteAuditoria {

    static final String TIPO_ACCION = "ACTUALIZACION";
    static final String REFERENCIA = "admin-parametros:version-";

    /** El resultado de una entrega. */
    public enum Resultado {
        /** 201 (nueva) o 200 (la referencia ya estaba). */
        REGISTRADO,
        /** 4xx: ms-cumplimiento la rechaza; reintentarla tal cual no la arreglaria. */
        RECHAZADO
    }

    private final RestClient http;
    private final String url;

    public ClienteAuditoria(RestClient http, String url) {
        this.http = Objects.requireNonNull(http);
        this.url = Objects.requireNonNull(url);
    }

    /**
     * @throws org.springframework.web.client.RestClientException si no responde
     *         o responde 5xx: la entrega se reintenta
     */
    public Resultado entregar(Version v) {
        return enviar(eventoDe(v));
    }

    /**
     * Un asiento ya armado: lo usa la entrega de los cambios en las politicas
     * de uso (SA-11), que arma el suyo sin que este paquete la conozca.
     *
     * @throws org.springframework.web.client.RestClientException si no responde
     *         o responde 5xx: la entrega se reintenta
     */
    public Resultado enviar(Evento evento) {
        HttpStatusCode estado = http.post()
                .uri(url)
                .body(Objects.requireNonNull(evento))
                .exchange((peticion, respuesta) -> respuesta.getStatusCode());
        if (estado.is2xxSuccessful()) {
            return Resultado.REGISTRADO;
        }
        if (estado.is4xxClientError()) {
            return Resultado.RECHAZADO;
        }
        throw new HttpServerErrorException(estado);
    }

    /**
     * Un cambio programado lo dice en el valor nuevo: «4 (desde
     * 2026-10-20T05:00Z)». Una version sin IP (no deberia haberlas desde V7)
     * dice «desconocida»: ms-cumplimiento exige el campo.
     */
    static Evento eventoDe(Version v) {
        boolean programado = v.vigenteDesde() != null && v.cambiadoEn() != null
                && v.vigenteDesde().isAfter(v.cambiadoEn());
        String nuevo = v.valorNuevo() == null ? "sin definir" : v.valorNuevo();
        return new Evento(TIPO_ACCION, v.cambiadoPor().toString(), "parametro:" + v.clave(),
                v.valorAnterior() == null ? "sin definir" : v.valorAnterior(),
                programado ? nuevo + " (desde " + v.vigenteDesde().toInstant() + ")" : nuevo,
                v.motivo(),
                v.ipOrigen() == null || v.ipOrigen().isBlank() ? "desconocida" : v.ipOrigen(),
                v.cambiadoEn().toInstant().toString(),
                REFERENCIA + v.id());
    }

    /** El cuerpo de {@code RegistrarAuditoriaRequest} (ms-cumplimiento-auditoria.yaml 1.3.0). */
    public record Evento(String tipoAccion, String administradorId, String afectado, String valorAnterior,
                         String valorNuevo, String motivo, String ipOrigen, String ocurridoEn, String referencia) { }
}

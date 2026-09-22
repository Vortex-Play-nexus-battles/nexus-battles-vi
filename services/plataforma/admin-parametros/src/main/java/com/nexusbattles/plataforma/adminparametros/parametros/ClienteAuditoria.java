package com.nexusbattles.plataforma.adminparametros.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code POST /admin/auditoria/eventos} de ms-cumplimiento con la forma de su
 * {@code RegistrarAuditoriaRequest}. Fail-open con bitacora.
 */
public class ClienteAuditoria implements Auditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(ClienteAuditoria.class);

    static final String TIPO_ACCION = "ACTUALIZACION";

    private final RestClient http;
    private final String url;

    public ClienteAuditoria(RestClient http, String url) {
        this.http = http;
        this.url = url;
    }

    @Override
    public void registrar(Version v) {
        try {
            http.post()
                    .uri(url)
                    .body(new Evento(TIPO_ACCION, v.cambiadoPor().toString(), "parametro:" + v.clave(),
                            v.valorAnterior(), v.valorNuevo(), v.motivo(), null))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException noResponde) {
            BITACORA.warn("La auditoria no respondio; el cambio de {} (v{}) queda solo en el historial local: {}",
                    v.clave(), v.version(), noResponde.getMessage());
        }
    }

    record Evento(String tipoAccion, String administradorId, String afectado, String valorAnterior,
                  String valorNuevo, String motivo, String ipOrigen) { }
}

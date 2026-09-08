package com.nexusbattles.plataforma.salaspartidas.chat.integracion;

import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Filtro de contenido contra la lista negra de HU-ADM-002, segun
 * contracts/openapi/moderacion-lista-negra.yaml.
 *
 * <p>Si el servicio no responde, el veredicto es SIN_VERIFICAR y el caso de
 * uso bloquea el mensaje avisando al autor. Publicar sin verificar romperia
 * la postcondicion de RF-COM-007, y aqui, a diferencia de comentarios, no hay
 * un moderador que revise despues: el mensaje del chat o sale ya o no sale.
 */
@Component
class ClienteListaNegra implements FiltroDeContenido {

    private static final Logger log = LoggerFactory.getLogger(ClienteListaNegra.class);

    private final RestClient restClient;
    private final String urlVerificacion;

    ClienteListaNegra(RestClient restClientChat, @Value("${chat.lista-negra.url}") String urlVerificacion) {
        this.restClient = restClientChat;
        this.urlVerificacion = urlVerificacion;
    }

    @Override
    public Veredicto verificar(String texto) {
        try {
            RespuestaVerificacion respuesta = restClient.post()
                    .uri(urlVerificacion)
                    .body(new SolicitudVerificacion(texto))
                    .retrieve()
                    .body(RespuestaVerificacion.class);
            if (respuesta == null) {
                return sinVerificar("respuesta vacia del servicio");
            }
            return respuesta.aprobado() ? Veredicto.LIMPIO : Veredicto.SENALADO;
        } catch (RestClientException ex) {
            return sinVerificar(ex.getMessage());
        }
    }

    private static Veredicto sinVerificar(String motivo) {
        log.warn("Lista negra no disponible, el mensaje del chat se bloquea sin verificar. Motivo: {}", motivo);
        return Veredicto.SIN_VERIFICAR;
    }

    record SolicitudVerificacion(String texto) { }

    record RespuestaVerificacion(boolean aprobado, String motivo) { }
}

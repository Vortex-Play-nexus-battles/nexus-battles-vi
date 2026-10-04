package com.nexusbattles.plataforma.salaspartidas.chat.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Filtro de contenido contra la lista negra de HU-ADM-002, segun
 * contracts/openapi/moderacion-lista-negra.yaml 2.0.x.
 *
 * <p>Manda el {@code contexto} del canal (CHAT_SALA o CHAT_GENERAL) para que
 * moderacion aplique su politica para el chat; sin el, el texto se trataba
 * como GENERICO. Lee la respuesta igual que {@code ClienteListaNegraMensajesPrivados}:
 * si trae {@code accion}, manda la {@code accion} y solo PERMITIR publica; si
 * no la trae (moderacion anterior a 2.0.0), manda {@code aprobado}. Los campos
 * que el chat no usa (categoria, coincidencias) se ignoran en vez de romper la
 * lectura.
 *
 * <p>Si el servicio no responde, o la respuesta no trae veredicto, el
 * resultado es SIN_VERIFICAR y el caso de uso bloquea el mensaje avisando al
 * autor. Publicar sin verificar romperia la postcondicion de RF-COM-007, y
 * aqui, a diferencia de comentarios, no hay un moderador que revise despues:
 * el mensaje del chat o sale ya o no sale. Por eso REVISION tambien bloquea.
 */
@Component
class ClienteListaNegra implements FiltroDeContenido {

    private static final Logger log = LoggerFactory.getLogger(ClienteListaNegra.class);

    static final String PERMITIR = "PERMITIR";

    private final RestClient restClient;
    private final String urlVerificacion;

    ClienteListaNegra(RestClient restClientChat, @Value("${chat.lista-negra.url}") String urlVerificacion) {
        this.restClient = restClientChat;
        this.urlVerificacion = urlVerificacion;
    }

    @Override
    public Veredicto verificar(String texto, Canal canal) {
        try {
            RespuestaVerificacion respuesta = restClient.post()
                    .uri(urlVerificacion)
                    .body(new SolicitudVerificacion(texto, contextoDe(canal)))
                    .retrieve()
                    .body(RespuestaVerificacion.class);
            return veredictoDe(respuesta);
        } catch (RestClientException ex) {
            return sinVerificar(ex.getMessage());
        }
    }

    /** El {@code contexto} de moderacion-lista-negra.yaml que le toca al canal. */
    static String contextoDe(Canal canal) {
        return canal.esGeneral() ? "CHAT_GENERAL" : "CHAT_SALA";
    }

    private static Veredicto veredictoDe(RespuestaVerificacion respuesta) {
        if (respuesta == null) {
            return sinVerificar("respuesta vacia del servicio");
        }
        if (respuesta.accion() != null && !respuesta.accion().isBlank()) {
            return PERMITIR.equals(respuesta.accion()) ? Veredicto.LIMPIO : Veredicto.SENALADO;
        }
        if (respuesta.aprobado() == null) {
            return sinVerificar("respuesta sin veredicto");
        }
        return respuesta.aprobado() ? Veredicto.LIMPIO : Veredicto.SENALADO;
    }

    private static Veredicto sinVerificar(String motivo) {
        log.warn("Lista negra no disponible, el mensaje del chat se bloquea sin verificar. Motivo: {}", motivo);
        return Veredicto.SIN_VERIFICAR;
    }

    record SolicitudVerificacion(String texto, String contexto) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaVerificacion(Boolean aprobado, String accion) { }
}

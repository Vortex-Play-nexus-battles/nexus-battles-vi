package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.FiltroDeMensajesPrivados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

/**
 * La lista negra para los mensajes privados — B6, contra
 * {@code POST /lista-negra/verificar} de
 * {@code contracts/openapi/moderacion-lista-negra.yaml} 2.0.x.
 *
 * <p>Distinto del filtro del chat ({@code chat.integracion.ClienteListaNegra},
 * de HU-JUE-015) en dos cosas, y por eso es otra clase y no un parametro de
 * aquella: manda {@code contexto: MENSAJE_PRIVADO}, para que moderacion aplique
 * SU politica para ese contexto (BLOQUEAR por omision) en vez de que este
 * servicio invente una; y va con credencial de servicio y tiempos acotados.
 *
 * <p><b>Como se lee la respuesta.</b> Si trae {@code accion}, manda la
 * {@code accion}: solo {@code PERMITIR} entrega. Si no la trae (un
 * moderacion-sanciones anterior a 2.0.0), manda {@code aprobado}. Lo que no se
 * pueda leer, o que no llegue, es SIN_VERIFICAR y el mensaje no sale.
 *
 * <p>El texto del mensaje nunca va a la bitacora: es privado.
 */
public class ClienteListaNegraMensajesPrivados implements FiltroDeMensajesPrivados {

    private static final Logger log = LoggerFactory.getLogger(ClienteListaNegraMensajesPrivados.class);

    static final String CONTEXTO = "MENSAJE_PRIVADO";
    static final String PERMITIR = "PERMITIR";

    private final RestClient http;
    private final String urlVerificacion;

    /** @param urlVerificacion la de {@code LISTA_NEGRA_VERIFICAR_URL}, la misma que usa el chat */
    public ClienteListaNegraMensajesPrivados(RestClient http, String urlVerificacion) {
        this.http = Objects.requireNonNull(http);
        this.urlVerificacion = Objects.requireNonNull(urlVerificacion, "Hace falta la URL de la lista negra");
    }

    @Override
    public Veredicto verificar(String texto) {
        try {
            Respuesta respuesta = http.post()
                    .uri(urlVerificacion)
                    .body(new Solicitud(texto, CONTEXTO))
                    .retrieve()
                    .body(Respuesta.class);
            return veredictoDe(respuesta);
        } catch (RestClientException ex) {
            return sinVerificar(ex.getClass().getSimpleName());
        }
    }

    private static Veredicto veredictoDe(Respuesta respuesta) {
        if (respuesta == null) {
            return sinVerificar("respuesta vacia");
        }
        if (respuesta.accion() != null && !respuesta.accion().isBlank()) {
            return PERMITIR.equals(respuesta.accion()) ? Veredicto.ENTREGABLE : Veredicto.BLOQUEADO;
        }
        if (respuesta.aprobado() == null) {
            return sinVerificar("respuesta sin veredicto");
        }
        return respuesta.aprobado() ? Veredicto.ENTREGABLE : Veredicto.BLOQUEADO;
    }

    private static Veredicto sinVerificar(String motivo) {
        log.warn("Lista negra no disponible: el mensaje privado no se entrega sin verificar. Motivo: {}", motivo);
        return Veredicto.SIN_VERIFICAR;
    }

    record Solicitud(String texto, String contexto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(Boolean aprobado, String accion) {
    }
}

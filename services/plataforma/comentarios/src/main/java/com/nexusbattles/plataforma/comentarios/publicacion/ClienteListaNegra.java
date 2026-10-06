package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.comentarios.DeteccionAutomatica;
import com.nexusbattles.plataforma.comentarios.publicacion.FiltroDeContenido.VeredictoDelFiltro;

/**
 * Filtro automatico de contenido respaldado por la lista negra de HU-ADM-002.
 *
 * <p>Consume el endpoint de verificacion que moderacion-sanciones publico en
 * contracts/openapi/moderacion-lista-negra.yaml, con el mismo patron de
 * RestClient que ya usa el equipo de cuentas para validar apodos.
 *
 * <h2>Contexto y accion (lista negra 2.0.0, B3)</h2>
 *
 * <p>La verificacion se pide con {@code contexto: COMENTARIO}, y la respuesta
 * trae la {@code accion} que la politica de moderacion aplica a ese contexto:
 * para un comentario, {@code REVISION}. Se obedece la accion y no se inventa
 * otra: {@code PERMITIR} publica y cualquier otra retiene el comentario en
 * revision. Si la politica llegara a decir {@code RECHAZAR} o {@code BLOQUEAR}
 * para comentarios, tambien se retiene: nada inapropiado sale publicado y un
 * moderador decide, que es lo que pide RF-COM-007. Un servidor anterior a la
 * 2.0.0, que no manda {@code accion}, se sigue entendiendo por {@code
 * aprobado}.
 *
 * <h2>Por que retuvo (HU-COM-007 CA-01, lista negra 2.1.0)</h2>
 *
 * <p>Lo que retiene trae ademas su {@link DeteccionAutomatica}: los {@code id}
 * de las reglas que coincidieron ({@code reglas}, 2.1.0), la categoria y el
 * motivo generico, con la fecha de la verificacion. Las {@code coincidencias}
 * —los terminos mismos— no se leen: el moderador ve la regla por su id, nunca
 * el termino.
 *
 * <h2>Si la lista negra no contesta</h2>
 *
 * <p>Hay una diferencia deliberada con el cliente de los apodos. Para los
 * apodos se decidio dejar pasar cuando el servicio no responde. Aqui es al
 * reves: la postcondicion de RF-COM-007 es que el contenido inapropiado no
 * alcance la publicacion sin revision, y el proceso principal de RF-COM-001
 * valida todo comentario contra el filtro, asi que si el filtro no contesta,
 * publicar sin verificar romperia el requisito. Lo unico que no lo rompe es
 * retener el comentario en revision y que un moderador decida, y siempre queda
 * constancia en la bitacora, nunca en silencio. Desde HU-COM-007 queda ademas
 * en la deteccion, con {@code servicioNoDisponible}, sin reglas y con un motivo
 * fijo ({@link #MOTIVO_SIN_SERVICIO}), para que el moderador sepa que se retuvo
 * por precaucion. El mensaje de la excepcion, que puede llevar la URL interna
 * o el cuerpo del error, va solo a la bitacora. El RestClient tiene tiempos
 * de conexion y de lectura acotados (ConfiguracionClientesHttp): sin ellos, un
 * servicio colgado dejaria la publicacion esperando en vez de retenerla.
 *
 * <p>El reintento y el cortacircuitos de Resilience4j quedan pendientes: el
 * complemento nexus.spring-conventions no gestiona la version de ese artefacto
 * y fijarla por servicio contradice la regla de no crear configuracion de build
 * propia. Mientras se acuerda en las convenciones compartidas, el respaldo
 * esta implementado a mano con la misma semantica.
 */
@Component
class ClienteListaNegra implements FiltroDeContenido {

    private static final Logger log = LoggerFactory.getLogger(ClienteListaNegra.class);

    /** {@code ContextoDeTexto} de la lista negra 2.0.0 para el texto de un comentario. */
    static final String CONTEXTO = "COMENTARIO";

    /** La unica accion que deja publicar. */
    static final String PERMITIR = "PERMITIR";

    /** Motivo de la deteccion cuando la lista negra no contesta, sea cual sea la falla. */
    static final String MOTIVO_SIN_SERVICIO =
            "La lista negra no respondio; el comentario queda retenido para revision";

    private final RestClient restClient;
    private final String urlVerificacion;
    private final Clock reloj;

    ClienteListaNegra(
            RestClient restClientComentarios,
            @Value("${comentarios.lista-negra.url}") String urlVerificacion,
            Clock reloj) {
        this.restClient = restClientComentarios;
        this.urlVerificacion = urlVerificacion;
        this.reloj = reloj;
    }

    @Override
    public VeredictoDelFiltro verificar(String texto) {
        try {
            RespuestaVerificacion respuesta = restClient.post()
                    .uri(urlVerificacion)
                    .body(new SolicitudVerificacion(texto, CONTEXTO))
                    .retrieve()
                    .body(RespuestaVerificacion.class);
            if (respuesta == null) {
                return retenerPorFalla("respuesta vacia del servicio");
            }
            boolean publica = respuesta.accion() != null
                    ? PERMITIR.equals(respuesta.accion())
                    : respuesta.aprobado();
            if (publica) {
                return VeredictoDelFiltro.limpio();
            }
            return VeredictoDelFiltro.senalado(new DeteccionAutomatica(Instant.now(reloj),
                    respuesta.reglas(), respuesta.categoria(), respuesta.motivo(), false));
        } catch (RestClientException ex) {
            return retenerPorFalla(ex.getMessage());
        }
    }

    private VeredictoDelFiltro retenerPorFalla(String causa) {
        log.warn(
                "Servicio de lista negra no disponible, el comentario queda retenido en revision"
                        + " para cumplir RF-COM-007. Motivo: {}",
                causa);
        return VeredictoDelFiltro.senalado(new DeteccionAutomatica(
                Instant.now(reloj), List.of(), null, MOTIVO_SIN_SERVICIO, true));
    }

    /** Cuerpo del POST /lista-negra/verificar segun el contrato 2.0.0. */
    record SolicitudVerificacion(String texto, String contexto) {
    }

    /**
     * Respuesta del contrato: aprobado y accion (2.0.0); motivo, categoria y
     * reglas (2.1.0) para la deteccion. Las coincidencias —los terminos
     * mismos— no se leen: no hay donde guardarlas.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaVerificacion(boolean aprobado, String accion, String motivo, String categoria,
            List<Long> reglas) {
    }
}

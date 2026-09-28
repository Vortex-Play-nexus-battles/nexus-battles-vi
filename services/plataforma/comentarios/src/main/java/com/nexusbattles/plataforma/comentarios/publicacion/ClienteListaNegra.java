package com.nexusbattles.plataforma.comentarios.publicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro;

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
 * <h2>Si la lista negra no contesta</h2>
 *
 * <p>Hay una diferencia deliberada con el cliente de los apodos. Para los
 * apodos se decidio dejar pasar cuando el servicio no responde. Aqui es al
 * reves: la postcondicion de RF-COM-007 es que el contenido inapropiado no
 * alcance la publicacion sin revision, y el proceso principal de RF-COM-001
 * valida todo comentario contra el filtro, asi que si el filtro no contesta,
 * publicar sin verificar romperia el requisito. Lo unico que no lo rompe es
 * retener el comentario en revision y que un moderador decida, y siempre queda
 * constancia en la bitacora, nunca en silencio. El RestClient tiene tiempos
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

    private final RestClient restClient;
    private final String urlVerificacion;

    ClienteListaNegra(
            RestClient restClientComentarios,
            @Value("${comentarios.lista-negra.url}") String urlVerificacion) {
        this.restClient = restClientComentarios;
        this.urlVerificacion = urlVerificacion;
    }

    @Override
    public ResultadoDelFiltro verificar(String texto) {
        try {
            RespuestaVerificacion respuesta = restClient.post()
                    .uri(urlVerificacion)
                    .body(new SolicitudVerificacion(texto, CONTEXTO))
                    .retrieve()
                    .body(RespuestaVerificacion.class);
            if (respuesta == null) {
                return retenerPorFalla("respuesta vacia del servicio");
            }
            if (respuesta.accion() != null) {
                return PERMITIR.equals(respuesta.accion()) ? ResultadoDelFiltro.LIMPIO : ResultadoDelFiltro.SENALADO;
            }
            return respuesta.aprobado() ? ResultadoDelFiltro.LIMPIO : ResultadoDelFiltro.SENALADO;
        } catch (RestClientException ex) {
            return retenerPorFalla(ex.getMessage());
        }
    }

    private ResultadoDelFiltro retenerPorFalla(String motivo) {
        log.warn(
                "Servicio de lista negra no disponible, el comentario queda retenido en revision"
                        + " para cumplir RF-COM-007. Motivo: {}",
                motivo);
        return ResultadoDelFiltro.SENALADO;
    }

    /** Cuerpo del POST /lista-negra/verificar segun el contrato 2.0.0. */
    record SolicitudVerificacion(String texto, String contexto) {
    }

    /**
     * Respuesta del contrato: aprobado y accion (2.0.0); motivo, categoria y
     * coincidencias, que aqui no se usan, se ignoran.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaVerificacion(boolean aprobado, String accion, String motivo) {
    }
}

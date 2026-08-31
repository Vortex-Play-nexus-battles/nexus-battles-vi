package com.nexusbattles.plataforma.comentarios.publicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro;

/**
 * Filtro automatico de contenido respaldado por la lista negra de HU-ADM-002.
 *
 * <p>Consume el endpoint de verificacion que moderacion-sanciones publico en
 * contracts/openapi/moderacion-lista-negra.yaml, con el mismo patron de
 * RestClient que ya usa el equipo de cuentas para validar apodos.
 *
 * <p>Hay una diferencia deliberada con ese cliente. Para los apodos se decidio
 * dejar pasar cuando el servicio no responde. Aqui es al reves: RN-CMT-001
 * exige que todo comentario pase por el filtro antes de publicarse, asi que si
 * el filtro no contesta, publicar sin verificar romperia la regla. Lo unico que
 * no la rompe es retener el comentario en revision y que un moderador decida,
 * y siempre queda constancia en la bitacora, nunca en silencio.
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
                    .body(new SolicitudVerificacion(texto))
                    .retrieve()
                    .body(RespuestaVerificacion.class);
            if (respuesta == null) {
                return retenerPorFalla("respuesta vacia del servicio");
            }
            return respuesta.aprobado() ? ResultadoDelFiltro.LIMPIO : ResultadoDelFiltro.SENALADO;
        } catch (RestClientException ex) {
            return retenerPorFalla(ex.getMessage());
        }
    }

    private ResultadoDelFiltro retenerPorFalla(String motivo) {
        log.warn(
                "Servicio de lista negra no disponible, el comentario queda retenido en revision"
                        + " para cumplir RN-CMT-001. Motivo: {}",
                motivo);
        return ResultadoDelFiltro.SENALADO;
    }

    /** Cuerpo del POST /lista-negra/verificar segun el contrato. */
    record SolicitudVerificacion(String texto) {
    }

    /** Respuesta del contrato: aprobado, y motivo solo cuando no lo esta. */
    record RespuestaVerificacion(boolean aprobado, String motivo) {
    }
}

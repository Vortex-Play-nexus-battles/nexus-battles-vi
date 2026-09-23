package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;

import com.nexusbattles.plataforma.comentarios.Comentario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Las dos salidas del flujo de moderacion hacia otros servicios, y el reloj.
 *
 * <p>Las dos son fail-open a proposito y por la misma razon: una decision de
 * moderacion ya confirmada no puede deshacerse porque un servicio de avisos o
 * la bitacora transversal no respondan. Lo que no se hace es tragarse el
 * fallo: el aviso devuelve si salio, y los dos dejan rastro en la bitacora
 * local en JSON (regla 6).
 */
@Configuration
public class AdaptadoresDeModeracion {

    private static final Logger log = LoggerFactory.getLogger(AdaptadoresDeModeracion.class);

    /**
     * El reloj del servicio. Se declara como bean para que las pruebas puedan
     * fijarlo: un limite "por dia" que dependa del reloj del sistema es un
     * limite que no se puede probar sin esperar un dia.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock relojDelSistema() {
        return Clock.systemUTC();
    }

    /**
     * Aviso al autor contra {@code POST /internal/notifications}
     * (contracts/openapi/notificaciones.yaml 1.1.0). El RestClient llega con la
     * credencial de servicio: esa ruta es ROLE_SERVICIO (ADR-005).
     */
    @Bean
    public AvisoAlAutor avisoAlAutor(
            RestClient restClientComentarios,
            @Value("${comentarios.notificaciones.url:}") String base) {
        String destino = base == null ? "" : base.replaceAll("/+$", "");
        return (Comentario comentario, AsientoDeModeracion asiento) -> {
            if (destino.isEmpty()) {
                log.warn("Sin URL de notificaciones: el autor {} no se entera de {} sobre {}",
                        comentario.autorId(), asiento.accion(), comentario.id());
                return false;
            }
            try {
                restClientComentarios.post()
                        .uri(destino + "/internal/notifications")
                        .body(new AvisoDeModeracion(
                                comentario.autorId(),
                                asiento.id(),
                                "MODERACION_COMENTARIO",
                                tituloDe(asiento.accion()),
                                asiento.motivo(),
                                asiento.fecha().toString()))
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (RestClientException fallo) {
                // Fail-open: la decision ya esta tomada y guardada. Lo unico
                // que se pierde es el aviso, y queda dicho que se perdio.
                log.warn("El aviso de moderacion sobre {} no salio: {}",
                        comentario.id(), fallo.getMessage());
                return false;
            }
        };
    }

    /**
     * Bitacora transversal. ms-cumplimiento no esta desplegado en dev por
     * capacidad (#459): hoy solo existe en el banco E2E. Sin URL, el asiento
     * queda al menos en la bitacora local en JSON, que es lo que se puede
     * garantizar mientras tanto.
     */
    @Bean
    public RegistroDeAuditoria registroDeAuditoria(
            RestClient restClientComentarios,
            @Value("${comentarios.auditoria.url:}") String base) {
        String destino = base == null ? "" : base.replaceAll("/+$", "");
        return asiento -> {
            if (destino.isEmpty()) {
                log.info("AUDITORIA_MODERACION accion={} comentario={} moderador={} de={} a={}",
                        asiento.accion(), asiento.comentarioId(), asiento.moderadorId(),
                        asiento.estadoAnterior(), asiento.estadoNuevo());
                return;
            }
            try {
                restClientComentarios.post()
                        .uri(destino + "/eventos")
                        .body(new EventoDeAuditoria(
                                asiento.moderadorId(),
                                "MODERACION_COMENTARIO_" + asiento.accion(),
                                asiento.comentarioId(),
                                asiento.motivo(),
                                asiento.fecha().toString()))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException fallo) {
                log.warn("El evento de auditoria de {} no salio: {}",
                        asiento.comentarioId(), fallo.getMessage());
            }
        };
    }

    private static String tituloDe(AccionDeModeracion accion) {
        return switch (accion) {
            case APROBAR -> "Tu comentario sigue publicado";
            case OCULTAR -> "Tu comentario se oculto";
            case ELIMINAR -> "Tu comentario se elimino";
            case RESTAURAR -> "Tu comentario vuelve a estar visible";
        };
    }

    /** Cuerpo de {@code EmitirNotificacionRequest} (notificaciones 1.1.0). */
    record AvisoDeModeracion(String usuarioId, String id, String tipo,
            String titulo, String cuerpo, String creadaEn) {
    }

    /** Cuerpo del evento de ms-cumplimiento. */
    record EventoDeAuditoria(String actor, String accion, String entidad,
            String detalle, String fecha) {
    }
}

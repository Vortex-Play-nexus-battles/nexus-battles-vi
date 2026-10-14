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
 * Las salidas del flujo de moderacion hacia otros servicios, y el reloj.
 *
 * <p>Una decision de moderacion ya confirmada no puede deshacerse porque un
 * servicio de avisos o la bitacora transversal no respondan. El aviso al autor
 * es fail-open y devuelve si salio; la bitacora central (SA-5) sale de la cola
 * del propio asiento ({@link EnvioDeAuditoria}), con reintentos. Las dos dejan
 * rastro en la bitacora local en JSON (regla 6).
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
     * SA-5 (§7.3.5): la linea de cada decision en la bitacora JSON (regla 6).
     * La entrega a ms-cumplimiento la hace {@link EnvioDeAuditoria} desde el
     * asiento, que ya se guardo con la decision: aqui no se llama a nadie, asi
     * que una bitacora central caida no retrasa ni tumba una decision.
     */
    @Bean
    public RegistroDeAuditoria registroDeAuditoria() {
        return asiento -> log.info("AUDITORIA_MODERACION accion={} comentario={} moderador={} de={} a={} asiento={}",
                asiento.accion(), asiento.comentarioId(), asiento.moderadorId(),
                asiento.estadoAnterior(), asiento.estadoNuevo(), asiento.id());
    }

    /**
     * SA-5: la entrega a la bitacora central, con la credencial de servicio de
     * comentarios (el endpoint solo admite {@code rol=SERVICIO}). Sin
     * {@code AUDITORIA_URL} no hay entrega: los asientos esperan y salen en
     * cuanto se configure. Admite la URL de la bitacora o la de sus eventos.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
            "!'${comentarios.auditoria.url:}'.isBlank()")
    public EnvioDeAuditoria envioDeAuditoria(
            AsientoRepository asientos,
            RestClient restClientComentarios,
            Clock reloj,
            @Value("${comentarios.auditoria.url}") String url,
            @Value("${comentarios.auditoria.reintento-ms:10000}") long reintentoMs,
            @Value("${comentarios.auditoria.espera-maxima-ms:900000}") long esperaMaximaMs) {
        String limpia = url.strip().replaceAll("/+$", "");
        String destino = limpia.endsWith("/eventos") ? limpia : limpia + "/eventos";
        return new EnvioDeAuditoria(asientos, restClientComentarios, destino, reloj,
                java.time.Duration.ofMillis(reintentoMs), java.time.Duration.ofMillis(esperaMaximaMs));
    }

    /**
     * MARCAR y DESMARCAR no llegan aqui (no se avisan: ver
     * {@link AccionDeModeracion#seAvisaAlAutor()}), pero el switch las nombra
     * para que el compilador obligue a pensar en cada accion nueva.
     */
    private static String tituloDe(AccionDeModeracion accion) {
        return switch (accion) {
            case APROBAR -> "Tu comentario sigue publicado";
            case OCULTAR -> "Tu comentario se oculto";
            case ELIMINAR -> "Tu comentario se elimino";
            case RESTAURAR -> "Tu comentario vuelve a estar visible";
            case EDITAR -> "Moderacion edito tu comentario";
            case MARCAR, DESMARCAR -> "Tu comentario tiene seguimiento de moderacion";
        };
    }

    /** Cuerpo de {@code EmitirNotificacionRequest} (notificaciones 1.1.0). */
    record AvisoDeModeracion(String usuarioId, String id, String tipo,
            String titulo, String cuerpo, String creadaEn) {
    }

}

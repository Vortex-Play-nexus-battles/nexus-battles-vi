package com.nexusbattles.plataforma.comentarios.moderacion;

import com.nexusbattles.plataforma.comentarios.Comentario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Lleva cada decision de moderacion a la bitacora central de ms-cumplimiento
 * — §7.3.5 (SA-5), ms-cumplimiento-auditoria.yaml 1.3.0.
 *
 * <p>El asiento de moderacion ya se guarda en la transaccion de la decision
 * (es el historial que ven los moderadores); aqui solo se entrega. Cada
 * {@code comentarios.auditoria.reintento-ms} (10 s) toma los pendientes cuya
 * espera paso, del mas antiguo al mas reciente, con la credencial de servicio:
 * <ul>
 *   <li>201 o 200 (la referencia ya estaba): auditado.</li>
 *   <li>4xx: se queda con el motivo y una espera creciente; nunca se descarta.</li>
 *   <li>Sin respuesta o 5xx: se para la vuelta y se reintenta despues.</li>
 * </ul>
 *
 * <p>La referencia es {@code comentarios:<asiento>}: un reintento de una
 * entrega que entro sin llegar a marcarse no duplica la entrada.
 */
public class EnvioDeAuditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(EnvioDeAuditoria.class);

    static final int LOTE = 50;
    static final String REFERENCIA = "comentarios:";
    static final int LARGO_VALOR = 2000;
    static final int LARGO_TEXTO_CITADO = 300;

    private final AsientoRepository asientos;
    private final RestClient http;
    private final String url;
    private final Clock reloj;
    private final Duration esperaBase;
    private final Duration esperaMaxima;

    public EnvioDeAuditoria(AsientoRepository asientos, RestClient http, String url, Clock reloj,
                            Duration esperaBase, Duration esperaMaxima) {
        this.asientos = Objects.requireNonNull(asientos);
        this.http = Objects.requireNonNull(http);
        this.url = Objects.requireNonNull(url);
        this.reloj = Objects.requireNonNull(reloj);
        this.esperaBase = Objects.requireNonNull(esperaBase);
        this.esperaMaxima = Objects.requireNonNull(esperaMaxima);
    }

    @Scheduled(fixedDelayString = "${comentarios.auditoria.reintento-ms:10000}",
            initialDelayString = "${comentarios.auditoria.reintento-ms:10000}")
    public void entregarPendientes() {
        ejecutar();
    }

    /** @return cuantos asientos entraron en esta vuelta */
    public int ejecutar() {
        int entregados = 0;
        List<AsientoDeModeracion> pendientes = asientos.porAuditar(Instant.now(reloj), PageRequest.of(0, LOTE));
        for (AsientoDeModeracion asiento : pendientes) {
            Instant ahora = Instant.now(reloj);
            try {
                HttpStatusCode estado = http.post().uri(url).body(eventoDe(asiento))
                        .exchange((peticion, respuesta) -> respuesta.getStatusCode());
                if (estado.is2xxSuccessful()) {
                    asiento.auditado(ahora);
                    entregados++;
                } else if (estado.is4xxClientError()) {
                    asiento.falloDeAuditoria("rechazado por ms-cumplimiento: " + estado.value(), ahora, esperaBase,
                            esperaMaxima);
                    BITACORA.error("La decision de moderacion {} sobre {} fue rechazada por la bitacora central ({}); "
                            + "queda pendiente para revision", asiento.id(), asiento.comentarioId(), estado.value());
                } else {
                    throw new IllegalStateException("ms-cumplimiento respondio " + estado.value());
                }
                asientos.save(asiento);
            } catch (RuntimeException noResponde) {
                asiento.falloDeAuditoria(noResponde.getClass().getSimpleName() + ": " + noResponde.getMessage(),
                        ahora, esperaBase, esperaMaxima);
                asientos.save(asiento);
                BITACORA.warn("La bitacora central no responde; {} decision(es) esperan: {}",
                        pendientes.size() - entregados, noResponde.getMessage());
                break;
            }
        }
        return entregados;
    }

    /** {@code RegistrarAuditoriaRequest} 1.3.0 de una decision. */
    static Evento eventoDe(AsientoDeModeracion asiento) {
        boolean edicion = asiento.accion() == AccionDeModeracion.EDITAR;
        return new Evento(
                tipoDe(asiento.accion()),
                asiento.moderadorId(),
                "comentario:" + asiento.comentarioId(),
                recortar(nombreDe(asiento.estadoAnterior())
                        + (edicion && asiento.textoAnterior() != null ? " · «" + citado(asiento.textoAnterior()) + "»" : "")),
                recortar(nombreDe(asiento.estadoNuevo())
                        + (edicion && asiento.textoNuevo() != null ? " · «" + citado(asiento.textoNuevo()) + "»" : "")),
                asiento.motivo(),
                asiento.ipOrigen() == null || asiento.ipOrigen().isBlank() ? "desconocida" : asiento.ipOrigen(),
                asiento.fecha().toString(),
                REFERENCIA + asiento.id());
    }

    /** La accion de moderacion con el nombre de {@code TipoAccion} de ms-cumplimiento. */
    static String tipoDe(AccionDeModeracion accion) {
        return switch (accion) {
            case APROBAR -> "APROBACION";
            case ELIMINAR -> "ELIMINACION_LOGICA";
            case OCULTAR, RESTAURAR, EDITAR -> "ACTUALIZACION";
            case MARCAR, DESMARCAR -> "OTRO";
        };
    }

    /** El estado del comentario tal como lo ve quien modera. */
    static String nombreDe(Comentario.Estado estado) {
        if (estado == null) {
            return "Sin estado";
        }
        return switch (estado) {
            case PUBLICADO -> "Publicado";
            case EN_REVISION -> "En revisión";
            case OCULTO -> "Oculto";
            case ELIMINADO -> "Eliminado";
        };
    }

    private static String citado(String texto) {
        String limpio = texto.strip();
        return limpio.length() <= LARGO_TEXTO_CITADO ? limpio : limpio.substring(0, LARGO_TEXTO_CITADO - 1) + "…";
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_VALOR ? texto : texto.substring(0, LARGO_VALOR - 1) + "…";
    }

    /** Cuerpo de {@code POST /admin/auditoria/eventos}; {@code ocurridoEn} en ISO-8601. */
    record Evento(String tipoAccion, String administradorId, String afectado, String valorAnterior,
                  String valorNuevo, String motivo, String ipOrigen, String ocurridoEn, String referencia) {
    }
}

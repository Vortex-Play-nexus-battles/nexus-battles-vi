package com.nexusbattles.ms_chatbot.chat.auditoria;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * ms-chatbot.yaml 1.18.0 (§7.4.4, observación de RF-CHA-008 / HU-CHA-008:
 * «el acceso del chatbot a datos personales debe respetar el modelo de
 * autorización y quedar auditado»).
 *
 * <p>Cada vez que el asistente lee datos de la cuenta de una persona con su
 * token (inventario, una búsqueda en él, sus subastas y pujas, sus avisos,
 * sus movimientos de créditos, sus misiones o el informe de actividad), deja
 * un asiento en la bitácora central de ms-cumplimiento
 * ({@code POST /api/v1/admin/auditoria/eventos}, ms-cumplimiento-auditoria.yaml
 * 1.3.0), como hace ms-identidad con los hitos de una cuenta: tipo
 * {@code OTRO}, quien lo pidió y de quién son los datos (el mismo {@code uid},
 * nunca un parámetro: sale del token), qué dato y si se pudo consultar, la
 * dirección de origen y una referencia única. <b>Nunca el contenido</b>: ni la
 * pregunta, ni lo que respondió el otro servicio.
 *
 * <p>Lo lee solo un superadministrador con segundo factor, en la consola de
 * auditoría y en la ficha de la persona. No depende de la conversación: si la
 * persona borra su historial (§7.4.8), el asiento queda (RF-AUD: inmutable).
 *
 * <p><b>Fail-open y en segundo plano</b>: que la bitácora esté caída no puede
 * impedir que alguien consulte lo suyo. Cada asiento queda además en la
 * bitácora JSON (regla 6), con la misma forma y sin el contenido, así que si
 * ms-cumplimiento no lo recibe queda la evidencia local. Sin
 * {@code AUDITORIA_URL} o sin credencial de servicio, solo esa línea.
 */
@Component
public class AuditoriaDeAccesos {

    private static final Logger log = LoggerFactory.getLogger("AUDITORIA_ASISTENTE");

    static final String TIPO_ACCION = "OTRO";
    static final String MOTIVO = "CONSULTA_DEL_ASISTENTE";
    static final String REFERENCIA = "ms-chatbot:consulta-";
    static final String IP_DESCONOCIDA = "DESCONOCIDA";
    /** La columna de ms-cumplimiento admite 45 (cabe una IPv6). */
    static final int MAXIMO_IP = 45;

    private final RestClient http;
    private final String url;
    private final Clock reloj;
    private final Executor ejecutor;

    @Autowired
    public AuditoriaDeAccesos(@Value("${chatbot.auditoria.url:}") String url,
                              ObjectProvider<InterceptorDePortadorDeServicio> credencial,
                              ObjectProvider<RestClient.Builder> constructores,
                              Clock reloj) {
        this(clienteDe(url, credencial.getIfAvailable(), constructores), url, reloj,
            tarea -> Thread.ofVirtual().name("auditoria-asistente").start(tarea));
    }

    AuditoriaDeAccesos(RestClient http, String url, Clock reloj, Executor ejecutor) {
        this.http = http;
        this.url = url == null ? null : url.strip();
        this.reloj = reloj;
        this.ejecutor = ejecutor;
    }

    /** Solo la línea de la bitácora JSON: para quien no tiene bitácora central (pruebas). */
    public static AuditoriaDeAccesos soloEnLaBitacora(Clock reloj) {
        return new AuditoriaDeAccesos(null, null, reloj, Runnable::run);
    }

    private static RestClient clienteDe(String url, InterceptorDePortadorDeServicio credencial,
                                        ObjectProvider<RestClient.Builder> constructores) {
        if (url == null || url.isBlank()) {
            log.info("Sin AUDITORIA_URL: las consultas del asistente a datos personales quedan solo en esta "
                + "bitacora");
            return null;
        }
        if (credencial == null) {
            log.warn("Hay AUDITORIA_URL pero no credencial de servicio (DIRECTORIO_ACTIVO_CLIENT_ID): las consultas "
                + "del asistente a datos personales quedan solo en esta bitacora");
            return null;
        }
        // El constructor del servicio: con los tiempos de espera y el trace id (regla 5).
        return constructores.getObject().requestInterceptor(credencial).build();
    }

    /**
     * El asistente leyó (o intentó leer) un dato de la cuenta de {@code uid}.
     *
     * @param uid       el del token de quien pregunta; sin él no hay nada que auditar
     * @param dato      qué dato
     * @param resultado {@code CONSULTA_EN_VIVO} si llegó; cualquier otro, «no-disponible»
     * @param origen    la dirección del cliente (X-Real-IP del borde); puede faltar
     */
    public void registrar(String uid, DatoConsultado dato, ResultadoDeRespuesta resultado, String origen) {
        if (uid == null || uid.isBlank() || dato == null) {
            return;
        }
        String conseguido = resultado == ResultadoDeRespuesta.CONSULTA_EN_VIVO ? "consultado" : "no-disponible";
        Evento evento = new Evento(TIPO_ACCION, uid, uid, null, "dato=" + dato.clave() + ";resultado=" + conseguido,
            MOTIVO, ipDe(origen), reloj.instant().toString(), REFERENCIA + UUID.randomUUID());
        log.info("{} afectado={} dato={} resultado={}", MOTIVO, uid, dato.clave(), conseguido);
        if (http == null) {
            return;
        }
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        try {
            ejecutor.execute(() -> entregar(evento, contexto));
        } catch (RuntimeException sinHilo) {
            log.warn("No se pudo programar el envio de {} a la bitacora central: {}", MOTIVO, sinHilo.getMessage());
        }
    }

    private void entregar(Evento evento, Map<String, String> contexto) {
        if (contexto != null) {
            MDC.setContextMap(contexto);
        }
        try {
            http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(evento)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException fallo) {
            log.warn("No fue posible registrar {} en ms-cumplimiento afectado={} motivo={}", evento.motivo(),
                evento.afectado(), fallo.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private static String ipDe(String origen) {
        if (origen == null || origen.isBlank()) {
            return IP_DESCONOCIDA;
        }
        String ip = origen.strip();
        return ip.length() > MAXIMO_IP ? ip.substring(0, MAXIMO_IP) : ip;
    }

    /** El cuerpo de {@code RegistrarAuditoriaRequest} (ms-cumplimiento-auditoria.yaml 1.3.0). */
    public record Evento(String tipoAccion, String administradorId, String afectado, String valorAnterior,
                         String valorNuevo, String motivo, String ipOrigen, String ocurridoEn, String referencia) {
    }
}

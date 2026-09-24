package com.nexusbattles.ms_identidad.onboarding.auditoria;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Deja en ms-cumplimiento (auditoria, {@code POST /admin/auditoria/eventos})
 * los hitos de la vida de una cuenta de jugador: alta, primer acceso, alta
 * completada o fallida y cierre de sesion.
 *
 * <p><b>Fail-open y en segundo plano</b>, como {@code AuditoriaLoginClient}:
 * que la auditoria este caida no puede impedir que alguien se registre o
 * entre. Cada evento queda ademas en la bitacora JSON (regla 6) con la misma
 * forma, asi que si ms-cumplimiento no lo recibe queda la evidencia local.
 *
 * <p>Nunca se envia el correo ni nada de la contrasena: el afectado es el
 * uid (ADR-002) y como mucho el apodo, que ya es publico en el juego.
 */
@Component
public class AuditoriaDeCuenta {

    private static final Logger log = LoggerFactory.getLogger("AUDITORIA_CUENTA");

    static final String SISTEMA = CredencialPropia.CLIENT_ID;

    private final RestClient http;
    private final String url;
    private final Executor ejecutor;

    @Autowired
    public AuditoriaDeCuenta(
            @Value("${app.auditoria.url:http://localhost:8091/api/v1/admin/auditoria/eventos}") String url,
            CredencialPropia credencial,
            InterceptorDeTraza traza) {
        this(url, credencial, traza, tarea -> Thread.ofVirtual().name("auditoria-cuenta").start(tarea));
    }

    AuditoriaDeCuenta(String url, CredencialPropia credencial, InterceptorDeTraza traza, Executor ejecutor) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(1000);
        fabrica.setReadTimeout(1000);
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        if (credencial != null) {
            constructor.requestInterceptor(credencial);
        }
        if (traza != null) {
            constructor.requestInterceptor(traza);
        }
        this.http = constructor.build();
        this.url = url;
        this.ejecutor = ejecutor;
    }

    public void registro(UUID uid, String apodo, String ip) {
        enviar(new Evento("CREACION", texto(uid), texto(uid), null, "apodo=" + apodo + ";rol=JUGADOR",
                "REGISTRO_JUGADOR", ip));
    }

    public void primerAcceso(UUID uid, String ip) {
        enviar(new Evento("OTRO", texto(uid), texto(uid), null, null, "PRIMER_INICIO_SESION", ip));
    }

    public void cierreDeSesion(String quien, String ip) {
        enviar(new Evento("OTRO", quien, quien, null, null, "CIERRE_SESION", ip));
    }

    public void altaCompletada(UUID uid, String resumen) {
        enviar(new Evento("CREACION", SISTEMA, texto(uid), null, resumen, "ONBOARDING_COMPLETADO", null));
    }

    public void altaFallida(UUID uid, String pendientes) {
        enviar(new Evento("OTRO", SISTEMA, texto(uid), null, pendientes, "ONBOARDING_FALLIDO", null));
    }

    private void enviar(Evento evento) {
        log.info("{} afectado={} detalle={}",
                evento.motivo(), evento.afectado(), evento.valorNuevo() == null ? "" : evento.valorNuevo());
        String traceId = Traza.actual().orElse(null);
        try {
            ejecutor.execute(() -> entregar(evento, traceId));
        } catch (RuntimeException sinHilo) {
            log.warn("No se pudo programar el envio de {} a auditoria: {}", evento.motivo(), sinHilo.getMessage());
        }
    }

    private void entregar(Evento evento, String traceId) {
        if (traceId != null) {
            Traza.abrir(traceId);
        }
        try {
            http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(evento)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException fallo) {
            log.warn("No fue posible registrar {} en ms-cumplimiento afectado={} motivo={}",
                    evento.motivo(), evento.afectado(), fallo.getMessage());
        } finally {
            if (traceId != null) {
                Traza.cerrar();
            }
        }
    }

    private static String texto(UUID uid) {
        return uid == null ? "DESCONOCIDO" : uid.toString();
    }

    private static String ipDe(String ip) {
        return ip == null || ip.isBlank() ? "DESCONOCIDA" : ip;
    }

    /** Cuerpo de ms-cumplimiento; mismo orden y nombres que usan los otros clientes de auditoria. */
    public record Evento(String tipoAccion, String administradorId, String afectado, String valorAnterior,
                         String valorNuevo, String motivo, String ipOrigen) {

        public Evento {
            ipOrigen = ipDe(ipOrigen);
        }
    }
}

package com.nexusbattles.ms_identidad.auth.codigos;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoCambioClaveRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoConfirmacionCuentaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoRecuperacionClaveRequest;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Los correos y la auditoria de la vida de una cuenta, DESPUES del commit.
 *
 * <p><b>Despues</b>: antes de B1 la bienvenida salia dentro de la transaccion
 * del registro; si el registro se deshacia despues (un apodo repetido que se
 * colo, el avatar que no se pudo guardar), la persona ya tenia un correo de
 * una cuenta que no existia. Con AFTER_COMMIT, un codigo solo sale si la
 * cuenta y el codigo existen de verdad.
 *
 * <p><b>En segundo plano</b> ({@code identidad.correo.envio}, por omision
 * {@code segundo-plano}): las rutas publicas de B1 responden lo mismo exista o
 * no la cuenta, y esperar aqui a correo (o a su reintento, si esta caido)
 * haria que la respuesta tardara mas cuando la cuenta existe: el tiempo diria
 * lo que el cuerpo calla. En hilo virtual, sin esperar. {@code sincrono} es
 * para las pruebas que quieren afirmar el envio sin sondear.
 *
 * <p>Si correo no responde, el envio se pierde y queda en la bitacora
 * ({@link CorreoClient}, fail-open); la persona pide otro codigo. Nunca se
 * registra el codigo.
 */
@Component
public class CorreosDeCuenta {

    private static final Logger log = LoggerFactory.getLogger(CorreosDeCuenta.class);

    private final CorreoClient correo;
    private final AuditoriaDeCuenta auditoria;
    private final Executor ejecutor;
    private final Clock reloj;

    @Autowired
    public CorreosDeCuenta(CorreoClient correo, AuditoriaDeCuenta auditoria,
                           @Value("${identidad.correo.envio:segundo-plano}") String modo) {
        this(correo, auditoria, ejecutorPara(modo), Clock.systemDefaultZone());
    }

    CorreosDeCuenta(CorreoClient correo, AuditoriaDeCuenta auditoria, Executor ejecutor, Clock reloj) {
        this.correo = correo;
        this.auditoria = auditoria;
        this.ejecutor = ejecutor;
        this.reloj = reloj;
    }

    static Executor ejecutorPara(String modo) {
        if (modo != null && "sincrono".equals(modo.trim().toLowerCase(Locale.ROOT))) {
            return Runnable::run;
        }
        return tarea -> Thread.ofVirtual().name("correo-de-cuenta").start(tarea);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void codigoEmitido(CodigoParaEnviar codigo) {
        enviar(() -> {
            if (codigo.tipo() == TipoCodigo.RESTABLECIMIENTO) {
                correo.enviarRecuperacionClave(new CorreoRecuperacionClaveRequest(
                        codigo.email(), codigo.apodo(), codigo.codigo(), codigo.minutosVigencia()),
                        codigo.claveDeIdempotencia());
            } else {
                String proposito = codigo.tipo() == TipoCodigo.VERIFICACION
                        ? CorreoConfirmacionCuentaRequest.VERIFICACION
                        : CorreoConfirmacionCuentaRequest.ACTIVACION;
                correo.enviarConfirmacionCuenta(new CorreoConfirmacionCuentaRequest(
                        codigo.email(), codigo.apodo(), codigo.codigo(), codigo.minutosVigencia(), proposito),
                        codigo.claveDeIdempotencia());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cuentaRegistrada(CuentaRegistrada cuenta) {
        auditoria.registro(cuenta.uid(), cuenta.apodo(), cuenta.ip());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void correoVerificado(CorreoVerificado verificado) {
        auditoria.correoVerificado(verificado.uid(), verificado.ip());
        enviar(() -> correo.enviarBienvenida(new CorreoBienvenidaRequest(
                verificado.email(), verificado.apodo(), verificado.nombres(), verificado.apellidos()),
                "bienvenida-" + verificado.uid()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void contrasenaRestablecida(ContrasenaRestablecida cambio) {
        auditoria.contrasenaRestablecida(cambio.afectado(),
                cambio.tipo() == TipoCodigo.ACTIVACION ? "ACTIVACION_CUENTA" : "RESTABLECIMIENTO_CONTRASENA",
                cambio.ip());
        String momento = OffsetDateTime.now(reloj).toString();
        enviar(() -> correo.enviarCambioClave(new CorreoCambioClaveRequest(
                cambio.email(), cambio.apodo(), cambio.ip() == null ? "desconocida" : cambio.ip(), momento),
                "cambio-clave-" + cambio.codigoId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void preguntasConfiguradas(PreguntasConfiguradas cambio) {
        auditoria.preguntasConfiguradas(cambio.afectado(), cambio.cantidad(), cambio.ip());
    }

    /** Lanza el envio sin esperarlo, con la traza de quien lo pidio (regla 5). */
    private void enviar(Runnable envio) {
        String traza = Traza.actual().orElse(null);
        try {
            ejecutor.execute(() -> conTraza(traza, envio));
        } catch (RuntimeException sinHilo) {
            log.warn("No se pudo programar un correo de cuenta: {}", sinHilo.getMessage());
        }
    }

    private static void conTraza(String traza, Runnable envio) {
        // En modo sincrono el hilo ya tiene su traza (la de la peticion):
        // abrirla y cerrarla aqui la borraria para el resto de la peticion.
        boolean abrir = traza != null && Traza.actual().isEmpty();
        if (abrir) {
            Traza.abrir(traza);
        }
        try {
            envio.run();
        } catch (RuntimeException fallo) {
            log.warn("Un correo de cuenta no se pudo entregar a correo: {}", fallo.getMessage());
        } finally {
            if (abrir) {
                Traza.cerrar();
            }
        }
    }
}

package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.privacidad.CierreRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.privacidad.ConsultaDeSubastas.OperacionesAbiertas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Solicitar, consultar y cancelar el cierre de la propia cuenta — derecho al
 * olvido (HU-PRV-005, RF-PRV-005, RN-USR-011; ms-identidad-perfiles.yaml 1.4.0).
 *
 * <p><b>Por que en ms-identidad y no en ms-cumplimiento.</b> Los datos que se
 * eliminan (correo, nombres, apellidos, avatar, preguntas de seguridad,
 * dispositivos, codigos) y la contrasena que verifica la identidad viven en
 * la base de este servicio. Ponerlo en ms-cumplimiento habria obligado a
 * mandarle la contrasena por la red o a abrirle una ruta interna que
 * verificara contrasenas, y a repartir una misma operacion entre dos bases
 * sin transaccion comun. Aqui la verificacion, el plazo y la anonimizacion
 * son locales; ms-cumplimiento sigue siendo la autoridad de la auditoria y
 * recibe cada paso por su API.
 *
 * <p><b>Orden de {@link #solicitar}</b>, cada paso sin cambiar nada si falla:
 * <ol>
 *   <li>cuenta bloqueada por intentos (RF-AUT-009): ni se compara;</li>
 *   <li>contrasena actual: si no coincide, cuenta como intento fallido (se
 *       confirma aparte, REQUIRES_NEW) y 422;</li>
 *   <li>ya programado: se devuelve tal cual (idempotente; no se mueve la
 *       fecha);</li>
 *   <li>subastas activas o pujas vigentes en ms-subastas, con el token de la
 *       persona: 409; si no responde, 503 (fail-closed). Es una llamada HTTP:
 *       va FUERA de toda transaccion;</li>
 *   <li>en una transaccion corta, con la cuenta bloqueada para escritura, se
 *       vuelve a mirar si ya hay uno y se guarda. El indice unico parcial de
 *       V6 es la ultima red: si otra solicitud gano la carrera, se devuelve la
 *       suya.</li>
 * </ol>
 * La auditoria y el aviso en la bandeja van despues y son fail-open: que no
 * respondan no deshace un cierre ya programado.
 */
@Service
public class CierreDeCuentaService {

    private static final Logger log = LoggerFactory.getLogger(CierreDeCuentaService.class);

    /** Tipo de aviso de la bandeja para la vida de la cuenta (el mismo que usa la reactivacion). */
    static final String TIPO_AVISO = "CUENTA";

    private final SolicitudDeCierreRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final IntentosFallidosService intentosFallidos;
    private final ConsultaDeSubastas subastas;
    private final AuditoriaDeCuenta auditoria;
    private final NotificacionClient notificaciones;
    private final TransactionTemplate transaccion;
    private final PasswordEncoder cifrador;
    private final Clock reloj;

    /** Una solicitud atendida: el estado resultante y si se programo ahora o ya estaba. */
    public record Solicitud(EstadoDelCierre estado, boolean nueva) {
    }

    @Autowired
    public CierreDeCuentaService(SolicitudDeCierreRepository solicitudes,
                                 UsuarioRepository usuarios,
                                 IntentosFallidosService intentosFallidos,
                                 ConsultaDeSubastas subastas,
                                 AuditoriaDeCuenta auditoria,
                                 NotificacionClient notificaciones,
                                 PlatformTransactionManager transacciones) {
        this(solicitudes, usuarios, intentosFallidos, subastas, auditoria, notificaciones, transacciones,
                new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con cifrador y reloj explicitos: pruebas. */
    CierreDeCuentaService(SolicitudDeCierreRepository solicitudes,
                          UsuarioRepository usuarios,
                          IntentosFallidosService intentosFallidos,
                          ConsultaDeSubastas subastas,
                          AuditoriaDeCuenta auditoria,
                          NotificacionClient notificaciones,
                          PlatformTransactionManager transacciones,
                          PasswordEncoder cifrador,
                          Clock reloj) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.intentosFallidos = intentosFallidos;
        this.subastas = subastas;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.transaccion = new TransactionTemplate(transacciones);
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    /** Si el cierre de la cuenta esta programado y para cuando. */
    public EstadoDelCierre consultar(Usuario cuenta) {
        return estadoDe(solicitudes.programadaDe(cuenta.getPublicId()).orElse(null));
    }

    /**
     * Verifica la identidad y programa el cierre a {@value SolicitudDeCierre#PLAZO_DIAS} dias.
     *
     * @param cuenta        la cuenta del token (ya comprobado que es la de la ruta)
     * @param autorizacion  su cabecera {@code Authorization}, para preguntar a ms-subastas
     * @param ip            para la auditoria
     * @throws CierreRechazadoException con el motivo; nada cambio
     */
    public Solicitud solicitar(Usuario cuenta, String passwordActual, String autorizacion, String ip) {
        UUID uid = cuenta.getPublicId();
        LocalDateTime ahora = LocalDateTime.now(reloj);

        if (cuenta.getBloqueadoHasta() != null && ahora.isBefore(cuenta.getBloqueadoHasta())) {
            throw new CierreRechazadoException(Motivo.CUENTA_BLOQUEADA,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo más tarde.");
        }
        if (passwordActual == null || !cifrador.matches(passwordActual, cuenta.getPassword())) {
            intentosFallidos.registrarIntentoFallido(cuenta.getId());
            throw new CierreRechazadoException(Motivo.ACTUAL_INCORRECTA, "La contraseña actual es incorrecta.");
        }

        Optional<SolicitudDeCierre> existente = solicitudes.programadaDe(uid);
        if (existente.isPresent()) {
            return new Solicitud(estadoDe(existente.get()), false);
        }

        exigirSinOperacionesAbiertas(autorizacion);

        SolicitudDeCierre guardada = programarSiNoHayOtra(uid, ahora);
        if (guardada == null) {
            // Otra solicitud de la misma persona la programo entre medias.
            return new Solicitud(estadoDe(solicitudes.programadaDe(uid).orElse(null)), false);
        }

        LocalDateTime programadoPara = guardada.getProgramadaPara();
        log.info("CIERRE_CUENTA_SOLICITADO uid={} programadoPara={}", uid, programadoPara);
        avisar(() -> auditoria.cierreDeCuentaSolicitado(uid, programadoPara, ip), "auditoria", uid);
        avisar(() -> notificaciones.emitir(uid.toString(), TIPO_AVISO, "Programaste el cierre de tu cuenta",
                "Tu cuenta y tus datos personales se eliminarán en " + SolicitudDeCierre.PLAZO_DIAS
                        + " días. Hasta entonces puedes cancelarlo desde Mi cuenta, en Privacidad."),
                "bandeja", uid);
        return new Solicitud(estadoDe(guardada), true);
    }

    /**
     * En una transaccion corta, con la cuenta bloqueada para escritura: si
     * sigue sin haber un cierre programado, lo guarda. {@code null} si otra
     * solicitud se adelanto (la vio aqui dentro o choco con el indice unico).
     */
    private SolicitudDeCierre programarSiNoHayOtra(UUID uid, LocalDateTime ahora) {
        try {
            return transaccion.execute(estado -> {
                usuarios.bloquearPorIdentificadorPublico(uid);
                if (solicitudes.programadaDe(uid).isPresent()) {
                    return null;
                }
                return solicitudes.save(SolicitudDeCierre.programar(uid, ahora));
            });
        } catch (DataIntegrityViolationException otraGano) {
            return null;
        }
    }

    /**
     * Cancela el cierre programado, si lo hay. Idempotente: sin nada
     * programado responde igual, sin escribir.
     */
    public EstadoDelCierre cancelar(Usuario cuenta, String ip) {
        UUID uid = cuenta.getPublicId();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Boolean cancelada = transaccion.execute(estado -> {
            usuarios.bloquearPorIdentificadorPublico(uid);
            Optional<SolicitudDeCierre> programada = solicitudes.programadaDe(uid);
            if (programada.isEmpty()) {
                return false;
            }
            programada.get().cancelar(ahora);
            solicitudes.save(programada.get());
            return true;
        });
        if (Boolean.TRUE.equals(cancelada)) {
            log.info("CIERRE_CUENTA_CANCELADO uid={}", uid);
            avisar(() -> auditoria.cierreDeCuentaCancelado(uid, ip), "auditoria", uid);
            avisar(() -> notificaciones.emitir(uid.toString(), TIPO_AVISO, "Cancelaste el cierre de tu cuenta",
                    "Tu cuenta sigue activa y no se eliminará ningún dato."), "bandeja", uid);
        }
        return EstadoDelCierre.sinSolicitud();
    }

    private void exigirSinOperacionesAbiertas(String autorizacion) {
        OperacionesAbiertas abiertas;
        try {
            abiertas = subastas.delJugador(autorizacion);
        } catch (SubastasNoDisponiblesException noSeSabe) {
            log.warn("Cierre de cuenta rechazado: no se pudo consultar ms-subastas ({})", noSeSabe.getMessage());
            throw new CierreRechazadoException(Motivo.SUBASTAS_NO_DISPONIBLES,
                    "No pudimos comprobar si tienes subastas o pujas abiertas, así que no se programó nada. "
                            + "Inténtalo de nuevo en unos minutos.");
        }
        if (abiertas.hay()) {
            throw new CierreRechazadoException(Motivo.OPERACIONES_PENDIENTES,
                    "Tienes subastas activas o pujas vigentes. Espera a que terminen (o cancela tus "
                            + "publicaciones) antes de cerrar tu cuenta.",
                    abiertas.subastasActivas(), abiertas.pujasVigentes());
        }
    }

    private EstadoDelCierre estadoDe(SolicitudDeCierre solicitud) {
        return EstadoDelCierre.de(solicitud, reloj.getZone());
    }

    /** Auditoria y bandeja: fail-open, nunca deshacen lo ya hecho. */
    private static void avisar(Runnable aviso, String que, UUID uid) {
        try {
            aviso.run();
        } catch (RuntimeException caido) {
            log.warn("Cierre de cuenta de {}: no se pudo avisar a {} ({})", uid, que, caido.getMessage());
        }
    }
}

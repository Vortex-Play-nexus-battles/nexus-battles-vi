package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorService;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.JerarquiaDeRoles;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.Sancion;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.SancionActiva;
import com.nexusbattles.ms_identidad.sanciones.ProyeccionDeSancionService;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Gestion de cuentas desde el panel (7.3.2, 7.3.4).
 *
 * <p><b>B2 — sanciones unificadas.</b> Suspender, banear y reactivar ya no
 * cambian la cuenta por su cuenta: delegan en moderacion-sanciones, que es la
 * fuente de verdad (historial, autor, motivo, apelaciones, avisos al jugador).
 * La peticion viaja con el token del administrador que actua, para que
 * moderacion aplique su regla de roles y lo anote como autor. Con su
 * respuesta se aplica aqui la proyeccion (la misma que moderacion enviara por
 * la ruta interna, que entonces no cambia nada) y el login ya la ve.
 *
 * <ul>
 *   <li>Si moderacion no responde: 503 y NO se aplica nada aqui. Divergir en
 *       silencio —suspender aqui sin sancion alli— es justo lo que esto evita.</li>
 *   <li>Si moderacion dice que no (4xx): se reenvia su respuesta.</li>
 *   <li>La auditoria de ms-cumplimiento se intenta despues y, si falla, queda
 *       en la bitacora sin deshacer la sancion: el registro de quien, cuando y
 *       por que ya esta en el historial de moderacion, y deshacer la
 *       proyeccion aqui dejaria la sancion vigente alli y la cuenta entrando
 *       aqui.</li>
 *   <li>Una cuenta sin {@code uid} (anterior al identificador publico) no
 *       existe para moderacion: se opera solo aqui, como antes de B2, con la
 *       auditoria fail-closed de HU-AUD-001.</li>
 * </ul>
 *
 * <p><b>Jerarquia (7.3.1).</b> Cada accion mira el rol de la cuenta afectada
 * ({@link JerarquiaDeRoles}): sancionar o cambiar el estado solo sobre un rol
 * inferior; editar el perfil o restablecer credenciales sobre un rol inferior,
 * o un Super Administrador sobre otro. Levantar una suspension es de
 * administrador (la Tabla 24 deja al moderador solo la suspension temporal).
 *
 * <p><b>Avisos y auditoria por {@code uid}.</b> Las bandejas de notificaciones
 * van por {@code uid} (notificaciones.yaml); hasta este cambio se mandaban con
 * la clave interna y no le llegaban a nadie. La cuenta afectada de cada
 * registro de auditoria tambien es su {@code uid}, el mismo con que la ficha
 * administrativa reune su historial; la clave interna queda solo para las
 * cuentas anteriores al uid.
 */
@Service
public class AdminGestionUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(AdminGestionUsuarioService.class);

    static final String MOTIVO_SUSPENSION = "Suspensión desde el panel de administración";
    static final String MOTIVO_BANEO = "Baneo definitivo desde el panel de administración";
    static final String MOTIVO_REACTIVACION = "Reactivación desde el panel de administración";
    static final String BANEO_IRREVERSIBLE = "No se puede reactivar una cuenta baneada definitivamente.";

    private final AuthAdminService authAdminService;
    private final PerfilUsuarioService perfilUsuarioService;
    private final AuditoriaClient auditoriaClient;
    private final NotificacionClient notificacionClient;
    private final UsuarioRepository usuarios;
    private final ModeracionSancionesClient moderacion;
    private final ProyeccionDeSancionService proyecciones;
    private final SegundoFactorService segundoFactor;
    private final Clock reloj;

    @Autowired
    public AdminGestionUsuarioService(AuthAdminService authAdminService,
                                      PerfilUsuarioService perfilUsuarioService,
                                      AuditoriaClient auditoriaClient,
                                      NotificacionClient notificacionClient,
                                      UsuarioRepository usuarios,
                                      ModeracionSancionesClient moderacion,
                                      ProyeccionDeSancionService proyecciones,
                                      SegundoFactorService segundoFactor) {
        this(authAdminService, perfilUsuarioService, auditoriaClient, notificacionClient, usuarios, moderacion,
                proyecciones, segundoFactor, Clock.systemDefaultZone());
    }

    public AdminGestionUsuarioService(AuthAdminService authAdminService,
                                      PerfilUsuarioService perfilUsuarioService,
                                      AuditoriaClient auditoriaClient,
                                      NotificacionClient notificacionClient,
                                      UsuarioRepository usuarios,
                                      ModeracionSancionesClient moderacion,
                                      ProyeccionDeSancionService proyecciones,
                                      SegundoFactorService segundoFactor,
                                      Clock reloj) {
        this.authAdminService = authAdminService;
        this.perfilUsuarioService = perfilUsuarioService;
        this.auditoriaClient = auditoriaClient;
        this.notificacionClient = notificacionClient;
        this.usuarios = usuarios;
        this.moderacion = moderacion;
        this.proyecciones = proyecciones;
        this.segundoFactor = segundoFactor;
        this.reloj = reloj;
    }

    public PerfilUsuario obtenerUsuarioParaGestion(Long usuarioId) {
        return perfilUsuarioService.obtenerPorUsuarioId(usuarioId);
    }

    @Transactional
    public PerfilUsuario editarPerfilDeUsuario(Long usuarioId, String nombres, String apellidos,
                                               MultipartFile nuevoAvatar, String preferencias,
                                               String nuevoApodo, String administradorId, String rolActor,
                                               String ipOrigen) {
        Usuario usuario = cuenta(usuarioId);
        exigirGestion(rolActor, usuario, "editar el perfil de");
        PerfilUsuario actualizado = perfilUsuarioService.actualizarPerfilPropio(
            usuarioId, nombres, apellidos, nuevoAvatar, preferencias, nuevoApodo
        );

        auditoriaClient.registrar(
            "ACTUALIZACION", administradorId, destinatario(usuario),
            null, "nombres=" + nombres + ", apellidos=" + apellidos,
            "Edición administrativa de perfil", ipOrigen
        );

        // Aviso al usuario afectado (HU-USR-003). Fail-open: si notificaciones
        // no responde, la operación ya está hecha y no se revierte.
        notificacionClient.emitir(
            destinatario(usuario),
            "CUENTA",
            "Tu perfil fue actualizado",
            "Un administrador actualizó la información de tu perfil."
        );

        return actualizado;
    }

    /**
     * Suspension temporal (7.3.2, sancion 2). Con {@code uid}: {@code POST
     * /sanciones} tipo SUSPENSION con la duracion en horas hasta
     * {@code suspendidoHasta} (redondeada hacia arriba: la suspension no
     * termina antes de lo que pidio quien la impuso).
     *
     * @param motivo       causal documentada; si falta, {@value #MOTIVO_SUSPENSION}
     * @param autorizacion la cabecera {@code Authorization} de quien actua
     */
    @Transactional
    public void suspenderCuenta(Long usuarioId, LocalDateTime suspendidoHasta, String motivo, String autorizacion,
                                String administradorId, String rolActor, String ipOrigen) {
        suspenderCuenta(usuarioId, suspendidoHasta, motivo, null, null, autorizacion, administradorId, rolActor,
                ipOrigen);
    }

    /**
     * Como {@link #suspenderCuenta(Long, LocalDateTime, String, String, String, String, String)}
     * con la {@code causal} de 7.3.2 y la {@code politica} violada (SA-4), que
     * moderacion guarda con la sancion y lleva al aviso y al correo (7.3.7).
     */
    @Transactional
    public void suspenderCuenta(Long usuarioId, LocalDateTime suspendidoHasta, String motivo, String causal,
                                String politica, String autorizacion, String administradorId, String rolActor,
                                String ipOrigen) {
        Usuario usuario = cuenta(usuarioId);
        exigirSancion(rolActor, usuario, "suspender a");
        String estadoAnterior = EstadoCuenta.normalizado(usuario.getEstado());

        if (usuario.getPublicId() == null) {
            // HU-AUD-001 (fail-closed): el cambio de estado y la auditoría van en
            // la MISMA transacción; si la auditoría falla, se deshace.
            authAdminService.actualizarEstadoCuenta(usuarioId, EstadoCuenta.SUSPENDIDO, suspendidoHasta);
            auditoriaClient.registrar("SUSPENSION", administradorId, destinatario(usuario),
                estadoAnterior, "SUSPENDIDO hasta " + suspendidoHasta, "Suspensión de cuenta", ipOrigen);
            notificacionClient.emitir(destinatario(usuario), "SANCION", "Tu cuenta fue suspendida",
                "Tu cuenta ha sido suspendida hasta " + suspendidoHasta + ".");
            return;
        }

        exigirCredencial(autorizacion);
        Sancion sancion = moderacion.emitir(autorizacion, usuario.getPublicId(), "SUSPENSION", vacioANulo(causal),
                motivoOPorOmision(motivo, MOTIVO_SUSPENSION), vacioANulo(politica), horasHasta(suspendidoHasta), null);
        OffsetDateTime fin = sancion.vigenteHasta() != null
                ? sancion.vigenteHasta()
                : suspendidoHasta.atZone(reloj.getZone()).toOffsetDateTime();
        proyecciones.aplicar(usuario, EstadoCuenta.SUSPENDIDO, fin, sancion.id());

        auditarSinFrenar("SUSPENSION", administradorId, usuario, estadoAnterior,
                "SUSPENDIDO hasta " + fin + " (sancion " + sancion.id() + ")",
                "Suspensión de cuenta (moderacion-sanciones)", ipOrigen);
    }

    /** Baneo definitivo (7.3.2, sancion 3): {@code POST /sanciones} tipo BANEO con {@code confirmacion: true}. */
    @Transactional
    public void banearCuenta(Long usuarioId, String motivo, String autorizacion, String administradorId,
                             String rolActor, String ipOrigen) {
        banearCuenta(usuarioId, motivo, null, null, autorizacion, administradorId, rolActor, ipOrigen);
    }

    /** Como {@link #banearCuenta(Long, String, String, String, String, String)} con causal y politica (SA-4). */
    @Transactional
    public void banearCuenta(Long usuarioId, String motivo, String causal, String politica, String autorizacion,
                             String administradorId, String rolActor, String ipOrigen) {
        Usuario usuario = cuenta(usuarioId);
        exigirSancion(rolActor, usuario, "banear a");
        String estadoAnterior = EstadoCuenta.normalizado(usuario.getEstado());

        if (usuario.getPublicId() == null) {
            authAdminService.actualizarEstadoCuenta(usuarioId, EstadoCuenta.BANEADO, null);
            auditoriaClient.registrar("SANCION", administradorId, destinatario(usuario),
                estadoAnterior, EstadoCuenta.BANEADO, "Baneo definitivo de cuenta", ipOrigen);
            notificacionClient.emitir(destinatario(usuario), "SANCION", "Tu cuenta fue baneada",
                "Tu cuenta ha sido baneada de forma definitiva.");
            return;
        }

        exigirCredencial(autorizacion);
        Sancion sancion = moderacion.emitir(autorizacion, usuario.getPublicId(), "BANEO", vacioANulo(causal),
                motivoOPorOmision(motivo, MOTIVO_BANEO), vacioANulo(politica), null, Boolean.TRUE);
        proyecciones.aplicar(usuario, EstadoCuenta.BANEADO, null, sancion.id());

        auditarSinFrenar("SANCION", administradorId, usuario, estadoAnterior,
                EstadoCuenta.BANEADO + " (sancion " + sancion.id() + ")",
                "Baneo definitivo de cuenta (moderacion-sanciones)", ipOrigen);
    }

    /**
     * Levanta la suspension vigente. Un baneo no se levanta desde aqui (7.3.2:
     * «inhabilita permanentemente»; solo lo revierte una apelacion). Con
     * {@code uid}: se pregunta a moderacion por la sancion activa (credencial
     * de servicio) y se levanta con el token de quien actua; sin sancion activa
     * alli, se limpia la proyeccion que hubiera quedado aqui.
     */
    @Transactional
    public void reactivarCuenta(Long usuarioId, String autorizacion, String administradorId, String rolActor,
                                String ipOrigen) {
        if (JerarquiaDeRoles.rango(rolActor) < JerarquiaDeRoles.ADMINISTRADOR) {
            throw new JerarquiaInsuficienteException(
                    "Levantar una suspensión es de administrador: el moderador solo impone suspensiones temporales.");
        }
        Usuario usuario = cuenta(usuarioId);
        exigirSancion(rolActor, usuario, "reactivar a");
        String estadoAnterior = EstadoCuenta.normalizado(usuario.getEstado());
        if (EstadoCuenta.esBaneado(estadoAnterior)) {
            throw new IllegalArgumentException(BANEO_IRREVERSIBLE);
        }

        if (usuario.getPublicId() == null) {
            authAdminService.actualizarEstadoCuenta(usuarioId, EstadoCuenta.ACTIVO, null);
            auditoriaClient.registrar("ACTUALIZACION", administradorId, destinatario(usuario),
                estadoAnterior, EstadoCuenta.ACTIVO, "Reactivación de cuenta", ipOrigen);
            notificacionClient.emitir(destinatario(usuario), "CUENTA", "Tu cuenta fue reactivada",
                "Tu cuenta ha sido reactivada y ya puedes volver a acceder.");
            return;
        }

        exigirCredencial(autorizacion);
        SancionActiva activa = moderacion.activa(usuario.getPublicId());
        if (activa.sancionActiva()) {
            if ("BANEO".equals(activa.tipo())) {
                throw new IllegalArgumentException(BANEO_IRREVERSIBLE);
            }
            try {
                moderacion.levantar(autorizacion, activa.sancionId(), MOTIVO_REACTIVACION);
            } catch (SancionRechazadaException yaNoVigente) {
                // 409: entre la consulta y el levantamiento dejo de estar
                // vigente. No queda nada que levantar alli; se sigue.
                if (yaNoVigente.getEstado() != 409) {
                    throw yaNoVigente;
                }
            }
        }
        proyecciones.levantar(usuario);

        auditarSinFrenar("ACTUALIZACION", administradorId, usuario, estadoAnterior,
                EstadoCuenta.normalizado(usuario.getEstado()),
                "Reactivación de cuenta (moderacion-sanciones)", ipOrigen);
    }

    @Transactional
    public void restablecerPassword(Long usuarioId, String administradorId, String rolActor, String ipOrigen) {
        Usuario usuario = cuenta(usuarioId);
        exigirGestion(rolActor, usuario, "restablecer la contraseña de");
        authAdminService.restablecerContrasena(usuarioId);

        auditoriaClient.registrar(
            "OTRO", administradorId, destinatario(usuario),
            null, null,
            "Restablecimiento de contraseña (código de un solo uso enviado)", ipOrigen
        );

        notificacionClient.emitir(
            destinatario(usuario),
            "CUENTA",
            "Se restableció tu contraseña",
            "Un administrador restableció tu contraseña. Revisa tu correo para establecer una nueva."
        );
    }

    /**
     * Quita el segundo factor de otra cuenta (7.3.9): la persona perdio su
     * telefono y sus codigos de recuperacion. Solo el Super Administrador, nunca
     * sobre si mismo (la propia se gestiona desde Mi cuenta), con auditoria
     * fail-closed y aviso a la cuenta. Las sesiones abiertas se cierran; si el
     * segundo factor es obligatorio para su rol, el siguiente inicio de sesion
     * le pide enrolarse de nuevo.
     */
    @Transactional
    public void restablecerSegundoFactor(Long usuarioId, String motivo, String administradorId, String uidActor,
                                         String rolActor, String ipOrigen) {
        Usuario usuario = cuenta(usuarioId);
        if (JerarquiaDeRoles.rango(rolActor) != JerarquiaDeRoles.SUPER_ADMINISTRADOR) {
            throw new JerarquiaInsuficienteException("Restablecer el segundo factor de otra cuenta es del Super Administrador.");
        }
        if (usuario.getPublicId() != null && usuario.getPublicId().toString().equals(uidActor)) {
            throw new JerarquiaInsuficienteException(
                    "Tu propio segundo factor se gestiona desde Mi cuenta, con tus códigos de recuperación.");
        }
        exigirGestion(rolActor, usuario, "restablecer el segundo factor de");
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Indica el motivo del restablecimiento.");
        }
        boolean teniaActivo = segundoFactor.restablecerPorAdministracion(usuario, ipOrigen);
        // Las sesiones abiertas se cierran: el token decia que paso el segundo
        // factor que ya no existe.
        usuario.setVersionToken(usuario.getVersionToken() + 1);
        usuarios.save(usuario);

        auditoriaClient.registrar(
            "ACTUALIZACION", administradorId, destinatario(usuario),
            teniaActivo ? "segundo-factor=ACTIVO" : "segundo-factor=SIN_ACTIVAR", "segundo-factor=RESTABLECIDO",
            motivo.strip(), ipOrigen
        );

        notificacionClient.emitir(
            destinatario(usuario),
            "CUENTA",
            "Se restableció tu verificación en dos pasos",
            "Un administrador restableció tu verificación en dos pasos. La próxima vez que entres tendrás que "
                + "configurarla otra vez con tu aplicación de autenticación."
        );
    }

    private Usuario cuenta(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
    }

    /** La bandeja de la cuenta: su {@code uid}; la clave interna solo para cuentas anteriores al uid. */
    static String destinatario(Usuario usuario) {
        return usuario.getPublicId() != null ? usuario.getPublicId().toString() : String.valueOf(usuario.getId());
    }

    private static String rolDe(Usuario usuario) {
        return usuario.getRol() == null ? null : usuario.getRol().getNombre();
    }

    private static void exigirSancion(String rolActor, Usuario objetivo, String que) {
        if (!JerarquiaDeRoles.puedeSancionar(rolActor, rolDe(objetivo))) {
            throw new JerarquiaInsuficienteException("No puedes " + que + " una cuenta con un rol igual o superior "
                    + "al tuyo. Si hace falta, un Super Administrador puede cambiarle el rol primero.");
        }
    }

    private static void exigirGestion(String rolActor, Usuario objetivo, String que) {
        if (!JerarquiaDeRoles.puedeGestionar(rolActor, rolDe(objetivo))) {
            throw new JerarquiaInsuficienteException("No puedes " + que + " una cuenta con un rol igual o superior al tuyo.");
        }
    }

    /**
     * Sin token de sesion no se puede actuar en nombre de nadie ante
     * moderacion (p. ej. con el respaldo de desarrollo por cabecera): se
     * rechaza aqui en vez de enviarle una peticion anonima.
     */
    private static void exigirCredencial(String autorizacion) {
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            throw new SancionRechazadaException(403,
                    "Para sancionar hace falta una sesión con token: moderación registra quién actúa.");
        }
    }

    /** Horas enteras desde ahora hasta {@code hasta}, hacia arriba y como minimo una. */
    long horasHasta(LocalDateTime hasta) {
        if (hasta == null) {
            throw new IllegalArgumentException("Falta la fecha de fin de la suspensión.");
        }
        // Instantes, no horas de pared: el fin se lee en la zona del reloj
        // (la misma con la que se guardo) y un cambio de horario no altera
        // cuanto dura la suspension.
        long minutos = Duration.between(reloj.instant(), hasta.atZone(reloj.getZone()).toInstant()).toMinutes();
        if (minutos <= 0) {
            throw new IllegalArgumentException("La fecha de fin de la suspensión debe ser futura.");
        }
        return Math.max(1, (minutos + 59) / 60);
    }

    private static String motivoOPorOmision(String motivo, String porOmision) {
        return motivo == null || motivo.isBlank() ? porOmision : motivo.strip();
    }

    /** Un texto opcional sin espacios sobrantes; en blanco cuenta como ausente. */
    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private void auditarSinFrenar(String tipo, String administradorId, Usuario usuario, String anterior,
                                  String nuevo, String motivo, String ipOrigen) {
        try {
            auditoriaClient.registrar(tipo, administradorId, destinatario(usuario), anterior, nuevo, motivo,
                    ipOrigen);
        } catch (RuntimeException bitacoraNoDisponible) {
            log.warn("La sancion de la cuenta {} ya esta aplicada en moderacion-sanciones pero no se pudo auditar"
                    + " en ms-cumplimiento: {}", usuario.getId(), bitacoraNoDisponible.getMessage());
        }
    }
}

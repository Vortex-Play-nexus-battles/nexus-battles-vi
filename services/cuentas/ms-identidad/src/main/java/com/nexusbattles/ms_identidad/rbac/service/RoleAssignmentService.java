package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.admin.service.JerarquiaInsuficienteException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Cambio de rol de una cuenta (7.3.2: «roles y permisos asignados, solo
 * Super Administradores»). La ruta ya exige {@code ASIGNAR_ROL}, que solo tiene
 * el Super Administrador.
 *
 * <ul>
 *   <li>Nunca deja el sistema sin Super Administrador (409).</li>
 *   <li>Nadie cambia su propio rol: un Super Administrador que se degrada por
 *       error pierde la capacidad de deshacerlo.</li>
 *   <li>Auditoria fail-closed ANTES del cambio, con el motivo que escribio
 *       quien actua (7.3.5) y la cuenta por su {@code uid}.</li>
 *   <li>La version del token sube (las sesiones abiertas con el rol anterior
 *       dejan de valer) y la cuenta recibe un aviso en su bandeja.</li>
 * </ul>
 */
@Service
public class RoleAssignmentService {

    private final AuthAdminService authAdminService;
    private final AuditoriaCambioRolClient auditoriaCambioRolClient;
    private final UsuarioRepository usuarios;
    private final NotificacionClient notificaciones;

    /** Sin repositorio ni avisos: lo usan las pruebas que solo miran la regla y la auditoria. */
    public RoleAssignmentService(
        AuthAdminService authAdminService,
        AuditoriaCambioRolClient auditoriaCambioRolClient) {
        this(authAdminService, auditoriaCambioRolClient, null, null);
    }

    @Autowired
    public RoleAssignmentService(
        AuthAdminService authAdminService,
        AuditoriaCambioRolClient auditoriaCambioRolClient,
        UsuarioRepository usuarios,
        NotificacionClient notificaciones) {

        this.authAdminService = authAdminService;
        this.auditoriaCambioRolClient = auditoriaCambioRolClient;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public void asignarRol(Long usuarioId, Role nuevoRol, String administradorId, String ipOrigen) {
        asignarRol(usuarioId, nuevoRol, administradorId, ipOrigen, null, null);
    }

    /**
     * @param motivo   justificacion que queda en la auditoria (opcional en la API; la consola la pide)
     * @param uidActor {@code uid} de quien actua, para impedir que cambie su propio rol
     */
    @Transactional
    public void asignarRol(Long usuarioId, Role nuevoRol, String administradorId, String ipOrigen, String motivo,
                           String uidActor) {

        Role rolActual = authAdminService.obtenerRolDeUsuario(usuarioId);
        Optional<Usuario> cuenta = usuarios == null ? Optional.empty() : usuarios.findById(usuarioId);

        if (uidActor != null && cuenta.map(Usuario::getPublicId).map(Object::toString)
                .filter(uidActor::equals).isPresent()) {
            throw new JerarquiaInsuficienteException(
                "No puedes cambiar tu propio rol. Pídeselo a otro Super Administrador.");
        }

        // HU-RBAC-003: no dejar el sistema sin ningún Super Administrador.
        if (rolActual == Role.SUPER_ADMINISTRADOR && nuevoRol != Role.SUPER_ADMINISTRADOR) {
            long totalSuperAdministradores = authAdminService.contarPorRol(Role.SUPER_ADMINISTRADOR);
            if (totalSuperAdministradores <= 1) {
                throw new UltimoSuperAdministradorException();
            }
        }

        // Auditoría fail-closed: primero CAMBIO_ROL; solo si ms-cumplimiento
        // lo registra se modifica la cuenta.
        if (cuenta.isPresent() && cuenta.get().getPublicId() != null) {
            auditoriaCambioRolClient.registrarCambioRol(administradorId, cuenta.get().getPublicId().toString(),
                rolActual, nuevoRol, motivo, ipOrigen);
        } else {
            auditoriaCambioRolClient.registrarCambioRol(administradorId, usuarioId, rolActual, nuevoRol, ipOrigen);
        }

        // Sube la version del token: el rol anterior deja de valer en todas
        // las sesiones abiertas (HU-RBAC-003).
        authAdminService.actualizarRol(usuarioId, nuevoRol);

        if (notificaciones != null && rolActual != nuevoRol) {
            cuenta.map(Usuario::getPublicId).ifPresent(uid -> notificaciones.emitir(
                uid.toString(),
                "CUENTA",
                "Tu rol en Nexus Battles VI cambió",
                "Tu cuenta pasó de " + rolActual.getDisplayName() + " a " + nuevoRol.getDisplayName()
                    + ". Vuelve a iniciar sesión para continuar con tus nuevos permisos."));
        }
    }
}

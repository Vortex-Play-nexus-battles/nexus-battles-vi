package com.nexusbattles.ms_identidad.admin.ficha;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;

import com.nexusbattles.ms_identidad.admin.dto.MomentosDelServidor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * HU-USR-010 — lo que ms-identidad publica de una cuenta para su ficha
 * administrativa ({@code FichaAdministrativa} de ms-identidad-admin.yaml 1.4.0).
 *
 * <p>Como la fila del directorio, lo que NO lleva importa tanto como lo que
 * lleva: ni contrasena, ni su hash, ni la version de token. El resto de la
 * ficha (sanciones, comentarios...) no se replica aqui: lo publica el servicio
 * dueno de cada dato y la vista lo pide con el {@code uid}.
 *
 * @param id              clave interna de la cuenta
 * @param uid             identificador publico (nulo en cuentas anteriores a el)
 * @param apodo           nombre visible en el juego
 * @param email           correo de contacto
 * @param nombres         del perfil; nulo si la cuenta no tiene perfil
 * @param apellidos       del perfil
 * @param avatar          del perfil
 * @param rol             nombre del rol, o nulo si la cuenta no tiene
 * @param estado          con el nombre del contrato ({@link EstadoCuenta#normalizado})
 * @param suspendidoHasta fin de la suspension vigente, si la hay (SA-21: con su zona)
 * @param bloqueada       bloqueo temporal por intentos fallidos, ahora mismo
 * @param creadoEn        registro de la cuenta (SA-21: con su zona)
 * @param ultimoAcceso    ultima entrada correcta; nulo = nunca ha entrado (SA-21: con su zona)
 * @param accesoAuditado  si esta consulta quedo registrada en ms-cumplimiento
 */
public record FichaAdministrativa(
        Long id,
        UUID uid,
        String apodo,
        String email,
        String nombres,
        String apellidos,
        String avatar,
        String rol,
        String estado,
        OffsetDateTime suspendidoHasta,
        boolean bloqueada,
        OffsetDateTime creadoEn,
        OffsetDateTime ultimoAcceso,
        boolean accesoAuditado) {

    /**
     * @param cuenta         la cuenta consultada
     * @param perfil         su perfil, o {@code null} si no tiene (cuentas administrativas)
     * @param accesoAuditado si la consulta quedo en la auditoria
     * @param ahora          para decidir si el bloqueo por intentos sigue vigente
     * @param zona           la del reloj del servicio, en la que se escribieron las marcas
     */
    static FichaAdministrativa desde(Usuario cuenta, PerfilUsuario perfil, boolean accesoAuditado,
                                     LocalDateTime ahora, ZoneId zona) {
        boolean bloqueada = cuenta.getBloqueadoHasta() != null && ahora.isBefore(cuenta.getBloqueadoHasta());
        return new FichaAdministrativa(
                cuenta.getId(),
                cuenta.getPublicId(),
                cuenta.getApodo(),
                cuenta.getEmail(),
                perfil != null ? perfil.getNombres() : null,
                perfil != null ? perfil.getApellidos() : null,
                perfil != null ? perfil.getAvatar() : null,
                cuenta.getRol() != null ? cuenta.getRol().getNombre() : null,
                EstadoCuenta.normalizado(cuenta.getEstado()),
                MomentosDelServidor.conZona(cuenta.getSuspendidoHasta(), zona),
                bloqueada,
                MomentosDelServidor.conZona(cuenta.getCreadoEn(), zona),
                MomentosDelServidor.conZona(cuenta.getUltimoAcceso(), zona),
                accesoAuditado);
    }
}

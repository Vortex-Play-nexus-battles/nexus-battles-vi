package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.auth.model.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una fila del directorio administrativo de jugadores.
 *
 * Lo que NO lleva es tan importante como lo que lleva: ni contrasena, ni su
 * hash, ni la version de token, ni nada que sirva para suplantar a nadie. Un
 * administrador necesita saber quien es cada cuenta y si puede entrar, no
 * necesita sus credenciales.
 *
 * El correo si aparece: es el identificador con el que un jugador escribe
 * para pedir ayuda, y sin el la tabla no sirve para atender un reporte. Solo
 * lo ve quien tiene permiso de gestion de cuentas.
 *
 * @param uid           identificador publico, el que usan los demas servicios
 * @param apodo         nombre visible en el juego
 * @param email         correo de contacto de la cuenta
 * @param estado        ACTIVO, INACTIVO, SUSPENDIDO...
 * @param rol           JUGADOR, MODERADOR, ADMINISTRADOR, SUPER_ADMINISTRADOR
 * @param creadoEn      cuando se registro; nulo en cuentas anteriores al campo
 * @param ultimoAcceso  ultima entrada correcta; nulo = nunca ha entrado
 * @param suspendidoHasta fin de la suspension vigente, si la hay
 * @param bloqueada     bloqueo temporal por intentos fallidos, ahora mismo
 */
public record AdminUsuarioDirectorioResponse(
        UUID uid,
        String apodo,
        String email,
        String estado,
        String rol,
        LocalDateTime creadoEn,
        LocalDateTime ultimoAcceso,
        LocalDateTime suspendidoHasta,
        boolean bloqueada) {

    public static AdminUsuarioDirectorioResponse desde(Usuario usuario) {
        boolean bloqueada = usuario.getBloqueadoHasta() != null
                && LocalDateTime.now().isBefore(usuario.getBloqueadoHasta());
        return new AdminUsuarioDirectorioResponse(
                usuario.getPublicId(),
                usuario.getApodo(),
                usuario.getEmail(),
                usuario.getEstado(),
                usuario.getRol() != null ? usuario.getRol().getNombre() : null,
                usuario.getCreadoEn(),
                usuario.getUltimoAcceso(),
                usuario.getSuspendidoHasta(),
                bloqueada);
    }
}

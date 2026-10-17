package com.nexusbattles.ms_identidad.rbac.model;

import java.util.Locale;

/**
 * Quien puede actuar sobre quien (7.3.1): la matriz de permisos dice QUE puede
 * hacer cada rol; esto dice SOBRE QUIEN.
 *
 * <p>Sin esta regla, la matriz sola dejaba que un Administrador baneara a un
 * Super Administrador (incluso al ultimo) o que un Moderador suspendiera a un
 * Administrador: la accion estaba permitida y nadie miraba el rol de la cuenta
 * afectada. La jerarquia es la de 7.3.1: Jugador &lt; Moderador &lt;
 * Administrador &lt; Super Administrador.
 *
 * <ul>
 *   <li><b>Sancionar o cambiar el estado</b> (advertir, suspender, banear,
 *       levantar): solo sobre un rol estrictamente inferior. Para sancionar a
 *       una cuenta administrativa, primero un Super Administrador le cambia el
 *       rol, y ese cambio queda auditado.</li>
 *   <li><b>Gestionar la cuenta</b> (editar perfil, restablecer contrasena o el
 *       segundo factor): sobre un rol inferior, o un Super Administrador sobre
 *       otro Super Administrador ("la gestion de otros administradores").</li>
 * </ul>
 */
public final class JerarquiaDeRoles {

    public static final int SIN_ROL = -1;
    public static final int JUGADOR = 0;
    public static final int MODERADOR = 1;
    public static final int ADMINISTRADOR = 2;
    public static final int SUPER_ADMINISTRADOR = 3;

    private JerarquiaDeRoles() {
    }

    /** Rango del rol; {@link #SIN_ROL} si no es uno de los cuatro. */
    public static int rango(String rol) {
        if (rol == null || rol.isBlank()) {
            return SIN_ROL;
        }
        return switch (rol.strip().toUpperCase(Locale.ROOT)) {
            case "JUGADOR" -> JUGADOR;
            case "MODERADOR" -> MODERADOR;
            case "ADMINISTRADOR" -> ADMINISTRADOR;
            case "SUPER_ADMINISTRADOR" -> SUPER_ADMINISTRADOR;
            default -> SIN_ROL;
        };
    }

    /** Moderador, Administrador o Super Administrador. */
    public static boolean esAdministrativo(String rol) {
        return rango(rol) >= MODERADOR;
    }

    /** Advertir, suspender, banear o levantar: solo sobre un rol inferior. */
    public static boolean puedeSancionar(String rolActor, String rolObjetivo) {
        int actor = rango(rolActor);
        return actor >= MODERADOR && actor > rango(rolObjetivo);
    }

    /** Editar el perfil o restablecer credenciales: sobre un rol inferior, o Super sobre Super. */
    public static boolean puedeGestionar(String rolActor, String rolObjetivo) {
        int actor = rango(rolActor);
        int objetivo = rango(rolObjetivo);
        if (actor < MODERADOR) {
            return false;
        }
        return actor > objetivo || (actor == SUPER_ADMINISTRADOR && objetivo == SUPER_ADMINISTRADOR);
    }
}

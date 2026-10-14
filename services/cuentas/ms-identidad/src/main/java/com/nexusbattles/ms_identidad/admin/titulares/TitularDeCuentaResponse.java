package com.nexusbattles.ms_identidad.admin.titulares;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;

import java.util.UUID;

/**
 * A quien pertenece una sancion, una apelacion o un reporte — SA-4
 * (ms-identidad-admin.yaml 1.9.0, {@code TitularDeCuenta}).
 *
 * <p>Lo minimo para reconocer a la persona en la consola de moderacion: su
 * apodo (lo que ya ve cualquier jugador en una sala o en un comentario), el
 * estado de la cuenta y su rol. Sin correo, sin nombre, sin fechas: esos datos
 * son de la ficha, que solo abre quien gestiona cuentas (Tabla 24).
 *
 * @param uid    identificador publico, el que usan los demas servicios
 * @param apodo  nombre visible en el juego
 * @param estado ACTIVO, PENDIENTE_VERIFICACION, INACTIVO, SUSPENDIDO o BANEADO
 *               (las filas antiguas en femenino salen como el contrato)
 * @param rol    JUGADOR, MODERADOR, ADMINISTRADOR o SUPER_ADMINISTRADOR
 */
public record TitularDeCuentaResponse(UUID uid, String apodo, String estado, String rol) {

    public static TitularDeCuentaResponse desde(Usuario usuario) {
        return new TitularDeCuentaResponse(
                usuario.getPublicId(),
                usuario.getApodo(),
                EstadoCuenta.normalizado(usuario.getEstado()),
                usuario.getRol() != null ? usuario.getRol().getNombre() : null);
    }
}

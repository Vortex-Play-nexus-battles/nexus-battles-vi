package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.util.UUID;

/**
 * El rol vigente de una cuenta, para la jerarquia de 7.3.1: nadie advierte,
 * suspende, banea, levanta ni resuelve la apelacion de una cuenta con un rol
 * igual o superior al suyo.
 *
 * <p>Fail-closed: si no se puede saber el rol, se rechaza la operacion
 * ({@link SancionRechazada.Motivo#IDENTIDAD_NO_DISPONIBLE}) en vez de suponer
 * que la cuenta es de un jugador.
 */
public interface RolesDeCuentas {

    /**
     * @return el rol de la cuenta ({@code JUGADOR}, {@code MODERADOR},
     *     {@code ADMINISTRADOR} o {@code SUPER_ADMINISTRADOR})
     * @throws SancionRechazada {@code NO_ENCONTRADA} si la cuenta no existe;
     *     {@code IDENTIDAD_NO_DISPONIBLE} si ms-identidad no lo dijo
     */
    String rolDe(UUID cuenta);
}

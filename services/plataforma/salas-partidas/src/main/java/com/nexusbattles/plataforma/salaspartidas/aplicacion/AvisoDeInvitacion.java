package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de salida: el aviso que le llega al jugador invitado — salas-partidas
 * 1.10.0. Lo lleva notificaciones ({@code POST /internal/notifications}), con
 * un {@code id} estable por sala e invitado: invitar dos veces a la misma
 * persona a la misma sala no le llena la bandeja.
 */
public interface AvisoDeInvitacion {

    /**
     * @return {@code true} si el aviso sale ahora; {@code false} si ya estaba (la misma invitacion otra vez)
     * @throws AvisoNoDisponible si notificaciones no contesta
     */
    boolean invitar(Invitacion invitacion);

    /**
     * Lo que dice el aviso. El codigo solo viaja en una sala privada: es lo que
     * le deja entrar al invitado.
     *
     * @param idSala         la sala
     * @param idInvitado     a quien se invita
     * @param apodoAnfitrion quien invita
     * @param modalidad      la modalidad, para el texto
     * @param privada        si hace falta el codigo
     * @param codigo         el codigo de invitacion, o nulo en una sala publica
     * @param recompensa     creditos en juego
     * @param enviadaEn      cuando
     */
    record Invitacion(UUID idSala, UUID idInvitado, String apodoAnfitrion, String modalidad, boolean privada,
                      String codigo, int recompensa, Instant enviadaEn) {
    }

    /** Notificaciones no contesto: la invitacion no salio. */
    class AvisoNoDisponible extends RuntimeException {
        public AvisoNoDisponible(String motivo) {
            super(motivo);
        }
    }
}

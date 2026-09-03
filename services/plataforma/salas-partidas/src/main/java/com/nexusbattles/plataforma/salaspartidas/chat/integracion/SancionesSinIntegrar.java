package com.nexusbattles.plataforma.salaspartidas.chat.integracion;

import com.nexusbattles.plataforma.salaspartidas.chat.SancionesDelJugador;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sanciones sin integrar, por el mismo motivo y con el mismo patron que
 * CreditosSinIntegrar: RF-USR-004 es de Sprint 3 y moderacion-sanciones no ha
 * publicado el contrato de consulta. Mientras tanto nadie figura silenciado.
 *
 * <p>Se reemplaza el dia que exista el contrato. El puerto SancionesDelJugador
 * y la prueba del caso de uso ya cubren el comportamiento del silencio.
 */
@Component
class SancionesSinIntegrar implements SancionesDelJugador {

    @Override
    public boolean estaSilenciado(UUID idJugador) {
        return false;
    }
}

package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesNoDisponibles;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Sanciones en memoria: quien esta en el conjunto tiene sancion activa. */
public class SancionesEnMemoria implements SancionesDelJugador {

    final Set<UUID> sancionados = new HashSet<>();

    /** Cuando esta caido, no responde (y el caso de uso no debe asumir «sin sancion»). */
    boolean caido;

    public SancionesEnMemoria sancionado(UUID jugador) {
        sancionados.add(jugador);
        return this;
    }

    /** El servicio de sanciones no responde: nadie puede dar por hecho que no hay sancion. */
    public SancionesEnMemoria caido() {
        caido = true;
        return this;
    }

    @Override
    public boolean tieneSancionActiva(UUID idJugador) {
        if (caido) {
            throw new SancionesNoDisponibles();
        }
        return sancionados.contains(idJugador);
    }
}

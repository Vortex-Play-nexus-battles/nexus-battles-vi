package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalidaNoPermitida;

import java.util.Objects;
import java.util.UUID;

/**
 * Salida de un jugador de una sala — operacion {@code abandonarSala} del contrato.
 *
 * <p>Espejo de {@link IngresarASala}, incluida la carrera: dos personas pueden
 * salir a la vez, o una salir mientras otra entra, y el bloqueo optimista lo
 * detecta igual. Se reintenta con el mismo criterio y por el mismo motivo: al
 * releer, o el jugador sigue dentro y sale, o ya no esta y el dominio lo dice.
 *
 * <p>El anuncio va despues de guardar, nunca antes. Anunciar una salida que
 * todavia podria perderse dejaria a los demas viendo un cupo libre que la base
 * de datos no tiene.
 */
public class AbandonarSala {

    /** Mismo criterio que {@link IngresarASala#INTENTOS}, y por la misma razon. */
    static final int INTENTOS = 3;

    private final RepositorioDeSalas repositorio;
    private final CanalDeSala canal;

    public AbandonarSala(RepositorioDeSalas repositorio, CanalDeSala canal) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.canal = Objects.requireNonNull(canal);
    }

    /**
     * @param idSala    sala que se abandona
     * @param idJugador jugador autenticado que se va
     * @throws SalaNoEncontrada  si el identificador no corresponde a ninguna sala
     * @throws SalidaNoPermitida si no esta dentro, si es el anfitrion, o si la
     *                           sala ya no admite salidas
     */
    public void ejecutar(UUID idSala, UUID idJugador) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere abandonar.");
        Objects.requireNonNull(idJugador, "Hace falta el jugador que quiere salir.");

        for (int intento = 1; ; intento++) {
            Sala sala = repositorio.buscarPorId(idSala)
                    .orElseThrow(() -> new SalaNoEncontrada(idSala));

            sala.abandonar(idJugador);

            try {
                Sala guardada = repositorio.guardar(sala);
                canal.anunciarSalida(guardada, idJugador);
                return;
            } catch (SalaModificadaConcurrentemente otroSeAdelanto) {
                if (intento >= INTENTOS) {
                    throw new SalidaNoPermitida(
                            "La sala cambio mientras salias. Intentalo de nuevo.");
                }
            }
        }
    }
}

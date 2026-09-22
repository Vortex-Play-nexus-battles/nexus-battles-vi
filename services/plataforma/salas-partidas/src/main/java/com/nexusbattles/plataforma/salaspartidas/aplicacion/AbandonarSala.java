package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalidaNoPermitida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Salida de un jugador de una sala — operacion {@code abandonarSala} del contrato.
 *
 * <p>Espejo de {@link IngresarASala}, incluida la carrera: dos personas pueden
 * salir a la vez, o una salir mientras otra entra, y el bloqueo optimista lo
 * detecta igual. Se reintenta con el mismo criterio y por el mismo motivo: al
 * releer, o el jugador sigue dentro y sale, o ya no esta y el dominio lo dice.
 *
 * <p><b>Devuelve los creditos</b> (RF-JUE-014, HU-JUE-014 CA-03): quien se va
 * antes de empezar recupera su reserva. Se libera DESPUES de guardar la salida,
 * por la misma razon que en {@link CancelarSala}: si se liberara antes y el
 * guardado fallara, el jugador tendria sus creditos de vuelta y seguiria
 * dentro de una sala con apuesta que ya no respalda. Y si el libro no responde
 * al liberar, la salida no se deshace —ya esta en la base— sino que se anota
 * con todo lo necesario para devolverla a mano; {@code liberar} es idempotente
 * precisamente para poder reintentarlo.
 *
 * <p>El anuncio va despues de guardar, nunca antes. Anunciar una salida que
 * todavia podria perderse dejaria a los demas viendo un cupo libre que la base
 * de datos no tiene.
 */
public class AbandonarSala {

    private static final Logger BITACORA = LoggerFactory.getLogger(AbandonarSala.class);

    /** Mismo criterio que {@link IngresarASala#INTENTOS}, y por la misma razon. */
    static final int INTENTOS = 3;

    private final RepositorioDeSalas repositorio;
    private final CanalDeSala canal;
    private final CreditosDelJugador creditos;

    public AbandonarSala(RepositorioDeSalas repositorio, CanalDeSala canal, CreditosDelJugador creditos) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.canal = Objects.requireNonNull(canal);
        this.creditos = Objects.requireNonNull(creditos, "Hace falta el libro de creditos.");
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

            // Se pregunta ANTES de sacarlo: una vez fuera, la sala ya no sabe
            // nada de el, tampoco que reserva era la suya.
            Optional<UUID> reserva = sala.reservaDe(idJugador);

            sala.abandonar(idJugador);

            try {
                Sala guardada = repositorio.guardar(sala);
                reserva.ifPresent(id -> devolver(id, idJugador, guardada));
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

    private void devolver(UUID idReserva, UUID idJugador, Sala sala) {
        try {
            creditos.liberar(idReserva);
        } catch (RuntimeException noSePudoLiberar) {
            BITACORA.error("El jugador {} salio de la sala {} pero su reserva {} de {} creditos "
                            + "no se pudo liberar; hay que devolverla a mano.",
                    idJugador, sala.id(), idReserva, sala.recompensaCreditos(), noSePudoLiberar);
        }
    }
}

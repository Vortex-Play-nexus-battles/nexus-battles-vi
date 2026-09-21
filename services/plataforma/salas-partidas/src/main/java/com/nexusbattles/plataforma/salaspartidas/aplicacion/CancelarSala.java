package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotivoDeCancelacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsElAnfitrion;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalidaNoPermitida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Cancelacion de una sala por su anfitrion — operacion {@code cancelarSala}.
 *
 * <p>Tres pasos, en este orden y no en otro:
 * <ol>
 *   <li>Marcar la sala CANCELADA y guardarla. Es lo unico que decide si la
 *       cancelacion ocurrio.</li>
 *   <li>Devolver los creditos comprometidos (RF-JUE-014), si los habia.</li>
 *   <li>Avisar a quienes estaban dentro.</li>
 * </ol>
 *
 * <p><b>Por que los creditos van despues de guardar.</b> Si se liberaran antes y
 * el guardado fallara, el anfitrion tendria sus creditos de vuelta y la sala
 * seguiria en pie y jugable: creditos en juego que ya no respalda nadie. Al
 * reves, el peor caso es una reserva que tarda en volver, y eso se puede
 * reintentar; {@code liberar} es idempotente justamente para permitirlo.
 *
 * <p><b>Y por que un fallo al liberar no tumba la cancelacion.</b> La sala ya
 * esta cancelada en la base: propagar el error haria que quien cancelo viera un
 * 500 y creyera que su sala sigue abierta. Se registra en la bitacora con todo
 * lo necesario para reclamarla a mano y se sigue, avisando de 0 creditos
 * devueltos en vez de prometer un numero que no se cumplio.
 */
public class CancelarSala {

    private static final Logger BITACORA = LoggerFactory.getLogger(CancelarSala.class);

    /** Mismo criterio que {@link IngresarASala#INTENTOS}, y por la misma razon. */
    static final int INTENTOS = 3;

    private final RepositorioDeSalas repositorio;
    private final CreditosDelJugador creditos;
    private final CanalDeSala canal;

    public CancelarSala(RepositorioDeSalas repositorio, CreditosDelJugador creditos,
                        CanalDeSala canal) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.creditos = Objects.requireNonNull(creditos);
        this.canal = Objects.requireNonNull(canal);
    }

    /**
     * @param idSala        sala que se cancela
     * @param idSolicitante jugador autenticado que pide la cancelacion
     * @throws SalaNoEncontrada  si el identificador no corresponde a ninguna sala
     * @throws NoEsElAnfitrion   si quien lo pide no creo la sala
     * @throws SalidaNoPermitida si la partida ya empezo o la sala ya no esta activa
     */
    public void ejecutar(UUID idSala, UUID idSolicitante) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere cancelar.");
        Objects.requireNonNull(idSolicitante, "Hace falta saber quien cancela.");

        for (int intento = 1; ; intento++) {
            Sala sala = repositorio.buscarPorId(idSala)
                    .orElseThrow(() -> new SalaNoEncontrada(idSala));

            sala.cancelar(idSolicitante);

            try {
                Sala guardada = repositorio.guardar(sala);
                int devueltos = devolverCreditos(guardada);
                canal.anunciarCancelacion(guardada,
                        MotivoDeCancelacion.CANCELADA_POR_ANFITRION, devueltos);
                return;
            } catch (SalaModificadaConcurrentemente otroSeAdelanto) {
                if (intento >= INTENTOS) {
                    throw new SalidaNoPermitida(
                            "La sala cambio mientras la cancelabas. Intentalo de nuevo.");
                }
            }
        }
    }

    /**
     * Devuelve la reserva de <b>cada</b> participante — HU-JUE-014, CA-03.
     *
     * <p>Una a una y sin parar en la primera que falle: que el libro no
     * responda para uno no es motivo para no devolverle al siguiente. Cada
     * fallo se anota con lo necesario para devolverla a mano.
     *
     * @return creditos devueltos a cada participante (la recompensa de la
     *         sala) si todas las reservas se liberaron; 0 si no habia o si
     *         alguna no se pudo, para no prometer un numero que no se cumplio
     */
    private int devolverCreditos(Sala sala) {
        java.util.Map<UUID, UUID> reservas = sala.reservasDeCreditos();
        if (reservas.isEmpty()) {
            return 0;
        }
        boolean todas = true;
        for (java.util.Map.Entry<UUID, UUID> entrada : reservas.entrySet()) {
            try {
                creditos.liberar(entrada.getValue());
            } catch (RuntimeException noSePudoLiberar) {
                todas = false;
                BITACORA.error(
                        "Sala {} cancelada pero la reserva {} de {} creditos no se pudo liberar; "
                                + "hay que devolverla a mano al jugador {}.",
                        sala.id(), entrada.getValue(), sala.recompensaCreditos(), entrada.getKey(),
                        noSePudoLiberar);
            }
        }
        return todas ? sala.recompensaCreditos() : 0;
    }
}

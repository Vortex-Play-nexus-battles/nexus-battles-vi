package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso de HU-SAL-001: un jugador crea una sala de batalla.
 *
 * <p>Orquesta, no decide. Las reglas del juego viven en {@link Sala#crear}; aqui
 * se coordinan tres pasos que pueden fallar por separado.
 *
 * <p><b>El orden importa y esta pensado:</b>
 * <ol>
 *   <li>Validar los parametros. Si estan mal, no se molesta al modulo de
 *       creditos ni se escribe nada.</li>
 *   <li>Reservar los creditos (RF-JUE-014). Atomico del lado del proveedor.</li>
 *   <li>Guardar la sala. Si esto falla, se libera la reserva: si no, el jugador
 *       se quedaria sin creditos y sin sala.</li>
 * </ol>
 *
 * <p><b>Limitacion conocida.</b> Entre el paso 2 y el 3 no hay transaccion
 * distribuida. Si el proceso muere justo ahi, la reserva queda huerfana. Se
 * resuelve de una de dos formas, y ninguna se puede decidir en solitario: o el
 * modulo de creditos caduca las reservas sin confirmar, o este servicio publica
 * la confirmacion por bandeja de salida. Esta anotado en la peticion que va al
 * equipo de creditos.
 */
public class CrearSala {

    private final RepositorioDeSalas repositorio;
    private final CreditosDelJugador creditos;

    public CrearSala(RepositorioDeSalas repositorio, CreditosDelJugador creditos) {
        this.repositorio = Objects.requireNonNull(repositorio, "Hace falta un repositorio de salas.");
        this.creditos = Objects.requireNonNull(creditos, "Hace falta el modulo de creditos.");
    }

    /**
     * @param parametros  parametros elegidos por el jugador
     * @param idAnfitrion jugador autenticado que crea la sala
     * @return la sala ya guardada
     * @throws ParametrosInvalidos   si algun parametro esta fuera de rango
     * @throws CreditosInsuficientes si el saldo no cubre la recompensa
     */
    public Sala ejecutar(ParametrosDeSala parametros, UUID idAnfitrion) {
        Objects.requireNonNull(idAnfitrion, "Solo un jugador identificado puede crear una sala.");

        Sala sala = Sala.crear(parametros, idAnfitrion);

        if (sala.recompensaCreditos() == 0) {
            // Apostar es libre: una sala sin recompensa no molesta al modulo de creditos.
            return repositorio.guardar(sala);
        }

        ReservaDeCreditos reserva =
                creditos.reservar(idAnfitrion, sala.recompensaCreditos(), sala.id());
        try {
            return repositorio.guardar(sala);
        } catch (RuntimeException falloAlGuardar) {
            creditos.liberar(reserva.id());
            throw falloAlGuardar;
        }
    }
}

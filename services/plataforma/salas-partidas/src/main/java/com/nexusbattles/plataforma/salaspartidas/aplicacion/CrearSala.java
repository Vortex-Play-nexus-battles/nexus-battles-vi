package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;

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
    private final HeroeDelJugador heroes;
    private final SancionesDelJugador sanciones;

    public CrearSala(RepositorioDeSalas repositorio, CreditosDelJugador creditos,
                     HeroeDelJugador heroes, SancionesDelJugador sanciones) {
        this.repositorio = Objects.requireNonNull(repositorio, "Hace falta un repositorio de salas.");
        this.creditos = Objects.requireNonNull(creditos, "Hace falta el modulo de creditos.");
        this.heroes = Objects.requireNonNull(heroes, "Sin inventario no se puede abrir la puerta.");
        this.sanciones = Objects.requireNonNull(sanciones, "Sin sanciones no se sabe quien puede jugar.");
    }

    /**
     * @param parametros parametros elegidos por el jugador
     * @param anfitrion  jugador autenticado que crea la sala
     * @return la sala ya guardada
     * @throws ParametrosInvalidos    si algun parametro esta fuera de rango
     * @throws CreditosInsuficientes  si el saldo no cubre la recompensa
     * @throws HeroeNoDisponible      si no tiene heroe equipado o el suyo ya combate
     * @throws InventarioNoDisponible si el inventario no contesta
     */
    public Sala ejecutar(ParametrosDeSala parametros, JugadorAutenticado anfitrion) {
        Objects.requireNonNull(anfitrion, "Solo un jugador identificado puede crear una sala.");

        // El orden importa: la sancion se comprueba ANTES que el heroe y antes
        // de reservar un solo credito. Preguntarle al inventario por el heroe
        // de alguien que no puede jugar es trabajo tirado, y una reserva que
        // luego hay que liberar es una via mas de que algo se quede a medias.
        PuertaDeSancion.comprobar(sanciones, anfitrion);

        // El anfitrion entra a su propia sala en el momento de crearla, asi que
        // pasa la misma puerta que los demas (SCRUM-1074). Va antes de reservar
        // creditos: rechazar despues de reservar obligaria a devolverlos, y una
        // devolucion que falle deja el saldo retenido.
        com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante ficha =
                new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante(
                        anfitrion.apodo(), PuertaDeHeroe.comprobar(heroes, anfitrion).heroe());

        UUID idAnfitrion = anfitrion.id();
        Sala sala = Sala.crear(parametros, idAnfitrion, ficha);

        if (sala.recompensaCreditos() == 0) {
            // Apostar es libre: una sala sin recompensa no molesta al modulo de creditos.
            return repositorio.guardar(sala);
        }

        // La sala acaba de nacer (version 0) y su identificador es nuevo: la
        // clave de idempotencia es unica por sala creada.
        ReservaDeCreditos reserva =
                creditos.reservar(idAnfitrion, sala.recompensaCreditos(), sala.id(), sala.version());
        try {
            // La sala guarda que reserva le pertenece: sin ese dato, cancelarla
            // mas tarde no podria devolver los creditos y quedarian retenidos
            // para siempre (RF-JUE-014, ver CancelarSala).
            return repositorio.guardar(sala.conReserva(reserva.id()));
        } catch (RuntimeException falloAlGuardar) {
            creditos.liberar(reserva.id());
            throw falloAlGuardar;
        }
    }
}

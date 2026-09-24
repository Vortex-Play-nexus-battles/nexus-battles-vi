package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Ingreso de un jugador a una sala existente — HU-SAL-002, RF-JUE-002.
 *
 * <p>Coordina y nada mas: buscar la sala, comprometer los creditos si la sala
 * los pide, pedirle a la sala que admita al jugador, guardar el resultado y
 * anunciarlo a quienes ya estan dentro. Las reglas de quien puede entrar viven
 * en {@link Sala#unirse(UUID, FichaDeParticipante, String)}, no aqui: si una
 * condicion de ingreso apareciera en esta clase, estaria en el sitio
 * equivocado.
 *
 * <p><b>Comprueba el heroe equipado</b> (RF-JUE-003, SCRUM-1074). Es la puerta
 * con efectos: {@code VerificarHeroe} avisa antes de pulsar, pero quien decide
 * es este caso de uso. Sin la comprobacion aqui, un cliente que llamara directo
 * al endpoint entraria sin heroe y la partida arrancaria con una barra de vida
 * que nadie puede pintar.
 *
 * <p><b>Compromete los creditos</b> (RF-JUE-014, HU-JUE-014 CA-01/CA-02). Si la
 * sala tiene recompensa, se reserva ANTES de entrar, contra el libro de
 * creditos, y la reserva viaja en la ficha del participante para que la sala
 * sepa que devolverle si se va o que cobrarle si pierde. Si el saldo no
 * alcanza, el rechazo (422) sale antes de tocar la sala. Y si despues de
 * reservar el ingreso no prospera —la sala se lleno, ya estaba dentro, la base
 * no responde— la reserva se libera: un jugador que no entro no puede quedarse
 * con creditos comprometidos.
 *
 * <p>Se reserva antes de comprobar el aforo y no despues porque lo contrario
 * seria duplicar aqui las reglas de la sala para «adivinar» si va a admitir. La
 * reserva y su liberacion son idempotentes y baratas; una regla duplicada no.
 *
 * <p>El codigo de invitacion se pasa tal cual al agregado: quien decide si vale
 * es {@link Sala}, no este caso de uso.
 */
public class IngresarASala {

    private static final Logger BITACORA = LoggerFactory.getLogger(IngresarASala.class);

    /**
     * Cuantas veces se vuelve a leer la sala si otro ingreso se guardo en medio.
     * Dos jugadores en el mismo cupo se resuelven en el segundo intento; tres
     * cubre a un tercero que llegue justo entonces. Mas seria insistir contra
     * una sala que cambia sin parar, y eso se reporta, no se espera.
     */
    static final int INTENTOS = 3;

    private final RepositorioDeSalas repositorio;
    private final CanalDeSala canal;
    private final HeroeDelJugador heroes;
    private final CreditosDelJugador creditos;
    private final SancionesDelJugador sanciones;

    public IngresarASala(RepositorioDeSalas repositorio, CanalDeSala canal, HeroeDelJugador heroes,
                         CreditosDelJugador creditos, SancionesDelJugador sanciones) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.canal = Objects.requireNonNull(canal);
        this.heroes = Objects.requireNonNull(heroes, "Sin inventario no se puede abrir la puerta.");
        this.creditos = Objects.requireNonNull(creditos, "Hace falta el libro de creditos.");
        this.sanciones = Objects.requireNonNull(sanciones, "Sin sanciones no se sabe quien puede jugar.");
    }

    /**
     * @param idSala  sala elegida del listado
     * @param jugador jugador autenticado que quiere entrar
     * @return la sala con el jugador dentro
     * @throws SalaNoEncontrada si el identificador no corresponde a ninguna sala
     * @throws IngresoNoPermitido si la sala no admite al jugador, o si cambio
     *                            tantas veces seguidas que no se pudo asentar
     * @throws HeroeNoDisponible si no tiene heroe equipado o el suyo ya combate
     * @throws InventarioNoDisponible si el inventario no contesta
     * @throws CreditosInsuficientes si el saldo no cubre la recompensa de la sala
     * @throws CreditosNoDisponibles si el libro de creditos no contesta
     */
    public Sala ejecutar(UUID idSala, JugadorAutenticado jugador) {
        return ejecutar(idSala, jugador, null);
    }

    /**
     * @param idSala  sala elegida del listado
     * @param jugador jugador autenticado que quiere entrar
     * @param codigo  codigo de invitacion; obligatorio solo si la sala es privada
     * @return la sala con el jugador dentro
     * @throws SalaNoEncontrada si el identificador no corresponde a ninguna sala
     * @throws IngresoNoPermitido si la sala no admite al jugador, o si cambio
     *                            tantas veces seguidas que no se pudo asentar
     * @throws HeroeNoDisponible si no tiene heroe equipado o el suyo ya combate
     * @throws InventarioNoDisponible si el inventario no contesta
     * @throws CreditosInsuficientes si el saldo no cubre la recompensa de la sala
     * @throws CreditosNoDisponibles si el libro de creditos no contesta
     */
    public Sala ejecutar(UUID idSala, JugadorAutenticado jugador, String codigo) {
        Objects.requireNonNull(idSala, "Hace falta la sala a la que se quiere entrar.");
        Objects.requireNonNull(jugador, "Hace falta el jugador que quiere entrar.");

        // Antes que el heroe y que la reserva, por lo mismo que en CrearSala.
        PuertaDeSancion.comprobar(sanciones, jugador);

        UUID idJugador = jugador.id();

        // La puerta va primero y fuera del bucle de reintentos: el veredicto del
        // inventario no cambia porque otro jugador gane una carrera por el cupo,
        // y repetir la consulta seria castigar al inventario por una colision
        // que no es suya.
        FichaDeParticipante ficha = new FichaDeParticipante(
                jugador.apodo(), PuertaDeHeroe.comprobar(heroes, jugador).heroe());

        // Los creditos se comprometen una sola vez, tambien fuera del bucle:
        // la reserva no depende de quien mas entre. Para saber cuantos, hay que
        // leer la sala; esa lectura es la del primer intento y sirve ademas
        // para rechazar con 404 antes de reservar nada.
        Sala sala = repositorio.buscarPorId(idSala)
                .orElseThrow(() -> new SalaNoEncontrada(idSala));
        ReservaDeCreditos reserva = null;
        if (sala.recompensaCreditos() > 0) {
            reserva = creditos.reservar(idJugador, sala.recompensaCreditos(), idSala, sala.version());
            ficha = ficha.conReserva(reserva.id());
        }

        try {
            return entrar(sala, idJugador, ficha, codigo);
        } catch (RuntimeException noEntro) {
            if (reserva != null) {
                devolver(reserva, idJugador, idSala);
            }
            throw noEntro;
        }
    }

    private Sala entrar(Sala primeraLectura, UUID idJugador, FichaDeParticipante ficha, String codigo) {
        UUID idSala = primeraLectura.id();
        Sala sala = primeraLectura;
        for (int intento = 1; ; intento++) {
            if (intento > 1) {
                sala = repositorio.buscarPorId(idSala)
                        .orElseThrow(() -> new SalaNoEncontrada(idSala));
            }

            // Si unirse rechaza, la excepcion sale antes de guardar: una sala que no
            // admitio a nadie no tiene por que reescribirse, ni anunciarse.
            sala.unirse(idJugador, ficha, codigo);

            try {
                Sala guardada = repositorio.guardar(sala);

                // El anuncio va DESPUES de guardar, y solo si guardar salio bien. Al
                // reves se anunciaria una entrada que todavia podria perderse, y los
                // que estan dentro verian una ocupacion que la base de datos no tiene.
                canal.anunciarIngreso(guardada, idJugador);

                return guardada;
            } catch (SalaModificadaConcurrentemente otroSeAdelanto) {
                // Carrera por el ultimo cupo (HU-SAL-002): alguien guardo su
                // ingreso entre nuestra lectura y nuestra escritura. No se pisa
                // esa escritura: se vuelve a leer la sala tal como quedo. Si el
                // otro ocupo el ultimo cupo, unirse la rechazara con el motivo
                // de siempre y este jugador recibira un 409 honesto, no un 200
                // por una entrada que la base no conserva. Y como no se guardo,
                // tampoco se anuncia nada por el canal.
                if (intento >= INTENTOS) {
                    throw new IngresoNoPermitido(
                            "La sala cambio mientras entrabas. Intentalo de nuevo.");
                }
            }
        }
    }

    /**
     * Devuelve la reserva de un ingreso que no prospero. Si el libro tampoco
     * responde a esto, se anota con todo lo necesario para devolverla a mano:
     * el error que se propaga es el del ingreso, que es el que explica al
     * jugador que paso.
     */
    private void devolver(ReservaDeCreditos reserva, UUID idJugador, UUID idSala) {
        try {
            creditos.liberar(reserva.id());
        } catch (RuntimeException noSePudoLiberar) {
            BITACORA.error("El jugador {} no entro a la sala {} pero su reserva {} de {} creditos "
                            + "no se pudo liberar; hay que devolverla a mano.",
                    idJugador, idSala, reserva.id(), reserva.creditos(), noSePudoLiberar);
        }
    }
}

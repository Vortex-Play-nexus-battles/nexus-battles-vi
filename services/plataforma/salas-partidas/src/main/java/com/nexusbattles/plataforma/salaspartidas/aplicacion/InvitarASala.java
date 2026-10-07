package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.InvitacionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Invitar a un jugador a la sala — salas-partidas.yaml 1.10.0, revision del modo
 * jugador del 6-oct (punto 13: «una opcion que diga invitar amigos o buscar por
 * nombre, algo bien disenado»).
 *
 * <p>El anfitrion busca al jugador por su apodo ({@code GET /perfiles/publicos}
 * de ms-identidad, que ya existe) y lo invita por su identificador. Aqui:
 * <ol>
 *   <li>Solo invita el anfitrion: la sala es suya y, si es privada, el codigo
 *       tambien.</li>
 *   <li>La sala tiene que estar esperando gente y con sitio, y el invitado no
 *       puede estar ya dentro ni ser el mismo anfitrion.</li>
 *   <li>El invitado tiene que existir y tener la cuenta activa: se pregunta a
 *       ms-identidad por su contacto (regla 7: este servicio no guarda
 *       cuentas).</li>
 *   <li>Le llega un aviso a la bandeja con quien invita, la sala y, si es
 *       privada, el codigo. Invitar otra vez a la misma persona a la misma sala
 *       no manda un segundo aviso.</li>
 * </ol>
 *
 * <p>No es un sistema de amigos: no guarda nada. La invitacion es el aviso.
 */
public class InvitarASala {

    /** Lo que se le dice al anfitrion: a quien, y si el aviso salio ahora o ya estaba. */
    public record Resultado(UUID idJugador, String apodo, boolean enviada) {
    }

    private final RepositorioDeSalas salas;
    private final DirectorioDeJugadores directorio;
    private final AvisoDeInvitacion aviso;
    private final Clock reloj;

    public InvitarASala(RepositorioDeSalas salas, DirectorioDeJugadores directorio, AvisoDeInvitacion aviso,
                        Clock reloj) {
        this.salas = Objects.requireNonNull(salas);
        this.directorio = Objects.requireNonNull(directorio);
        this.aviso = Objects.requireNonNull(aviso);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * @param idSala     la sala a la que se invita
     * @param anfitrion  quien invita (sale del token)
     * @param idInvitado a quien
     * @throws SalaNoEncontrada        si no existe
     * @throws InvitacionNoPermitida   por cualquiera de las reglas de arriba
     */
    public Resultado ejecutar(UUID idSala, JugadorAutenticado anfitrion, UUID idInvitado) {
        Objects.requireNonNull(idSala, "Hace falta la sala.");
        Objects.requireNonNull(anfitrion, "Hace falta quien invita.");
        Objects.requireNonNull(idInvitado, "Hace falta a quien se invita.");

        Sala sala = salas.buscarPorId(idSala).orElseThrow(() -> new SalaNoEncontrada(idSala));
        if (!sala.esAnfitrion(anfitrion.id())) {
            throw InvitacionNoPermitida.soloElAnfitrion();
        }
        if (idInvitado.equals(anfitrion.id())) {
            throw InvitacionNoPermitida.aTiMismo();
        }
        if (sala.participantes().contains(idInvitado)) {
            throw InvitacionNoPermitida.yaEstaDentro();
        }
        if (sala.estado() != EstadoSala.ABIERTA && sala.estado() != EstadoSala.PRIVADA) {
            throw sala.estado() == EstadoSala.LLENA
                    ? InvitacionNoPermitida.sinCupo()
                    : InvitacionNoPermitida.salaQueNoEspera();
        }
        if (sala.ocupacion() >= sala.maximoParticipantes()) {
            throw InvitacionNoPermitida.sinCupo();
        }

        DirectorioDeJugadores.CuentaDeJugador cuenta;
        try {
            cuenta = directorio.buscar(idInvitado)
                    .filter(DirectorioDeJugadores.CuentaDeJugador::activa)
                    .orElseThrow(InvitacionNoPermitida::jugadorNoEncontrado);
        } catch (DirectorioDeJugadores.DirectorioNoDisponible sinDirectorio) {
            throw InvitacionNoPermitida.sinServicio();
        }

        boolean enviada;
        try {
            enviada = aviso.invitar(new AvisoDeInvitacion.Invitacion(sala.id(), cuenta.uid(), anfitrion.apodo(),
                    sala.modalidad().name(), sala.privada(), sala.privada() ? sala.codigoInvitacion() : null,
                    sala.recompensaCreditos(), reloj.instant()));
        } catch (AvisoDeInvitacion.AvisoNoDisponible sinAviso) {
            throw InvitacionNoPermitida.sinServicio();
        }
        return new Resultado(cuenta.uid(), cuenta.apodo(), enviada);
    }
}

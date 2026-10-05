package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;

import java.util.Objects;
import java.util.UUID;

/**
 * ¿Me dejaria entrar esta sala con este codigo? — RFINAL-04, HU-SAL-002,
 * salas-partidas.yaml 1.9.0 ({@code comprobarIngreso}).
 *
 * <p><b>No tiene efectos.</b> No mete a nadie, no reserva creditos y no
 * pregunta al inventario: es la pregunta que el listado hace ANTES de mandar a
 * alguien a la verificacion del heroe. La revision de AWS DEV del 4-oct
 * encontro que un codigo de invitacion equivocado («ZZZZ-9999») llevaba igual
 * a esa verificacion, y la persona descubria que el codigo no valia despues de
 * elegir heroe y confirmar la apuesta.
 *
 * <p>Responde con las mismas reglas y el mismo orden que el ingreso de verdad
 * ({@link IngresarASala}): sancion (403), sala (404), codigo (403), estado y
 * aforo (409). Las reglas son de {@link Sala#comprobarIngreso}: aqui no se
 * repite ninguna. Que diga «si» no reserva el cupo: el ingreso las vuelve a
 * aplicar, y si alguien ocupa el ultimo hueco en medio, ese 409 es honesto.
 */
public class ComprobarIngreso {

    private final RepositorioDeSalas repositorio;
    private final SancionesDelJugador sanciones;

    public ComprobarIngreso(RepositorioDeSalas repositorio, SancionesDelJugador sanciones) {
        this.repositorio = Objects.requireNonNull(repositorio, "Hace falta un repositorio de salas.");
        this.sanciones = Objects.requireNonNull(sanciones, "Sin sanciones no se sabe quien puede jugar.");
    }

    /**
     * @param idSala  sala a la que se quiere entrar
     * @param jugador jugador autenticado; la identidad sale del token
     * @param codigo  codigo de invitacion; solo cuenta si la sala es privada
     * @return la sala tal como esta, sin cambios
     * @throws com.nexusbattles.plataforma.salaspartidas.dominio.JugadorSancionado
     *     si tiene una sancion activa (403)
     * @throws SalaNoEncontrada si la sala no existe (404)
     * @throws com.nexusbattles.plataforma.salaspartidas.dominio.SalaPrivadaSinInvitacion
     *     si es privada y el codigo falta o no vale (403)
     * @throws com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido
     *     si esta llena, ya empezo o ya se esta dentro (409)
     */
    public Sala ejecutar(UUID idSala, JugadorAutenticado jugador, String codigo) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere comprobar.");
        Objects.requireNonNull(jugador, "Hace falta el jugador que pregunta.");

        PuertaDeSancion.comprobar(sanciones, jugador);
        Sala sala = repositorio.buscarPorId(idSala)
                .orElseThrow(() -> new SalaNoEncontrada(idSala));
        sala.comprobarIngreso(jugador.id(), codigo);
        return sala;
    }
}

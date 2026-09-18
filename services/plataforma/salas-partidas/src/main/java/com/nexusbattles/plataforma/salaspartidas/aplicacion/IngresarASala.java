package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;

import java.util.Objects;
import java.util.UUID;

/**
 * Ingreso de un jugador a una sala existente — HU-SAL-002, RF-JUE-002.
 *
 * <p>Coordina cuatro pasos y nada mas: buscar la sala, pedirle que admita al
 * jugador, guardar el resultado y anunciarlo a quienes ya estan dentro. Las
 * reglas de quien puede entrar viven en {@link Sala#unirse(UUID)}, no aqui: si
 * una condicion de ingreso apareciera en esta clase, estaria en el sitio
 * equivocado.
 *
 * <p><b>Comprueba el heroe equipado</b> (RF-JUE-003, SCRUM-1074). Es la puerta
 * con efectos: {@code VerificarHeroe} avisa antes de pulsar, pero quien decide
 * es este caso de uso. Sin la comprobacion aqui, un cliente que llamara directo
 * al endpoint entraria sin heroe y la partida arrancaria con una barra de vida
 * que nadie puede pintar.
 *
 * <p>Se pregunta <b>antes</b> de tocar la sala: si el heroe no sirve, la sala
 * no se lee dos veces, no se reescribe y no se anuncia nada.
 *
 * <p>No comprueba los creditos. RF-JUE-014 lo exige, pero ese modulo todavia no
 * expone contrato; cuando exista se anade como puerto, igual que este.
 *
 * <p>El codigo de invitacion se pasa tal cual al agregado: quien decide si vale
 * es {@link Sala}, no este caso de uso. Aqui no vive ninguna condicion de
 * ingreso, ni siquiera la de las salas privadas.
 */
public class IngresarASala {

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

    public IngresarASala(RepositorioDeSalas repositorio, CanalDeSala canal, HeroeDelJugador heroes) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.canal = Objects.requireNonNull(canal);
        this.heroes = Objects.requireNonNull(heroes, "Sin inventario no se puede abrir la puerta.");
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
     */
    public Sala ejecutar(UUID idSala, JugadorAutenticado jugador, String codigo) {
        Objects.requireNonNull(idSala, "Hace falta la sala a la que se quiere entrar.");
        Objects.requireNonNull(jugador, "Hace falta el jugador que quiere entrar.");

        UUID idJugador = jugador.id();

        // La puerta va primero y fuera del bucle de reintentos: el veredicto del
        // inventario no cambia porque otro jugador gane una carrera por el cupo,
        // y repetir la consulta seria castigar al inventario por una colision
        // que no es suya.
        PuertaDeHeroe.comprobar(heroes, jugador);

        for (int intento = 1; ; intento++) {
            Sala sala = repositorio.buscarPorId(idSala)
                    .orElseThrow(() -> new SalaNoEncontrada(idSala));

            // Si unirse rechaza, la excepcion sale antes de guardar: una sala que no
            // admitio a nadie no tiene por que reescribirse, ni anunciarse.
            sala.unirse(idJugador, codigo);

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
}

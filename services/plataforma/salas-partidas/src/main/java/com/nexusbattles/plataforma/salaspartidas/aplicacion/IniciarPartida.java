package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InicioDeTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.OrdenDeTurnos;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Arranque del combate — HU-SAL-004, RF-JUE-017, §6.1.3.
 *
 * <p>Pedirle a la sala que empiece —ahi viven las reglas de quien puede y
 * cuando—, construir la partida con quien estaba dentro, preparar el combate
 * con el motor, guardar las dos y anunciarlo por el canal.
 *
 * <p><b>Desde B7:</b>
 * <ul>
 *   <li>El orden de los turnos se SORTEA entre todos (§6.1.3) con una semilla
 *       de {@code SecureRandom} que la partida guarda: ya no abre siempre el
 *       anfitrion.</li>
 *   <li>La maquina juega con un heroe aleatorio del catalogo (D-B7-11).</li>
 *   <li>El motor prepara el combate: todos a vida completa en su nivel, con
 *       su poder y las acciones que pueden jugar. Si no responde, la partida
 *       empieza igual y el motor completa el estado en la primera accion.</li>
 *   <li>Si el primer turno es de la maquina, juega (lo hace el gancho
 *       {@code despuesDeEmpezar}): nadie mas lo haria. Juega ANTES de que se
 *       anuncie el inicio, y el aviso y la respuesta traen la partida como
 *       quedo: todavia nadie esta suscrito al tema de la partida.</li>
 * </ul>
 *
 * <p><b>El orden no es casual.</b> Primero se guarda, despues se anuncia.
 *
 * <p><b>Idempotencia.</b> Si ya hay partida para la sala, se devuelve la que
 * existe: pulsar dos veces «empezar» no es un fallo del jugador.
 */
public class IniciarPartida {

    private static final Logger BITACORA = LoggerFactory.getLogger(IniciarPartida.class);

    private final RepositorioDeSalas salas;
    private final RepositorioDePartidas partidas;
    private final CanalDePartida canal;
    private final HeroeDelJugador heroes;
    private final Clock reloj;
    private final MotorDeCombate motor;
    private final HeroesDeLaMaquina maquina;
    private final LongSupplier semillas;
    private final Supplier<Integer> segundosPorTurno;
    private final Consumer<UUID> despuesDeEmpezar;

    public IniciarPartida(RepositorioDeSalas salas, RepositorioDePartidas partidas,
                          CanalDePartida canal, HeroeDelJugador heroes, Clock reloj) {
        this(salas, partidas, canal, heroes, reloj, null, null, new SecureRandom()::nextLong, () -> null,
                idPartida -> { });
    }

    /**
     * @param motor            prepara el combate al empezar; nulo en los dobles que no lo miran
     * @param maquina          heroes de la maquina (D-B7-11); nulo = copia del del anfitrion
     * @param semillas         semillas del sorteo del orden; {@code SecureRandom} en produccion
     * @param segundosPorTurno tiempo por turno; nulo = sin limite (D-B7-14)
     * @param despuesDeEmpezar se llama con la partida ya guardada y antes de
     *                         anunciarla: juega los turnos de la maquina si el
     *                         primero es suyo
     */
    public IniciarPartida(RepositorioDeSalas salas, RepositorioDePartidas partidas, CanalDePartida canal,
                          HeroeDelJugador heroes, Clock reloj, MotorDeCombate motor, HeroesDeLaMaquina maquina,
                          LongSupplier semillas, Supplier<Integer> segundosPorTurno,
                          Consumer<UUID> despuesDeEmpezar) {
        this.salas = Objects.requireNonNull(salas);
        this.partidas = Objects.requireNonNull(partidas);
        this.canal = Objects.requireNonNull(canal);
        this.heroes = Objects.requireNonNull(heroes, "Sin inventario no se puede abrir la puerta.");
        this.reloj = Objects.requireNonNull(reloj);
        this.motor = motor;
        this.maquina = maquina;
        this.semillas = Objects.requireNonNull(semillas);
        this.segundosPorTurno = Objects.requireNonNull(segundosPorTurno);
        this.despuesDeEmpezar = Objects.requireNonNull(despuesDeEmpezar);
    }

    /**
     * @param idSala      sala que arranca
     * @param solicitante quien lo pide; solo el anfitrion puede
     * @return la partida en curso
     * @throws SalaNoEncontrada       si la sala no existe
     * @throws HeroeNoDisponible      si su heroe dejo de estar disponible desde que entro
     * @throws InventarioNoDisponible si el inventario no contesta
     */
    public Partida ejecutar(UUID idSala, JugadorAutenticado solicitante) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere iniciar.");
        Objects.requireNonNull(solicitante, "Hace falta quien pide iniciarla.");

        UUID idSolicitante = solicitante.id();

        Sala sala = salas.buscarPorId(idSala).orElseThrow(() -> new SalaNoEncontrada(idSala));

        // Pulsar dos veces no es un error: se devuelve la partida que ya existe.
        // Va antes de la puerta a proposito: la segunda pulsacion no crea nada,
        // asi que no tiene por que volver a molestar al inventario ni fallar si
        // el heroe se ocupo en el combate que esta misma llamada arranco.
        var yaIniciada = partidas.buscarPorSala(idSala);
        if (yaIniciada.isPresent()) {
            return yaIniciada.get();
        }

        // Entre entrar a la sala y pulsar «empezar» puede pasar un buen rato, y
        // en ese rato el heroe del anfitrion puede haber entrado en otra
        // batalla. Se vuelve a comprobar: la puerta del ingreso no vale para
        // siempre (SCRUM-1074).
        PuertaDeHeroe.comprobar(heroes, solicitante);

        sala.iniciarPartida(idSolicitante);

        Instant ahora = reloj.instant();
        Partida partida = Partida.iniciar(sala, ahora, OrdenDeTurnos.sorteado(semillas.getAsLong()),
                heroesDeLaMaquina(sala));
        prepararCombate(partida, ahora);

        Partida guardada = partidas.guardar(partida);
        salas.guardar(sala);

        // Si el sorteo le dio el primer turno a la maquina, juega AHORA, antes
        // de anunciar el inicio. Quien espera en la sala se suscribe al tema de
        // la partida DESPUES de enterarse de que empezo (por este aviso o por
        // la respuesta de «Iniciar combate»): lo que la maquina anunciara antes
        // no le llegaria nunca, y su vista se quedaba en «Juega la maquina»
        // para siempre (visto en la prueba del profesor, B7). El aviso y la
        // respuesta llevan la partida como quedo tras su turno.
        despuesDeEmpezar.accept(guardada.id());
        Partida actual = partidas.buscarPorId(guardada.id()).orElse(guardada);

        canal.anunciarInicio(sala, actual);
        return actual;
    }

    /** Un heroe aleatorio del catalogo por cupo de la maquina, en el nivel del anfitrion. */
    private List<HeroeDeCombate> heroesDeLaMaquina(Sala sala) {
        if (maquina == null || sala.heroesIA() == 0) {
            return List.of();
        }
        FichaDeParticipante delAnfitrion = sala.fichaDe(sala.idAnfitrion());
        int nivel = delAnfitrion == null ? 1 : delAnfitrion.heroe().nivelDeCombate();
        try {
            return maquina.elegir(sala.heroesIA(), nivel);
        } catch (RuntimeException catalogoNoResponde) {
            BITACORA.warn("No se pudo sortear el heroe de la maquina para la sala {}; combate con una copia "
                    + "del heroe del anfitrion: {}", sala.id(), catalogoNoResponde.getMessage());
            return List.of();
        }
    }

    /**
     * Todos a vida completa en su nivel, con su poder y sus acciones: lo
     * calcula el motor al empezar el primer turno. Si no responde, la partida
     * empieza igual y el estado se completa en la primera accion.
     */
    private void prepararCombate(Partida partida, Instant ahora) {
        Integer segundos = segundosPorTurno.get();
        partida.fijarVencimientoDelTurno(segundos == null || segundos <= 0 ? null : ahora.plusSeconds(segundos));
        if (motor == null) {
            return;
        }
        UUID primero = partida.turnoActual().idJugador();
        boolean conReglas = partida.participante(primero).map(ParticipanteDePartida::heroe)
                .map(h -> h.prototipo() != null && !h.prototipo().isBlank()).orElse(false);
        if (!conReglas) {
            return;
        }
        try {
            InicioDeTurno inicio = motor.iniciarTurno(primero, partida, true);
            partida.aplicar(inicio.combatientes());
        } catch (MotorNoDisponible | DependenciaDegradada noResponde) {
            BITACORA.warn("La partida {} empieza sin el estado de combate del motor, que no respondio: {}",
                    partida.id(), noResponde.getMessage());
        }
    }
}

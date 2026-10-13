package com.nexusbattles.plataforma.salaspartidas.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Respuesta de una sala, calcada del esquema {@code Sala} del contrato OpenAPI.
 *
 * <p>Lleva exactamente lo que la interfaz pinta. La {@code Tarjeta de sala} del
 * sistema de diseno tiene dos propiedades — la variante {@code Estado} y una
 * unica linea de texto — y sus ocho ejemplos de la Pantalla 2 la resuelven como
 * «4 de 6 jugadores · 320 creditos · Con heroe de la IA». De ahi salen
 * {@code estado}, {@code ocupacion}, {@code maximoParticipantes},
 * {@code recompensaCreditos} e {@code incluirHeroeIA}.
 *
 * <p><b>Apodos (1.10.0).</b> La revision del modo jugador del 6-oct pide dos
 * pantallas que ahora si los muestran: la entrada a una sala privada («Sala
 * privada · Creada por Perez_Bro15») y la sala de espera con sus plazas
 * («Jugador 1 · Jugador 2 · 1/2»). El apodo NO se lee de la base de cuentas
 * (regla 7): es la foto que la sala ya guarda de cada ficha al entrar, sacada
 * del token ({@link FichaDeParticipante#apodo()}). {@code apodoAnfitrion} viaja
 * siempre; la lista {@code jugadores}, solo a quien esta dentro.
 *
 * <p><b>El codigo de invitacion es la excepcion, y por eso hay dos fabricas.</b>
 * {@link #desde(Sala)} lo omite siempre; {@link #paraElAnfitrion(Sala)} lo
 * incluye. Quien llama tiene que elegir a proposito, con el token delante: es
 * mas dificil filtrar un secreto cuando hay que pedirlo por su nombre que
 * cuando viene puesto y hay que acordarse de quitarlo.
 */
public record SalaResponse(
        UUID id,
        EstadoSala estado,
        Modalidad modalidad,
        int maximoParticipantes,
        int ocupacion,
        int recompensaCreditos,
        boolean incluirHeroeIA,
        int heroesIA,
        boolean privada,
        Integer tamanoEquipo,
        UUID idAnfitrion,
        List<UUID> participantes,
        UUID idPartida,
        Instant creadaEn,

        /*
         * Se omite del JSON cuando es nulo, en vez de salir como
         * "codigoInvitacion": null. Los demas campos nulos si viajan —idPartida
         * es nulo hasta que la partida arranca y el contrato lo declara
         * nullable— pero un secreto es distinto: que el campo ni aparezca deja
         * claro que no hay nada que ver, en lugar de anunciar que existe algo
         * llamado asi y que a esta persona le toco un nulo.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String codigoInvitacion,

        /* Apodo del anfitrion en el momento de abrirla (1.10.0); nulo si la sala no guarda su ficha. */
        String apodoAnfitrion,

        /* Quien hay dentro, con su apodo (1.10.0). Solo a quien esta dentro; a los demas no se les manda. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<JugadorEnSala> jugadores) {

    /**
     * Un jugador dentro de la sala, para las plazas de la sala de espera.
     *
     * @param id        su identificador
     * @param apodo     nombre visible al entrar (foto del token)
     * @param anfitrion si abrio la sala
     * @param heroe     nombre del heroe con el que entro, o nulo si no se conoce
     */
    public record JugadorEnSala(UUID id, String apodo, boolean anfitrion, String heroe) {
    }

    /** La sala sin su codigo de invitacion ni la lista de quien esta dentro. Es lo que ve todo el mundo. */
    static SalaResponse desde(Sala sala) {
        return construir(sala, null, null, false);
    }

    /**
     * La sala con su codigo de invitacion, para que el anfitrion pueda repartirlo.
     *
     * <p>Solo debe usarse cuando el token dice que quien pregunta es el
     * anfitrion. En una sala publica el campo sale nulo igual, porque no hay
     * codigo que dar.
     */
    static SalaResponse paraElAnfitrion(Sala sala) {
        return construir(sala, sala.codigoInvitacion(), null, true);
    }

    /** El codigo solo si quien pregunta es el anfitrion; si no, la sala pelada. */
    static SalaResponse segunQuienPregunta(Sala sala, UUID idJugador) {
        return segunQuienPregunta(sala, idJugador, null);
    }

    /**
     * Como {@link #segunQuienPregunta(Sala, UUID)}, con la partida de la sala si
     * ya arranco (R18): es lo que deja volver al combate tras recargar. Quien
     * esta dentro recibe ademas la lista de jugadores, con sus apodos.
     */
    static SalaResponse segunQuienPregunta(Sala sala, UUID idJugador, UUID idPartida) {
        boolean dentro = idJugador != null && sala.participantes().contains(idJugador);
        return construir(sala, sala.esAnfitrion(idJugador) ? sala.codigoInvitacion() : null, idPartida, dentro);
    }

    private static SalaResponse construir(Sala sala, String codigoInvitacion, UUID idPartida,
                                          boolean conJugadores) {
        FichaDeParticipante delAnfitrion = sala.fichaDe(sala.idAnfitrion());
        return new SalaResponse(
                sala.id(),
                sala.estado(),
                sala.modalidad(),
                sala.maximoParticipantes(),
                sala.ocupacion(),
                sala.recompensaCreditos(),
                sala.incluirHeroeIA(),
                sala.heroesIA(),
                sala.privada(),
                sala.tamanoEquipo(),
                sala.idAnfitrion(),
                List.copyOf(sala.participantes()),
                // Nula hasta que la sala arranca (HU-SAL-004). Despues solo la
                // rellena quien la busca: la consulta de una sala concreta.
                idPartida,
                sala.creadaEn(),
                codigoInvitacion,
                delAnfitrion == null ? null : delAnfitrion.apodo(),
                conJugadores ? jugadoresDe(sala) : null);
    }

    /** En el orden en que entraron; sin ficha (salas anteriores a V7) el apodo no se inventa. */
    private static List<JugadorEnSala> jugadoresDe(Sala sala) {
        Map<UUID, FichaDeParticipante> fichas = sala.fichas();
        return sala.participantes().stream()
                .map(id -> {
                    FichaDeParticipante ficha = fichas.get(id);
                    return new JugadorEnSala(id, ficha == null ? null : ficha.apodo(), sala.esAnfitrion(id),
                            ficha == null || ficha.heroe() == null ? null : ficha.heroe().nombre());
                })
                .toList();
    }
}

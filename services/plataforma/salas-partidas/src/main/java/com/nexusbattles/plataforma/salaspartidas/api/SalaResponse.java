package com.nexusbattles.plataforma.salaspartidas.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de una sala, calcada del esquema {@code Sala} del contrato OpenAPI.
 *
 * <p>Lleva exactamente lo que la interfaz pinta. La {@code Tarjeta de sala} del
 * sistema de diseno tiene dos propiedades — la variante {@code Estado} y una
 * unica linea de texto — y sus ocho ejemplos de la Pantalla 2 la resuelven como
 * «4 de 6 jugadores · 320 creditos · Con heroe de la IA». De ahi salen
 * {@code estado}, {@code ocupacion}, {@code maximoParticipantes},
 * {@code recompensaCreditos} e {@code incluirHeroeIA}, y nada mas.
 *
 * <p>No viaja el apodo de nadie. El apodo pertenece al modulo de cuentas y este
 * servicio no puede leer su base de datos (regla 7 de plataforma) ni tiene
 * motivo para copiarlo: ninguna pantalla de HU-SAL-002 lo muestra. Un
 * participante es un identificador.
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
        String codigoInvitacion) {

    /** La sala sin su codigo de invitacion. Es lo que ve todo el mundo. */
    static SalaResponse desde(Sala sala) {
        return construir(sala, null);
    }

    /**
     * La sala con su codigo de invitacion, para que el anfitrion pueda repartirlo.
     *
     * <p>Solo debe usarse cuando el token dice que quien pregunta es el
     * anfitrion. En una sala publica el campo sale nulo igual, porque no hay
     * codigo que dar.
     */
    static SalaResponse paraElAnfitrion(Sala sala) {
        return construir(sala, sala.codigoInvitacion());
    }

    /** El codigo solo si quien pregunta es el anfitrion; si no, la sala pelada. */
    static SalaResponse segunQuienPregunta(Sala sala, UUID idJugador) {
        return segunQuienPregunta(sala, idJugador, null);
    }

    /**
     * Como {@link #segunQuienPregunta(Sala, UUID)}, con la partida de la sala si
     * ya arranco (R18): es lo que deja volver al combate tras recargar.
     */
    static SalaResponse segunQuienPregunta(Sala sala, UUID idJugador, UUID idPartida) {
        return construir(sala, sala.esAnfitrion(idJugador) ? sala.codigoInvitacion() : null, idPartida);
    }

    private static SalaResponse construir(Sala sala, String codigoInvitacion) {
        return construir(sala, codigoInvitacion, null);
    }

    private static SalaResponse construir(Sala sala, String codigoInvitacion, UUID idPartida) {
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
                codigoInvitacion);
    }
}

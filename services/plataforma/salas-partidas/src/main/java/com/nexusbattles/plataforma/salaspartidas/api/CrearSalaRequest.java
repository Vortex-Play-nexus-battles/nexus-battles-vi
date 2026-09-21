package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;

/**
 * Cuerpo de {@code POST /api/v1/salas}, calcado del esquema
 * {@code CrearSalaRequest} del contrato OpenAPI.
 *
 * <p>Deliberadamente <b>sin anotaciones de validacion de rango</b>. Que los
 * participantes vayan de dos a seis es una regla del juego (RF-JUE-004) y vive
 * en {@code Sala.crear}. Repetirla aqui crearia dos verdades que se
 * desincronizarian, y ademas dejaria la regla fuera de cualquier camino que no
 * fuera este controlador.
 *
 * <p>Los valores por defecto de los booleanos y del entero los pone
 * {@link #aParametros()}, no Jackson, para que un campo ausente y uno en
 * {@code false} signifiquen lo mismo.
 *
 * <p>{@code heroesIA} (contrato 1.2.0, HU-SAL-004) manda sobre
 * {@code incluirHeroeIA} cuando viene: el booleano de RF-JUE-001 se sigue
 * aceptando y vale por una maquina.
 *
 * <p>{@code torneo} (contrato 1.5.0, HU-TOR-004 CA-04) vincula la sala a un
 * encuentro: al terminar la partida el ganador se informa a torneos.
 */
public record CrearSalaRequest(
        Integer maximoParticipantes,
        Modalidad modalidad,
        Integer recompensaCreditos,
        Boolean incluirHeroeIA,
        Boolean privada,
        Integer tamanoEquipo,
        Integer heroesIA,
        EncuentroDeTorneoRequest torneo) {

    /** Compatibilidad con el cuerpo 1.4.0 (sin torneo). */
    public CrearSalaRequest(Integer maximoParticipantes, Modalidad modalidad, Integer recompensaCreditos,
                            Boolean incluirHeroeIA, Boolean privada, Integer tamanoEquipo, Integer heroesIA) {
        this(maximoParticipantes, modalidad, recompensaCreditos, incluirHeroeIA, privada, tamanoEquipo, heroesIA, null);
    }

    /** El encuentro del torneo que esta sala juega: {@code torneoId} y {@code numeroEncuentro} (1..14). */
    public record EncuentroDeTorneoRequest(java.util.UUID torneoId, Integer numeroEncuentro) {
    }

    ParametrosDeSala aParametros() {
        int maquinas = heroesIA != null ? heroesIA : (Boolean.TRUE.equals(incluirHeroeIA) ? 1 : 0);
        return new ParametrosDeSala(
                maximoParticipantes == null ? 0 : maximoParticipantes,
                modalidad,
                recompensaCreditos == null ? 0 : recompensaCreditos,
                maquinas,
                Boolean.TRUE.equals(privada),
                tamanoEquipo);
    }
}

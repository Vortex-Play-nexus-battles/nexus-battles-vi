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
 */
public record CrearSalaRequest(
        Integer maximoParticipantes,
        Modalidad modalidad,
        Integer recompensaCreditos,
        Boolean incluirHeroeIA,
        Boolean privada,
        Integer tamanoEquipo,
        Integer heroesIA) {

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

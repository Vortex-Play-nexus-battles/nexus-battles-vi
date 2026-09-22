package nexus.combate.api;

import nexus.combate.ResolucionAtaque;

/**
 * Cuerpo de la respuesta de {@code POST /api/v1/combate/ataques}.
 *
 * <p>Lleva mas de lo que devuelve el dominio a proposito: {@code ataqueResuelto}
 * y {@code defensaObjetivo} no hacen falta para aplicar el dano, pero sin ellos
 * un {@code danoAplicado} de cero no se distingue de un fallo de integracion.
 * Un combate tiene que ser auditable desde su propia respuesta.
 *
 * @param categoria       efecto sorteado
 * @param danoAplicado    lo que hay que restar a la vida del objetivo
 * @param ataqueResuelto  resultado de la tirada de dados del atacante
 * @param defensaObjetivo defensa contra la que se comparo
 * @param indiceTabla     fila sorteada; nula si no se llego a sortear
 */
public record RespuestaDeAtaque(String categoria, int danoAplicado, int ataqueResuelto,
                                int defensaObjetivo, Integer indiceTabla) {

    static RespuestaDeAtaque de(ResolucionAtaque resolucion, int ataqueResuelto, int defensa) {
        if (resolucion instanceof ResolucionAtaque.ConEfecto conEfecto) {
            return new RespuestaDeAtaque(conEfecto.categoria().name(), conEfecto.danoAplicado(),
                    ataqueResuelto, defensa, conEfecto.indiceTabla());
        }
        // SinEfecto: el ataque no supero la defensa, o la accion protegia a un
        // companero. No se sorteo fila, y decirlo con null es la verdad.
        return new RespuestaDeAtaque("SIN_EFECTO", 0, ataqueResuelto, defensa, null);
    }
}

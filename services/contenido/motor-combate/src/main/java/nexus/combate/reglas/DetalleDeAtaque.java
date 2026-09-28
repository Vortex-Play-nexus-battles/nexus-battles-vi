package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;

/**
 * La tirada de un golpe, para que el combate sea auditable desde su respuesta.
 *
 * @param objetivo        a quien se golpeo
 * @param ataqueResuelto  tirada de ataque, con bonos y penalizaciones
 * @param defensaObjetivo defensa del objetivo, con sus protecciones
 * @param acierta         el ataque supero la defensa (§6.1.4)
 * @param categoria       efecto sorteado ({@code SIN_EFECTO} si no acierta)
 * @param indiceTabla     fila de la tabla, o nula si no hubo tirada
 * @param porcentajeDano  porcentaje aplicado (Tabla 22)
 * @param danoBase        dano antes del porcentaje
 * @param danoAplicado    lo que perdio el objetivo
 */
public record DetalleDeAtaque(String objetivo, int ataqueResuelto, int defensaObjetivo, boolean acierta,
                              CategoriaEfecto categoria, Integer indiceTabla, int porcentajeDano,
                              int danoBase, int danoAplicado) {
}

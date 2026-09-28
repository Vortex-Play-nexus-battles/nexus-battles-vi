package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;

/**
 * Lo que el heroe lleva al combate ademas de su vida — B7, §6.
 *
 * <p>Se captura al ENTRAR a la sala, con la misma consulta a inventario que
 * decide si el heroe puede combatir (SCRUM-1074): el heroe con el que alguien
 * entra es el que apuesta y el que combate. Al empezar la partida solo esta
 * autenticado el anfitrion, y no se vuelve a preguntar por el de nadie.
 *
 * @param nivel        nivel del heroe (§6.1.1); 1 cuando inventario no lo
 *                     publica: todo heroe empieza en el nivel 1
 * @param estadisticas en su nivel y con el equipamiento plano aplicado; nulas
 *                     si no se pudieron componer, y entonces el motor usa las
 *                     del catalogo en ese nivel
 * @param equipamiento nombres de las armas, armaduras e items equipados
 *                     (Tablas 8 a 19): el motor aplica sus efectos de combate
 * @param epicas       nombres de las epicas de la Tabla 20 que el jugador tiene
 */
public record PerfilDeCombate(int nivel, EstadisticasDeCombate estadisticas, List<String> equipamiento,
                              List<String> epicas) {

    public static final int NIVEL_MINIMO = 1;
    public static final int NIVEL_MAXIMO = 8;

    public PerfilDeCombate {
        if (nivel < NIVEL_MINIMO || nivel > NIVEL_MAXIMO) {
            throw new IllegalArgumentException("El nivel va de 1 a 8 (§6.1.1): " + nivel);
        }
        equipamiento = equipamiento == null ? List.of() : List.copyOf(equipamiento);
        epicas = epicas == null ? List.of() : List.copyOf(epicas);
    }

    /** Un heroe del catalogo sin nada encima: el de la maquina (D-B7-11). */
    public static PerfilDeCombate delCatalogo(int nivel) {
        return new PerfilDeCombate(nivel, null, List.of(), List.of());
    }
}

package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Parametros con los que un jugador crea una sala.
 *
 * <p><b>RF-JUE-001:</b> «crear una sala de batalla especificando cuantos jugadores
 * participaran, la posible recompensa y si se incluye un heroe aleatorio
 * controlado por la inteligencia artificial».
 *
 * <p>Es un objeto de entrada sin comportamiento: no se valida a si mismo. La
 * validacion vive en {@link Sala#crear}, porque depende de reglas que cruzan
 * varios campos y del saldo del jugador.
 *
 * @param nombre               nombre visible de la sala
 * @param maximoParticipantes  cuantos jugadores participaran (RF-JUE-001)
 * @param modalidad            modalidad de partida (RF-JUE-004)
 * @param recompensaCreditos   creditos puestos en juego (RF-JUE-001, RF-JUE-014)
 * @param incluirHeroeIA       incluir un heroe aleatorio de la IA (RF-JUE-001)
 * @param privada             si es privada no aparece en el listado publico
 * @param tamanoEquipo        integrantes por equipo; solo en modalidad HASTA_SEIS
 */
public record ParametrosDeSala(
        String nombre,
        int maximoParticipantes,
        Modalidad modalidad,
        int recompensaCreditos,
        boolean incluirHeroeIA,
        boolean privada,
        Integer tamanoEquipo) {

    /** Limites del nombre, identicos a los del contrato OpenAPI. */
    public static final int NOMBRE_MINIMO = 3;
    public static final int NOMBRE_MAXIMO = 60;
}

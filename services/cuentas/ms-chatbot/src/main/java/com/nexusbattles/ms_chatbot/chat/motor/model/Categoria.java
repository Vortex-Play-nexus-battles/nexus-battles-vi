package com.nexusbattles.ms_chatbot.chat.motor.model;

/**
 * Categoría temática de un tema de la base de conocimiento del chatbot.
 * Corresponde 1 a 1 con los 8 objetivos del Chatbot descritos en el
 * documento fuente del proyecto (Proyecto Integrador II, §7.4.1).
 */
public enum Categoria {

    /** Información sobre productos disponibles en el sistema (héroes, habilidades, armas, armaduras, ítems, épicas). */
    PRODUCTO,

    /** Reglas y mecánicas del juego (combate por turnos, progresión, sistema de efectos aleatorios). */
    MECANICA_JUEGO,

    /** Funcionamiento de las diferentes modalidades de juego (Batalla JcJ, Misión JcE, Torneo, Jugar online). */
    MODALIDAD_JUEGO,

    /** Proceso de registro y gestión de cuenta (creación de cuenta, recuperación de contraseña, perfil). */
    CUENTA_Y_REGISTRO,

    /** Sistema de subastas y comercio entre jugadores. */
    SUBASTA_Y_COMERCIO,

    /** Resolución de problemas técnicos comunes y requisitos del sistema. */
    SOPORTE_TECNICO,

    /** Políticas de uso y términos del servicio. */
    POLITICAS_Y_TERMINOS,

    /** Preguntas frecuentes (FAQ) que no encajan en una categoría específica. */
    FAQ_GENERAL
}

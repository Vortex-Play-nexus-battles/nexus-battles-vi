package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;

/**
 * Lo que la sala sabe de quien esta dentro, mas alla de su identificador —
 * HU-SAL-003, HU-SAL-005, SCRUM-1074.
 *
 * <p><b>Por que existe.</b> La puerta de heroe ya consulta al inventario cada
 * vez que alguien entra, y recibe el heroe con el que ese jugador va a combatir.
 * Hasta ahora esa respuesta se usaba para decidir «pasa / no pasa» y se tiraba.
 * El resultado era una partida cuyos participantes no tenian heroe, y una barra
 * de vida que no se podia pintar (RF-JUE-009) sin inventarse los numeros.
 *
 * <p>Se guarda en el momento del ingreso y no se vuelve a pedir al arrancar,
 * por dos razones: al iniciar solo esta autenticado el anfitrion y este
 * servicio no puede preguntar al inventario por el heroe de otro; y aunque
 * pudiera, el heroe con el que alguien entro es el que apuesta, no el que tenga
 * equipado cinco minutos despues.
 *
 * <p><b>Es una foto, no la verdad viva.</b> El dueno del heroe es el inventario
 * y el del apodo es cuentas. Aqui se conserva una copia del momento del
 * ingreso; si el jugador se cambia el apodo a mitad de sala, esta ficha queda
 * vieja y da igual, porque quien manda dentro del servicio es el identificador.
 *
 * @param apodo nombre visible en el momento de entrar; lo exige el esquema
 *              {@code ResumenJugador} del AsyncAPI para poder anunciar el
 *              arranque del combate
 * @param heroe heroe con el que entro, ya validado por el inventario
 */
public record FichaDeParticipante(String apodo, HeroeDeCombate heroe) {

    public FichaDeParticipante {
        Objects.requireNonNull(apodo, "Sin apodo no se puede anunciar al participante.");
        Objects.requireNonNull(heroe, "Un participante sin heroe no deberia haber pasado la puerta.");
    }
}

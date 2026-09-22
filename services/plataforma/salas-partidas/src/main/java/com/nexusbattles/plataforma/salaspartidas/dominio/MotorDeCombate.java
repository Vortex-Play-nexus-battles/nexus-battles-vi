package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Puerto de salida hacia el motor de combate — RF-JUE-006, RF-JUE-017.
 *
 * <p>Una sola pregunta: <i>este heroe ataca a este otro, ¿cuanto dano hace?</i>
 * La respuesta la da el motor, que es su dueno. Este servicio no sabe de dados,
 * ni de tablas de efectos, ni de criticos, y no debe saberlo: el Project Charter
 * excluye el motor de combate de este bloque, y lo que aqui se hace es
 * consumirlo.
 *
 * <p>Por eso el puerto devuelve un veredicto y no datos crudos. Si devolviera la
 * formula de ataque o la fila de la tabla, la regla de «cuanto dano hace esto»
 * acabaria escrita en este servicio, que es justo donde no debe estar.
 *
 * <p>Quien lleva la partida —el turno, la vida, quien gana— sigue siendo este
 * servicio: el motor no guarda nada entre llamadas.
 */
public interface MotorDeCombate {

    /**
     * Resuelve un ataque.
     *
     * @param atacante heroe que ataca, tal como lo conoce el catalogo
     * @param objetivo heroe que recibe
     * @return cuanto dano aplicar y por que
     * @throws MotorNoDisponible si el motor contesta algo que no se puede
     *                           interpretar. No se inventa un resultado: un
     *                           combate decidido con numeros falsos es peor
     *                           que un combate que no avanza.
     * @throws com.nexusbattles.plataforma.resiliencia.DependenciaDegradada
     *                           si el motor no responde: la seccion de
     *                           combate queda degradada (HU-DIS-003) y quien
     *                           llama decide si pasa el turno o se lo dice
     *                           al jugador.
     */
    ResolucionDelMotor resolver(HeroeDeCombate atacante, HeroeDeCombate objetivo);
}

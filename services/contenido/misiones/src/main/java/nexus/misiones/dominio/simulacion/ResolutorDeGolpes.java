package nexus.misiones.dominio.simulacion;

/**
 * Quien resuelve un golpe: el motor de combate ({@code POST
 * /api/v1/combate/ataques}), con su tirada de dados, la comparacion con la
 * defensa y el sorteo sobre la tabla de 8.000 filas (6.1.4). «Motor de combate
 * identico al de batallas en linea» (7.8.12): aqui no hay un segundo motor.
 */
public interface ResolutorDeGolpes {

    /**
     * @param prototipoAtacante nombre del catalogo de heroes («Guerrero Tanque»)
     * @param defensaObjetivo   la defensa del que recibe
     * @param semilla           solo en pruebas reproducibles; nula en juego real
     * @throws SinCapacidadDeAtaque si el atacante es un sanador sin ataque
     */
    Golpe resolver(String prototipoAtacante, int defensaObjetivo, Long semilla);
}

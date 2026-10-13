package nexus.misiones.ia;

/**
 * Lo que el decisor necesita de un modelo: puntuar candidatas. {@link PuntuadorOnnx} es el real (la red propia,
 * entrenada con PyTorch); en las pruebas del decisor se usan otros, deterministas.
 */
public interface Puntuador {

    /**
     * @param caracteristicas una fila por candidata, de {@link Caracteristicas#DIMENSION} numeros
     * @return un puntaje por candidata, en el mismo orden; mayor es mejor
     */
    float[] puntuar(float[][] caracteristicas);

    /** La version del modelo, tal como queda en el evento de combate. */
    String version();
}

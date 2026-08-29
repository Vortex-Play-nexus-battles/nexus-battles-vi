package nexus.dominio;

/**
 * Poder de un heroe durante y fuera de combate (seccion 6.1.1, p. 26):
 * se recupera a razon de dos puntos por turno durante el combate, e
 * instantaneamente al concluir. La recuperacion no supera el maximo del
 * prototipo (lectura razonable del "se recupera": no genera poder extra).
 */
public record EstadoDePoder(int actual, int maximo) {

    public static final int RECUPERACION_POR_TURNO = 2;

    public EstadoDePoder {
        if (actual < 0 || maximo <= 0 || actual > maximo) {
            throw new IllegalArgumentException("Estado de poder inválido.");
        }
    }

    public static EstadoDePoder de(Prototipo prototipo) {
        int maximo = prototipo.estadisticasNivel1().poder();
        return new EstadoDePoder(maximo, maximo);
    }

    public EstadoDePoder recuperarPorTurno() {
        return new EstadoDePoder(Math.min(maximo, actual + RECUPERACION_POR_TURNO), maximo);
    }

    public EstadoDePoder alConcluirCombate() {
        return new EstadoDePoder(maximo, maximo);
    }

    /**
     * Intenta ejecutar una accion. Si el poder no alcanza, la accion no se
     * ejecuta y "el valor de ataque se reduce a su valor base" (regla textual).
     */
    public ResultadoDeAccion usar(Accion accion) {
        if (accion.cuestaTodoElPoder()) {
            if (actual > 0) {
                return new ResultadoDeAccion(true, new EstadoDePoder(0, maximo), false);
            }
            return new ResultadoDeAccion(false, this, true);
        }
        if (actual >= accion.costoPuntos()) {
            return new ResultadoDeAccion(true, new EstadoDePoder(actual - accion.costoPuntos(), maximo), false);
        }
        return new ResultadoDeAccion(false, this, true);
    }
}

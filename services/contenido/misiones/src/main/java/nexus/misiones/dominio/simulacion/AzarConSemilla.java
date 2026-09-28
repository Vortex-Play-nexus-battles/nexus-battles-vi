package nexus.misiones.dominio.simulacion;

import java.util.SplittableRandom;

/**
 * {@link Azar} reproducible: la misma semilla da la misma secuencia. La
 * semilla de cada ejecucion la sortea el servidor al matricular (o la fija la
 * variable de pruebas del banco E2E), nunca el cliente.
 */
public final class AzarConSemilla implements Azar {

    private final SplittableRandom generador;

    public AzarConSemilla(long semilla) {
        this.generador = new SplittableRandom(semilla);
    }

    @Override
    public int entre(int minimo, int maximo) {
        if (maximo < minimo) {
            throw new IllegalArgumentException("El maximo no puede ser menor que el minimo.");
        }
        return generador.nextInt(minimo, maximo + 1);
    }

    @Override
    public boolean acierta(double probabilidad) {
        if (probabilidad <= 0) {
            return false;
        }
        if (probabilidad >= 1) {
            return true;
        }
        return generador.nextDouble() < probabilidad;
    }

    @Override
    public long largo() {
        return generador.nextLong();
    }
}

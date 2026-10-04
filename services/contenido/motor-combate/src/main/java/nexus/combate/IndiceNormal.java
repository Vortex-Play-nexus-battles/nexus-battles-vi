package nexus.combate;

import java.util.random.RandomGenerator;

/**
 * El indice aleatorio de la tabla de 8.000 filas — §6.1.4.
 *
 * <p>El documento lo pide asi: «el indice aleatorio, es una variable
 * pseudo-aleatoria que debe seguir una distribucion normal». Hasta B7 se sacaba
 * una gaussiana y se pasaba por su propia funcion de distribucion, y eso es la
 * transformada integral de probabilidad: el resultado es UNIFORME, normal solo
 * de nombre. Aqui la fila es de verdad {@code round(media + desviacion * Z)}
 * con {@code Z ~ N(0, 1)}, truncada a la tabla por rechazo: una tirada fuera de
 * 1..8000 se repite (recortarla la amontonaria en las filas 1 y 8000, que son
 * «causar dano» y «no causar dano», y cambiaria sus probabilidades).
 *
 * <p><b>Media y desviacion no las fija el documento</b> (decision D-B7-01). Por
 * omision la normal se centra en la tabla (media 4.000,5) y la cubre a tres
 * desviaciones por lado (8.000 / 6), que es la convencion neutra: ninguna fila
 * queda fuera del alcance y la masa truncada es un 0,27 %. Se configuran con
 * {@code MOTOR_INDICE_MEDIA} y {@code MOTOR_INDICE_DESVIACION}.
 *
 * <p><b>Consecuencia que conviene tener presente:</b> con un indice normal, el
 * porcentaje de FILAS de cada efecto (Tablas 21 a 23) no es su probabilidad. El
 * Guerrero Armas tiene el 60 % de las filas en «causar dano» (1-4800), pero con
 * los valores por omision esa franja sale el 72,6 % de las veces, porque rodea
 * el centro. {@link #probabilidadDeFilas} lo calcula, y el contrato lo explica.
 */
public final class IndiceNormal {

    /** Centro de la tabla: la fila media entre 1 y 8.000. */
    public static final double MEDIA_POR_OMISION = (1 + TablaEfectos.TOTAL_FILAS) / 2.0;

    /** Tres desviaciones a cada lado cubren la tabla entera. */
    public static final double DESVIACION_POR_OMISION = TablaEfectos.TOTAL_FILAS / 6.0;

    /** Un techo que en la practica nunca se alcanza: la masa fuera de la tabla es minima. */
    private static final int INTENTOS_MAXIMOS = 10_000;

    private static final IndiceNormal POR_OMISION =
            new IndiceNormal(MEDIA_POR_OMISION, DESVIACION_POR_OMISION);

    private final double media;
    private final double desviacion;

    /**
     * @param media      fila central, dentro de la tabla
     * @param desviacion en filas; mayor que cero
     */
    public IndiceNormal(double media, double desviacion) {
        if (Double.isNaN(media) || media < 1 || media > TablaEfectos.TOTAL_FILAS) {
            throw new IllegalArgumentException(
                    "La media del indice tiene que estar dentro de la tabla (1..8000): " + media);
        }
        if (Double.isNaN(desviacion) || desviacion <= 0) {
            throw new IllegalArgumentException(
                    "La desviacion del indice tiene que ser positiva: " + desviacion);
        }
        this.media = media;
        this.desviacion = desviacion;
    }

    /** La configuracion por omision (media 4.000,5; desviacion 1.333,3). */
    public static IndiceNormal porOmision() {
        return POR_OMISION;
    }

    public double media() {
        return media;
    }

    public double desviacion() {
        return desviacion;
    }

    /** Una fila de 1 a 8.000 con distribucion normal truncada a la tabla. */
    public int generar(RandomGenerator azar) {
        for (int intento = 0; intento < INTENTOS_MAXIMOS; intento++) {
            long fila = Math.round(media + desviacion * azar.nextGaussian());
            if (fila >= 1 && fila <= TablaEfectos.TOTAL_FILAS) {
                return (int) fila;
            }
        }
        throw new IllegalStateException(
                "El indice no cayo dentro de la tabla en " + INTENTOS_MAXIMOS + " intentos");
    }

    /**
     * Probabilidad de que el indice caiga entre {@code desde} y {@code hasta}
     * (ambas incluidas), ya con la truncacion a la tabla.
     */
    public double probabilidadDeFilas(int desde, int hasta) {
        int inicio = Math.max(1, desde);
        int fin = Math.min(TablaEfectos.TOTAL_FILAS, hasta);
        if (fin < inicio) {
            return 0.0;
        }
        double masaDeLaTabla = acumulada(TablaEfectos.TOTAL_FILAS + 0.5) - acumulada(0.5);
        return (acumulada(fin + 0.5) - acumulada(inicio - 0.5)) / masaDeLaTabla;
    }

    private double acumulada(double x) {
        return FuncionNormalEstandar.cdf((x - media) / desviacion);
    }
}

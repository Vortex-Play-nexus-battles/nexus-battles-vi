package nexus.combate.reglas;

import java.util.random.RandomGenerator;

/**
 * El valor de un efecto de la Tabla 7, 20 o de los objetos: un fijo, unos dados
 * o las dos cosas, y el multiplicador de nivel.
 *
 * <p>Tres formas que aparecen en el documento:
 * <ul>
 *   <li>{@code +2}: fijo ({@link #fija}).</li>
 *   <li>{@code +(3d6)}: dados ({@link #dados}), o fijo mas dados
 *       («+2 y +(2d4)», {@link #fijaMasDados}).</li>
 *   <li>{@code (0d4)} y {@code (0dx)}: «cero dados» no significa nada en la
 *       notacion NdM; se lee como una tirada ENTRE CERO Y el numero
 *       ({@link #entreCeroY}, decision D-B7-03). Aparecen en «Bola de hielo» y
 *       en «Pare de fuego».</li>
 * </ul>
 *
 * <p>§6.1.2: las acciones especiales «son afectadas por el multiplicador
 * asociado al nivel»: {@link #porNivel} multiplica el resultado de la tirada.
 */
public record Tirada(int fijo, int dados, int caras, boolean desdeCero, int multiplicador) {

    /** Ningun bono. */
    public static final Tirada NINGUNA = new Tirada(0, 0, 0, false, 1);

    public Tirada {
        if (fijo < 0 || dados < 0 || caras < 0) {
            throw new IllegalArgumentException("Una tirada no admite valores negativos.");
        }
        if ((dados > 0 || desdeCero) && caras < 1) {
            throw new IllegalArgumentException("Un dado necesita al menos una cara.");
        }
        if (multiplicador < 1) {
            throw new IllegalArgumentException("El multiplicador de nivel empieza en 1.");
        }
    }

    public static Tirada fija(int valor) {
        return new Tirada(valor, 0, 0, false, 1);
    }

    public static Tirada dados(int cantidad, int caras) {
        return new Tirada(0, cantidad, caras, false, 1);
    }

    public static Tirada fijaMasDados(int fijo, int cantidad, int caras) {
        return new Tirada(fijo, cantidad, caras, false, 1);
    }

    /** «(0dN)»: un valor entero de 0 a N, los dos incluidos (D-B7-03). */
    public static Tirada entreCeroY(int maximo) {
        return new Tirada(0, 0, maximo, true, 1);
    }

    /** La misma tirada, multiplicada por el nivel (§6.1.2). */
    public Tirada porNivel(int nivel) {
        return new Tirada(fijo, dados, caras, desdeCero, Math.max(1, nivel));
    }

    public boolean esNula() {
        return fijo == 0 && dados == 0 && !desdeCero;
    }

    public int tirar(RandomGenerator azar) {
        if (esNula()) {
            return 0;
        }
        int bruto = fijo;
        if (desdeCero) {
            bruto += azar.nextInt(caras + 1);
        } else {
            for (int i = 0; i < dados; i++) {
                bruto += azar.nextInt(caras) + 1;
            }
        }
        return bruto * multiplicador;
    }
}

package nexus.misiones.dominio.simulacion;

/** Una formula de la Tabla 6 como datos: base + cantidadDados dados de N caras. */
public record Formula(int base, int cantidadDados, int caras) {

    /** El valor medio de la formula: la base mas la media de cada dado, {@code (caras + 1) / 2}. */
    public double esperado() {
        return base + cantidadDados * (caras + 1) / 2.0;
    }
}

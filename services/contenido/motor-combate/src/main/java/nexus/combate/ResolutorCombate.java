package nexus.combate;

public final class ResolutorCombate {

    private ResolutorCombate() {
    }

    public static ResolucionAtaque resolver(int ataqueResuelto, int defensa) {
        if (ataqueResuelto < 0) {
            throw new IllegalArgumentException("El ataque resuelto no puede ser negativo");
        }
        if (defensa < 0) {
            throw new IllegalArgumentException("La defensa no puede ser negativa");
        }

        if (ataqueResuelto <= defensa) {
            return new ResolucionAtaque.SinEfecto();
        }

        throw new UnsupportedOperationException(
            "Criterio 2/3 aun no implementado: pendiente de tabla de efectos "
                + "(R-02/INC-03/INC-06) y decision sobre distribucion del indice (V-01/SUP-01)");
    }
}

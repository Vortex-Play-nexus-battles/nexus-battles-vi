package nexus.combate;

import java.util.random.RandomGenerator;

public final class ResolutorCombate {

    private ResolutorCombate() {
    }

    public static ResolucionAtaque resolver(int ataqueResuelto, int defensa) {
        validar(ataqueResuelto, defensa);

        if (ataqueResuelto <= defensa) {
            return new ResolucionAtaque.SinEfecto();
        }

        throw new UnsupportedOperationException(
            "Use resolverCompleto(...) para el criterio 2/3, "
                + "que necesita la distribucion de efectos del heroe y el generador de indice.");
    }

    public static ResolucionAtaque resolverCompleto(
            int ataqueResuelto,
            int defensa,
            DistribucionEfectos distribucion,
            int danoResuelto,
            RandomGenerator generadorIndice,
            RandomGenerator generadorCritico) {

        validar(ataqueResuelto, defensa);

        if (ataqueResuelto <= defensa) {
            return new ResolucionAtaque.SinEfecto();
        }

        int indice = GeneradorIndiceTabla.generarIndice(generadorIndice);
        CategoriaEfecto categoria = SelectorEfecto.seleccionar(indice, distribucion);
        int danoAplicado = calcularDano(categoria, danoResuelto, generadorCritico);

        return new ResolucionAtaque.ConEfecto(categoria, indice, danoAplicado);
    }

    private static void validar(int ataqueResuelto, int defensa) {
        if (ataqueResuelto < 0) {
            throw new IllegalArgumentException("El ataque resuelto no puede ser negativo");
        }
        if (defensa < 0) {
            throw new IllegalArgumentException("La defensa no puede ser negativa");
        }
    }

    private static int calcularDano(CategoriaEfecto categoria, int danoResuelto, RandomGenerator generador) {
        if (categoria == CategoriaEfecto.CAUSAR_DANO_CRITICO) {
            int rango = categoria.porcentajeDanoMaximo() - categoria.porcentajeDanoMinimo();
            int porcentaje = categoria.porcentajeDanoMinimo() + generador.nextInt(rango + 1);
            return danoResuelto * porcentaje / 100;
        }
        return danoResuelto * categoria.porcentajeDanoMinimo() / 100;
    }
}

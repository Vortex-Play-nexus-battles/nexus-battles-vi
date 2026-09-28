package nexus.combate;

import java.util.Objects;
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

    /**
     * Resuelve una accion aplicando primero la proteccion de aliados de
     * HU-JUE-008. La sobrecarga original se conserva para no cambiar el
     * contrato de las historias anteriores.
     */
    public static ResolucionAtaque resolverCompleto(
            ContextoAccion contextoAccion,
            int ataqueResuelto,
            int defensa,
            DistribucionEfectos distribucion,
            int danoResuelto,
            RandomGenerator generadorIndice,
            RandomGenerator generadorCritico) {

        return resolverConIndice(IndiceNormal.porOmision(), contextoAccion, ataqueResuelto, defensa,
                distribucion, danoResuelto, generadorIndice, generadorCritico);
    }

    /**
     * Como la anterior, con la media y la desviacion del indice configuradas
     * (D-B7-01). Nombre propio y no una sobrecarga mas: con un {@code null}
     * como primer argumento, dos sobrecargas de siete parametros serian ambiguas.
     */
    public static ResolucionAtaque resolverConIndice(
            IndiceNormal indiceNormal,
            ContextoAccion contextoAccion,
            int ataqueResuelto,
            int defensa,
            DistribucionEfectos distribucion,
            int danoResuelto,
            RandomGenerator generadorIndice,
            RandomGenerator generadorCritico) {

        Objects.requireNonNull(contextoAccion, "contextoAccion no puede ser nulo");
        validar(ataqueResuelto, defensa);

        if (contextoAccion.protegeACompanero()) {
            return new ResolucionAtaque.SinEfecto();
        }

        return resolverConIndice(indiceNormal,
                ataqueResuelto, defensa, distribucion, danoResuelto,
                generadorIndice, generadorCritico);
    }

    public static ResolucionAtaque resolverCompleto(
            int ataqueResuelto,
            int defensa,
            DistribucionEfectos distribucion,
            int danoResuelto,
            RandomGenerator generadorIndice,
            RandomGenerator generadorCritico) {

        return resolverConIndice(IndiceNormal.porOmision(), ataqueResuelto, defensa, distribucion,
                danoResuelto, generadorIndice, generadorCritico);
    }

    /**
     * Resuelve el golpe con el indice dado. La fila de la tabla de 8.000 sale
     * de una normal de verdad (§6.1.4, {@link IndiceNormal}).
     */
    public static ResolucionAtaque resolverConIndice(
            IndiceNormal indiceNormal,
            int ataqueResuelto,
            int defensa,
            DistribucionEfectos distribucion,
            int danoResuelto,
            RandomGenerator generadorIndice,
            RandomGenerator generadorCritico) {

        Objects.requireNonNull(indiceNormal, "indiceNormal no puede ser nulo");
        validar(ataqueResuelto, defensa);

        if (ataqueResuelto <= defensa) {
            return new ResolucionAtaque.SinEfecto();
        }

        int indice = indiceNormal.generar(generadorIndice);
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

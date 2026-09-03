package nexus.combate;

public sealed interface ResolucionAtaque {

    record SinEfecto() implements ResolucionAtaque {
    }

    record ConEfecto(CategoriaEfecto categoria, int indiceTabla, int danoAplicado) implements ResolucionAtaque {
    }
}

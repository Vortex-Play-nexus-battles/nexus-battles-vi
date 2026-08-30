package nexus.combate;

public sealed interface ResolucionAtaque {

    record SinEfecto() implements ResolucionAtaque {
    }

    record ConEfecto(int indiceTabla) implements ResolucionAtaque {
    }
}

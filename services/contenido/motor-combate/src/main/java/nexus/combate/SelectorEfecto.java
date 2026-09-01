package nexus.combate;

public final class SelectorEfecto {

    private SelectorEfecto() {
    }

    public static CategoriaEfecto seleccionar(int indiceFila, DistribucionEfectos distribucion) {
        return TablaEfectos.desde(distribucion).efectoEn(indiceFila);
    }
}

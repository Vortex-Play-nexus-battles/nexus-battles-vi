package nexus.combate;

public enum CategoriaEfecto {

    CAUSAR_DANO(100, 100),
    CAUSAR_DANO_CRITICO(120, 180),
    EVADIR_EL_GOLPE(80, 80),
    RESISTIR_EL_GOLPE(60, 60),
    ESCAPAR_AL_GOLPE(20, 20),
    SIN_EFECTO(0, 0);

    private final int porcentajeDanoMinimo;
    private final int porcentajeDanoMaximo;

    CategoriaEfecto(int porcentajeDanoMinimo, int porcentajeDanoMaximo) {
        this.porcentajeDanoMinimo = porcentajeDanoMinimo;
        this.porcentajeDanoMaximo = porcentajeDanoMaximo;
    }

    public int porcentajeDanoMinimo() {
        return porcentajeDanoMinimo;
    }

    public int porcentajeDanoMaximo() {
        return porcentajeDanoMaximo;
    }
}

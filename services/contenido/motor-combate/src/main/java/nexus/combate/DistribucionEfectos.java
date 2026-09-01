package nexus.combate;

public record DistribucionEfectos(
        int causarDano,
        int causarDanoCritico,
        int evadirElGolpe,
        int resistirElGolpe,
        int escaparAlGolpe,
        int sinEfecto) {

    public DistribucionEfectos {
        int suma = causarDano + causarDanoCritico + evadirElGolpe
                + resistirElGolpe + escaparAlGolpe + sinEfecto;
        if (suma != 100) {
            throw new IllegalArgumentException(
                "Los porcentajes de la distribucion deben sumar 100, suman " + suma);
        }
    }

    public static final DistribucionEfectos GUERRERO_TANQUE = new DistribucionEfectos(40, 0, 5, 0, 5, 50);
    public static final DistribucionEfectos GUERRERO_ARMAS = new DistribucionEfectos(60, 5, 3, 0, 2, 30);
    public static final DistribucionEfectos MAGO_FUEGO = new DistribucionEfectos(70, 5, 0, 5, 0, 20);
    public static final DistribucionEfectos MAGO_HIELO = new DistribucionEfectos(70, 6, 0, 4, 0, 20);
    public static final DistribucionEfectos PICARO_VENENO = new DistribucionEfectos(55, 10, 0, 0, 0, 35);
    public static final DistribucionEfectos PICARO_MACHETE = new DistribucionEfectos(60, 8, 0, 0, 2, 30);
}

package nexus.combate;

import java.util.List;
import java.util.Objects;

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
        if (causarDano < 0 || causarDanoCritico < 0 || evadirElGolpe < 0
                || resistirElGolpe < 0 || escaparAlGolpe < 0 || sinEfecto < 0) {
            throw new IllegalArgumentException("Los porcentajes no pueden ser negativos");
        }
        if (suma != 100) {
            throw new IllegalArgumentException(
                "Los porcentajes de la distribucion deben sumar 100, suman " + suma);
        }
    }

    public DistribucionEfectos aplicarEquipamiento(List<EfectoEquipamiento> efectos) {
        Objects.requireNonNull(efectos, "efectos no puede ser nulo");
        int aumentoCausarDano = aumento(efectos, CategoriaEfecto.CAUSAR_DANO);
        int aumentoCritico = aumento(efectos, CategoriaEfecto.CAUSAR_DANO_CRITICO);
        int aumentoEvasion = aumento(efectos, CategoriaEfecto.EVADIR_EL_GOLPE);
        int aumentoResistencia = aumento(efectos, CategoriaEfecto.RESISTIR_EL_GOLPE);
        int aumentoEscape = aumento(efectos, CategoriaEfecto.ESCAPAR_AL_GOLPE);
        int aumentoTotal = aumentoCausarDano + aumentoCritico + aumentoEvasion
                + aumentoResistencia + aumentoEscape;
        if (aumentoTotal > sinEfecto) {
            throw new IllegalArgumentException(
                    "El equipamiento no puede descontar mas probabilidad de la disponible");
        }
        return new DistribucionEfectos(
                causarDano + aumentoCausarDano,
                causarDanoCritico + aumentoCritico,
                evadirElGolpe + aumentoEvasion,
                resistirElGolpe + aumentoResistencia,
                escaparAlGolpe + aumentoEscape,
                sinEfecto - aumentoTotal);
    }

    private static int aumento(List<EfectoEquipamiento> efectos, CategoriaEfecto categoria) {
        return efectos.stream()
                .map(efecto -> Objects.requireNonNull(efecto, "un efecto no puede ser nulo"))
                .filter(efecto -> efecto.categoria() == categoria)
                .mapToInt(EfectoEquipamiento::puntosPorcentuales)
                .sum();
    }

    public static final DistribucionEfectos GUERRERO_TANQUE = new DistribucionEfectos(40, 0, 5, 0, 5, 50);
    public static final DistribucionEfectos GUERRERO_ARMAS = new DistribucionEfectos(60, 5, 3, 0, 2, 30);
    public static final DistribucionEfectos MAGO_FUEGO = new DistribucionEfectos(70, 5, 0, 5, 0, 20);
    public static final DistribucionEfectos MAGO_HIELO = new DistribucionEfectos(70, 6, 0, 4, 0, 20);
    public static final DistribucionEfectos PICARO_VENENO = new DistribucionEfectos(55, 10, 0, 0, 0, 35);
    public static final DistribucionEfectos PICARO_MACHETE = new DistribucionEfectos(60, 8, 0, 0, 2, 30);
}

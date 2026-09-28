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

    /**
     * Suma (o resta) puntos de critico moviendolos desde (o hacia) «no causar
     * dano» — Tabla 23: «Todo efecto que aumente en valor restara a no causar
     * dano». Lo usan el equipo del atacante (+1 %, +3 %...), las epicas, el
     * bono de critico de Segundo impulso y el Baculo de Permafrost del
     * defensor (-2 % al critico del oponente).
     *
     * <p>A diferencia de {@link #aplicarEquipamiento}, que rechaza un aumento
     * imposible, esto se ACOTA: en combate se combinan efectos de varias
     * fuentes y ninguno puede dejar un porcentaje negativo. El tope de un
     * aumento es lo que quede en «no causar dano»; el de una resta, el critico
     * que haya.
     */
    public DistribucionEfectos ajustarCritico(int puntos) {
        int efectivo = puntos >= 0 ? Math.min(puntos, sinEfecto) : -Math.min(-puntos, causarDanoCritico);
        if (efectivo == 0) {
            return this;
        }
        return new DistribucionEfectos(causarDano, causarDanoCritico + efectivo, evadirElGolpe,
                resistirElGolpe, escaparAlGolpe, sinEfecto - efectivo);
    }

    /**
     * La fila de la Tabla 21 de un prototipo del catalogo de heroes.
     *
     * <p>La tabla esta escrita por TIPO DE HEROE («Guerrero Tanque», «Mago
     * Fuego»...) y el tipo de heroe ES el prototipo del catalogo. Los sanadores
     * no tienen fila: la Tabla 21 les da 0 % en todo porque no atacan. Un
     * prototipo nuevo que el catalogo anada sin su fila tampoco: el motor no
     * inventa porcentajes, y quien lo use no podra atacar hasta que se definan.
     *
     * @param prototipo nombre del catalogo; tolera tildes y mayusculas
     */
    public static java.util.Optional<DistribucionEfectos> dePrototipo(String prototipo) {
        if (prototipo == null) {
            return java.util.Optional.empty();
        }
        String clave = java.text.Normalizer.normalize(prototipo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", "_");
        return java.util.Optional.ofNullable(switch (clave) {
            case "GUERRERO_TANQUE" -> GUERRERO_TANQUE;
            case "GUERRERO_ARMAS" -> GUERRERO_ARMAS;
            case "MAGO_FUEGO" -> MAGO_FUEGO;
            case "MAGO_HIELO" -> MAGO_HIELO;
            case "PICARO_VENENO" -> PICARO_VENENO;
            case "PICARO_MACHETE" -> PICARO_MACHETE;
            default -> null;
        });
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

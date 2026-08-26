package nexus.dominio;

/**
 * Heroe de un jugador. Reglas de progresion de la seccion 6.1.1, p. 26:
 * "El nivel inicial de todos los personajes es uno (1) y puede incrementarse
 * hasta el nivel 8"; "Experiencia = 100 x 1,2^(Nivel - 1)".
 */
public record Heroe(Prototipo prototipo, int nivel, double experiencia, Estadisticas estadisticas) {

    public static final int NIVEL_MAXIMO = 8;

    public static Heroe crear(Prototipo prototipo) {
        return new Heroe(prototipo, 1, 0, prototipo.estadisticasNivel1());
    }

    /** Experiencia requerida para pasar del nivel dado al siguiente (formula textual del documento). */
    public static double experienciaRequerida(int nivel) {
        return 100 * Math.pow(1.2, nivel - 1d);
    }

    /**
     * HU-HER-004: "La derrota de un enemigo no jugador otorga 10 x 1,2^(1d8)
     * puntos de experiencia" (seccion 6.1.1, p. 26, con su nota del proyecto).
     * Recibe el resultado del dado para ser determinista y probable; el
     * lanzamiento aleatorio lo hace la mecanica del combate.
     */
    public static double experienciaPorEnemigoDerrotado(int resultadoDelDado) {
        if (resultadoDelDado < 1 || resultadoDelDado > 8) {
            throw new IllegalArgumentException("El resultado de un 1d8 está entre 1 y 8.");
        }
        return 10 * Math.pow(1.2, resultadoDelDado);
    }

    /**
     * HU-HER-008: "el nivel actua como factor multiplicador en las demas
     * estadisticas" (seccion 6.1.1, p. 26). Ejemplo textual del cliente: un mago
     * de fuego de nivel 3 posee un ataque base de 30. Se multiplican las bases;
     * los dados no se escalan.
     */
    public Estadisticas estadisticasActuales() {
        return prototipo.estadisticasNivel1().escaladaPor(nivel);
    }

    /** HU-HER-007: el efecto de las acciones especiales se multiplica por el nivel. */
    public int multiplicadorDeEfecto() {
        return nivel;
    }

    /**
     * Acumula experiencia y sube de nivel cada vez que se alcanza el umbral del
     * nivel actual. El sobrante se conserva para el siguiente nivel (el documento
     * define el umbral por nivel; la conservacion del sobrante es decision de
     * implementacion). En el nivel 8 la experiencia se sigue acumulando sin subir.
     */
    /**
     * RC-01 (RG-021, dictada en clase el 2026-07-29, ausente del PDF): la primera
     * accion se tiene desde el nivel 1, la segunda se aprende en el 4 y la tercera
     * en el 8. El orden de desbloqueo es el orden de la Tabla 7.
     */
    public static final java.util.List<Integer> NIVELES_DE_DESBLOQUEO = java.util.List.of(1, 4, 8);

    public java.util.List<Accion> accionesDisponibles() {
        int desbloqueadas = 0;
        for (int umbral : NIVELES_DE_DESBLOQUEO) {
            if (nivel >= umbral) {
                desbloqueadas++;
            }
        }
        return prototipo.acciones().subList(0, desbloqueadas);
    }

    public Heroe ganarExperiencia(double puntos) {
        if (puntos < 0) {
            throw new IllegalArgumentException("La experiencia ganada no puede ser negativa.");
        }
        double acumulada = experiencia + puntos;
        int nuevoNivel = nivel;
        while (nuevoNivel < NIVEL_MAXIMO && acumulada >= experienciaRequerida(nuevoNivel)) {
            acumulada -= experienciaRequerida(nuevoNivel);
            nuevoNivel++;
        }
        return new Heroe(prototipo, nuevoNivel, acumulada, estadisticas);
    }
}

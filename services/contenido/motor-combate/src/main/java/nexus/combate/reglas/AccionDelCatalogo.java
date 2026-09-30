package nexus.combate.reglas;

/**
 * Una accion de la Tabla 7 tal como la publica el catalogo de heroes
 * ({@code heroes.yaml} 1.2.0): su coste, su carga y el nivel desde el que se
 * tiene. Los DATOS son del catalogo; lo que la accion HACE lo sabe
 * {@link Reglamento}.
 *
 * @param nombre         nombre del documento («Golpe con escudo»)
 * @param costoPoder     puntos de poder; nulo si cuesta todo el poder
 * @param todoElPoder    consume todo el poder (Reanimacion)
 * @param turnosDeCarga  turnos propios de carga tras usarla (§6.1.2: uno)
 * @param nivelRequerido nivel desde el que se tiene (RC-01: 1, 4 u 8)
 */
public record AccionDelCatalogo(String nombre, Integer costoPoder, boolean todoElPoder,
                                int turnosDeCarga, int nivelRequerido) {

    public AccionDelCatalogo {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una accion necesita su nombre.");
        }
        if (!todoElPoder && (costoPoder == null || costoPoder < 0)) {
            throw new IllegalArgumentException("La accion " + nombre + " necesita su coste de poder.");
        }
        if (turnosDeCarga < 0 || nivelRequerido < 1) {
            throw new IllegalArgumentException("Carga o nivel requerido fuera de rango en " + nombre + ".");
        }
    }

    /** Si el poder alcanza para ejecutarla (§6.1.1). «Todo el poder» exige tener algo. */
    public boolean alcanzaCon(int poder) {
        return todoElPoder ? poder > 0 : poder >= costoPoder;
    }
}

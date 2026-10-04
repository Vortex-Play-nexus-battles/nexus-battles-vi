package nexus.misiones.dominio;

/**
 * Una fila de la Tabla 20: la epica de un tipo de heroe y la «probabilidad de
 * Master en una mision» (0,04 % para Guerrero Tanque...). «Cada tipo de heroe
 * tiene Master asociados con epicas especificas» (7.8.4): el Master que puede
 * salirle a un heroe es el de SU tipo, y su prototipo es ese mismo tipo.
 *
 * @param probabilidadPorcentaje tal como la escribe la tabla: 0,04 = 0,04 %
 */
public record EpicaDeTabla20(String prototipo, Epica epica, double probabilidadPorcentaje) {

    public EpicaDeTabla20 {
        if (prototipo == null || prototipo.isBlank()) {
            throw new IllegalArgumentException("Cada fila de la Tabla 20 es de un tipo de heroe.");
        }
        if (epica == null) {
            throw new IllegalArgumentException("La fila de «" + prototipo + "» no tiene epica.");
        }
        if (probabilidadPorcentaje < 0 || probabilidadPorcentaje > 100) {
            throw new IllegalArgumentException("Un porcentaje va de 0 a 100.");
        }
    }

    /**
     * El Master de esta fila. No tiene nombre propio en el documento: se le
     * llama por su tipo, sin inventarle uno.
     */
    public MasterDeMision comoMaster() {
        return new MasterDeMision("Master afin a " + prototipo, prototipo,
                probabilidadPorcentaje / 100.0, epica);
    }
}

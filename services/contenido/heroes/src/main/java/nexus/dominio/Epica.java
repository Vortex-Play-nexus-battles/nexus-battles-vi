package nexus.dominio;

/**
 * Habilidad epica de la Tabla 20 (epicas.md, seccion 6.1.2, p. 32): un efecto
 * general para todos los heroes (null cuando la tabla dice "No aplica") y un
 * efecto potenciado exclusivo del tipo de heroe afin. La probabilidad de
 * aparicion del Master que la ensena es dato para el modulo de misiones.
 */
public record Epica(
        String nombre,
        String tipoDeHeroeAfin,
        String efectoGeneral,
        String efectoPotenciado,
        double probabilidadDeMasterPorcentaje) {

    /** Efectos que recibe un heroe concreto al usarla (HU-HER-010). */
    public Efectos efectosPara(Prototipo heroe) {
        boolean esAfin = heroe.nombre().equals(tipoDeHeroeAfin);
        return new Efectos(efectoGeneral, esAfin ? efectoPotenciado : null);
    }

    public record Efectos(String general, String potenciado) {
    }
}

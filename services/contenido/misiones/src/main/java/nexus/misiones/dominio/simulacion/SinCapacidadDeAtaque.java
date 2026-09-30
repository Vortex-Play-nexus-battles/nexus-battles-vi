package nexus.misiones.dominio.simulacion;

/**
 * El atacante no tiene formula de ataque: es un sanador (el motor responde
 * 422, 6.1.1: «le esta vedado infligir dano»). En la simulacion su golpe hace
 * cero, que es literalmente lo que dice el documento.
 */
public class SinCapacidadDeAtaque extends RuntimeException {

    public SinCapacidadDeAtaque(String prototipo) {
        super(prototipo + " no tiene formula de ataque.");
    }
}

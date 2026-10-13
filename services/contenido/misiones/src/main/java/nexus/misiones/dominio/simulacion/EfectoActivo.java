package nexus.misiones.dominio.simulacion;

/** Un efecto que lleva un combatiente (sangrado, proteccion, bono...), tal como lo guarda el motor. */
public record EfectoActivo(String codigo, String nombre, String tipo, int valor, int turnos, boolean hastaSuTurno,
                           String origen) {
}

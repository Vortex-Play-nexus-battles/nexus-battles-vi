package nexus.combate;

public record DetalleAtaque(int base, int cantidadDados, int caras) {

    public DetalleAtaque {
        if (base < 0) {
            throw new IllegalArgumentException("El valor base del ataque no puede ser negativo");
        }
        if (cantidadDados < 0) {
            throw new IllegalArgumentException("La cantidad de dados no puede ser negativa");
        }
        if (cantidadDados > 0 && caras <= 0) {
            throw new IllegalArgumentException("Un dado necesita al menos una cara");
        }
    }
}

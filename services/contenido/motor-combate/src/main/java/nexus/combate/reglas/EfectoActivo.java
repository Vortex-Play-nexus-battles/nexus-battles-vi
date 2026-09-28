package nexus.combate.reglas;

import java.util.Objects;

/**
 * Un efecto que lleva encima un combatiente.
 *
 * @param codigo identificador estable (p. ej. {@code CONO_DE_HIELO}); un mismo
 *               codigo del mismo origen no se acumula: se renueva
 * @param nombre como lo nombra el documento («Cono de hielo»)
 * @param tipo   que hace, y con ello cuando se descuenta (ver {@link TipoDeEfecto})
 * @param valor  magnitud: puntos, o puntos de porcentaje en {@code BONO_CRITICO}
 * @param turnos turnos propios del portador que le quedan (o actuaciones, en
 *               los efectos por turno); sin uso en protecciones y vinculos
 * @param origen quien lo causo, o nulo
 */
public record EfectoActivo(String codigo, String nombre, TipoDeEfecto tipo, int valor, int turnos,
                           String origen) {

    public EfectoActivo {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Un efecto necesita su codigo.");
        }
        Objects.requireNonNull(tipo, "Un efecto necesita su tipo.");
        nombre = nombre == null || nombre.isBlank() ? codigo : nombre;
        if (valor < 0) {
            throw new IllegalArgumentException("El valor de un efecto no puede ser negativo.");
        }
        if (turnos < 0) {
            throw new IllegalArgumentException("Los turnos de un efecto no pueden ser negativos.");
        }
    }

    /** Termina al empezar el siguiente turno del portador (protecciones). */
    public boolean hastaSuTurno() {
        return tipo.familia() == TipoDeEfecto.Familia.PROTECCION;
    }

    /** El mismo efecto con un turno menos. */
    public EfectoActivo descontado() {
        return new EfectoActivo(codigo, nombre, tipo, valor, Math.max(0, turnos - 1), origen);
    }
}

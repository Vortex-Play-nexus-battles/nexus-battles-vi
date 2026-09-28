package nexus.combate.api;

import nexus.combate.TiradorDados;
import nexus.combate.reglas.MotorDeAcciones;
import nexus.combate.reglas.SolicitudDeAccion;
import nexus.combate.reglas.SolicitudDeTurno;

import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * {@code POST /combate/acciones} y {@code POST /combate/turnos}: traduce el
 * contrato, elige el generador y llama al {@link MotorDeAcciones}.
 *
 * <p>No decide ninguna regla: si apareciera aqui un numero de juego, estaria en
 * el sitio equivocado. Java puro, como {@link ResolverAtaque}: el arranque lo
 * instancia a mano.
 */
public class ServicioDeCombate {

    private final MotorDeAcciones motor;
    private final Supplier<RandomGenerator> azarDeProduccion;

    public ServicioDeCombate(MotorDeAcciones motor) {
        this(motor, TiradorDados::generadorProduccion);
    }

    ServicioDeCombate(MotorDeAcciones motor, Supplier<RandomGenerator> azarDeProduccion) {
        this.motor = Objects.requireNonNull(motor, "Sin motor no hay combate.");
        this.azarDeProduccion = Objects.requireNonNull(azarDeProduccion);
    }

    public RespuestaDeAccion resolver(PeticionDeAccion peticion) {
        if (peticion == null) {
            throw new PeticionInvalida("Hace falta un cuerpo de peticion.");
        }
        if (vacio(peticion.accion())) {
            throw new PeticionInvalida("Hace falta la accion.");
        }
        if (vacio(peticion.ejecutor())) {
            throw new PeticionInvalida("Hace falta el combatiente que actua (ejecutor).");
        }
        SolicitudDeAccion solicitud = new SolicitudDeAccion(peticion.accion(), peticion.ejecutor(),
                vacio(peticion.objetivo()) ? null : peticion.objetivo(),
                Boolean.TRUE.equals(peticion.porEquipos()),
                TraductorDeCombate.aDominio(peticion.combatientes(), 2));
        return RespuestaDeAccion.de(motor.resolver(solicitud, azar(peticion.semilla())));
    }

    public RespuestaDeTurno iniciarTurno(PeticionDeTurno peticion) {
        if (peticion == null) {
            throw new PeticionInvalida("Hace falta un cuerpo de peticion.");
        }
        if (vacio(peticion.combatiente())) {
            throw new PeticionInvalida("Hace falta el combatiente que empieza su turno.");
        }
        SolicitudDeTurno solicitud = new SolicitudDeTurno(peticion.combatiente(),
                Boolean.TRUE.equals(peticion.porEquipos()),
                TraductorDeCombate.aDominio(peticion.combatientes(), 1));
        return RespuestaDeTurno.de(motor.iniciarTurno(solicitud, azar(peticion.semilla())));
    }

    /**
     * {@code SecureRandom} salvo que la peticion traiga semilla, que existe solo
     * para reproducir un combate en una prueba: un generador predecible convierte
     * el critico en un tramite.
     */
    private RandomGenerator azar(Long semilla) {
        return semilla == null ? azarDeProduccion.get() : new Random(semilla);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}

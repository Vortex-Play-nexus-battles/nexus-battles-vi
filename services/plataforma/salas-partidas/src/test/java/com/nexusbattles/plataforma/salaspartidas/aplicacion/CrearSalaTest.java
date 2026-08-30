package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caso de uso de creacion de sala — HU-SAL-001.
 *
 * <p>Aqui no se repiten las reglas del juego: eso es {@code SalaTest}. Lo que se
 * prueba es la coordinacion — que se reserve antes de guardar, que no se moleste
 * al modulo de creditos cuando no hay nada que reservar, y que una sala que no
 * llega a guardarse devuelva los creditos.
 */
@DisplayName("CrearSala · caso de uso (HU-SAL-001)")
class CrearSalaTest {

    private static final UUID ANFITRION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private RepositorioDeSalasEnMemoria repositorio;
    private CreditosDeMentira creditos;
    private CrearSala crearSala;

    /** Doble del puerto de creditos: reserva de verdad contra un saldo en memoria. */
    private static final class CreditosDeMentira implements CreditosDelJugador {
        private int saldo = 10_000;
        private final List<ReservaDeCreditos> reservas = new ArrayList<>();
        private final List<UUID> liberadas = new ArrayList<>();

        @Override
        public ReservaDeCreditos reservar(UUID idJugador, int cantidad, UUID idSala) {
            if (saldo < cantidad) {
                throw new CreditosInsuficientes(saldo, cantidad);
            }
            saldo -= cantidad; // atomico: comprobar y descontar de una vez
            ReservaDeCreditos reserva = new ReservaDeCreditos(UUID.randomUUID(), cantidad);
            reservas.add(reserva);
            return reserva;
        }

        @Override
        public void liberar(UUID idReserva) {
            liberadas.add(idReserva);
            reservas.stream()
                    .filter(r -> r.id().equals(idReserva))
                    .findFirst()
                    .ifPresent(r -> saldo += r.creditos());
        }
    }

    /** Repositorio que siempre falla, para probar la compensacion. */
    private static final class RepositorioRoto implements RepositorioDeSalas {
        @Override
        public Sala guardar(Sala sala) {
            throw new IllegalStateException("La base de datos no responde.");
        }

        @Override
        public Optional<Sala> buscarPorId(UUID id) {
            return Optional.empty();
        }
    }

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioDeSalasEnMemoria();
        creditos = new CreditosDeMentira();
        crearSala = new CrearSala(repositorio, creditos);
    }

    private static ParametrosDeSala validos() {
        return new ParametrosDeSala("Duelo en el Nexo", 4, Modalidad.HASTA_SEIS,
                400, false, false, null);
    }

    @Test
    @DisplayName("guarda la sala y reserva la recompensa")
    void guardaYReserva() {
        Sala sala = crearSala.ejecutar(validos(), ANFITRION);

        assertAll(
                () -> assertEquals(1, repositorio.cuantasHay()),
                () -> assertTrue(repositorio.buscarPorId(sala.id()).isPresent()),
                () -> assertEquals(EstadoSala.ABIERTA, sala.estado()),
                () -> assertEquals(1, creditos.reservas.size()),
                () -> assertEquals(400, creditos.reservas.get(0).creditos()),
                () -> assertEquals(9_600, creditos.saldo, "la reserva descuenta de verdad"));
    }

    @Test
    @DisplayName("dos salas seguidas agotan el saldo: la reserva descuenta, no solo consulta")
    void laReservaDescuenta() {
        creditos.saldo = 500;

        crearSala.ejecutar(validos(), ANFITRION);

        CreditosInsuficientes error = assertThrows(CreditosInsuficientes.class,
                () -> crearSala.ejecutar(validos(), ANFITRION));

        assertAll(
                () -> assertEquals(100, error.disponibles()),
                () -> assertEquals(400, error.requeridos()),
                () -> assertEquals(1, repositorio.cuantasHay(), "solo la primera sala existe"));
    }

    @Test
    @DisplayName("una sala sin recompensa no molesta al modulo de creditos")
    void sinRecompensaNoReserva() {
        ParametrosDeSala gratis = new ParametrosDeSala("Amistosa", 4, Modalidad.HASTA_SEIS,
                0, false, false, null);

        crearSala.ejecutar(gratis, ANFITRION);

        assertAll(
                () -> assertEquals(1, repositorio.cuantasHay()),
                () -> assertTrue(creditos.reservas.isEmpty()));
    }

    @Test
    @DisplayName("con parametros invalidos no reserva ni escribe nada")
    void parametrosInvalidos() {
        ParametrosDeSala invalidos = new ParametrosDeSala("ab", 9, Modalidad.HASTA_SEIS,
                -5, false, false, null);

        assertThrows(ParametrosInvalidos.class, () -> crearSala.ejecutar(invalidos, ANFITRION));

        assertAll(
                () -> assertEquals(0, repositorio.cuantasHay()),
                () -> assertTrue(creditos.reservas.isEmpty(), "ni se pregunta por los creditos"),
                () -> assertEquals(10_000, creditos.saldo));
    }

    @Test
    @DisplayName("acumula todos los errores de parametros en un solo rechazo")
    void acumulaLosErrores() {
        ParametrosDeSala invalidos = new ParametrosDeSala("ab", 9, Modalidad.HASTA_SEIS,
                -5, false, false, null);

        ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                () -> crearSala.ejecutar(invalidos, ANFITRION));

        assertEquals(3, error.errores().size(),
                "nombre, maximoParticipantes y recompensaCreditos, de una sola vez");
    }

    @Test
    @DisplayName("sin saldo suficiente no se escribe la sala")
    void saldoInsuficiente() {
        creditos.saldo = 240;

        CreditosInsuficientes error = assertThrows(CreditosInsuficientes.class,
                () -> crearSala.ejecutar(validos(), ANFITRION));

        assertAll(
                () -> assertEquals(240, error.disponibles()),
                () -> assertEquals(400, error.requeridos()),
                () -> assertEquals(422, error.estado()),
                () -> assertEquals(0, repositorio.cuantasHay()));
    }

    @Test
    @DisplayName("si la sala no se puede guardar, los creditos vuelven al jugador")
    void devuelveLosCreditosSiFallaAlGuardar() {
        CrearSala conRepositorioRoto = new CrearSala(new RepositorioRoto(), creditos);

        assertThrows(IllegalStateException.class,
                () -> conRepositorioRoto.ejecutar(validos(), ANFITRION));

        assertAll(
                () -> assertEquals(1, creditos.liberadas.size(), "se libero la reserva"),
                () -> assertEquals(10_000, creditos.saldo, "el jugador recupera su saldo"));
    }

    @Test
    @DisplayName("exige un jugador identificado")
    void exigeAnfitrion() {
        assertThrows(NullPointerException.class, () -> crearSala.ejecutar(validos(), null));
    }

    @Test
    @DisplayName("dos salas seguidas no comparten identificador")
    void identificadoresDistintos() {
        Sala primera = crearSala.ejecutar(validos(), ANFITRION);
        Sala segunda = crearSala.ejecutar(validos(), ANFITRION);

        assertAll(
                () -> assertTrue(!primera.id().equals(segunda.id())),
                () -> assertEquals(2, repositorio.cuantasHay()));
    }
}

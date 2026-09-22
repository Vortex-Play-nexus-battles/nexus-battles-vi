package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotivoDeCancelacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsElAnfitrion;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalidaNoPermitida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caso de uso de cancelacion de sala — operacion {@code cancelarSala}.
 *
 * <p>Lo que se prueba aqui es la coordinacion de los tres efectos: escribir el
 * estado, devolver los creditos (RF-JUE-014) y avisar a quienes estaban dentro.
 * Quien puede cancelar y cuando es cosa de {@code SalaTest}.
 */
@DisplayName("CancelarSala · caso de uso")
class CancelarSalaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private RepositorioDeSalasEnMemoria almacen;
    private CanalDeSalaEspia canal;
    private CreditosAnotados creditos;
    private CancelarSala cancelar;

    /** Doble del puerto de creditos que anota que se libero, y puede fallar a voluntad. */
    private static final class CreditosAnotados implements CreditosDelJugador {
        private final List<UUID> liberadas = new ArrayList<>();
        private boolean falla;

        @Override
        public ReservaDeCreditos reservar(UUID idJugador, int cantidad, UUID idSala, long ingreso) {
            return new ReservaDeCreditos(UUID.randomUUID(), cantidad);
        }

        @Override
        public void liberar(UUID idReserva) {
            if (falla) {
                throw new IllegalStateException("El modulo de creditos no responde.");
            }
            liberadas.add(idReserva);
        }

        @Override
        public void consumir(UUID idReserva, UUID idBeneficiario) {
            throw new UnsupportedOperationException("Cancelar una sala no cobra nada.");
        }
    }

    @BeforeEach
    void preparar() {
        almacen = new RepositorioDeSalasEnMemoria();
        canal = new CanalDeSalaEspia();
        creditos = new CreditosAnotados();
        cancelar = new CancelarSala(almacen, creditos, canal);
    }

    private Sala salaGratis() {
        return almacen.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION));
    }

    private Sala salaConApuesta(UUID reserva) {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 400, false, false, null), ANFITRION);
        return almacen.guardar(sala.conReserva(reserva));
    }

    @Test
    @DisplayName("la sala queda CANCELADA en el almacen")
    void marcaLaSala() {
        Sala sala = salaGratis();

        cancelar.ejecutar(sala.id(), ANFITRION);

        assertEquals(EstadoSala.CANCELADA,
                almacen.buscarPorId(sala.id()).orElseThrow().estado());
    }

    @Test
    @DisplayName("devuelve los creditos comprometidos y lo dice en el aviso")
    void devuelveLosCreditos() {
        UUID reserva = UUID.randomUUID();
        Sala sala = salaConApuesta(reserva);

        cancelar.ejecutar(sala.id(), ANFITRION);

        assertAll(
                () -> assertEquals(List.of(reserva), creditos.liberadas),
                () -> assertEquals(List.of(400), canal.creditosDevueltos()),
                () -> assertEquals(List.of(MotivoDeCancelacion.CANCELADA_POR_ANFITRION),
                        canal.motivos()));
    }

    @Test
    @DisplayName("una sala sin apuesta no molesta al modulo de creditos")
    void sinApuestaNoLibera() {
        Sala sala = salaGratis();

        cancelar.ejecutar(sala.id(), ANFITRION);

        assertAll(
                () -> assertTrue(creditos.liberadas.isEmpty()),
                () -> assertEquals(List.of(0), canal.creditosDevueltos()));
    }

    @Test
    @DisplayName("si los creditos no se pueden devolver, la cancelacion se sostiene igual")
    void elFalloAlLiberarNoTumbaLaCancelacion() {
        Sala sala = salaConApuesta(UUID.randomUUID());
        creditos.falla = true;

        cancelar.ejecutar(sala.id(), ANFITRION);

        assertAll(
                () -> assertEquals(EstadoSala.CANCELADA,
                        almacen.buscarPorId(sala.id()).orElseThrow().estado(),
                        "quien cancelo no puede acabar creyendo que su sala sigue abierta"),
                () -> assertEquals(List.of(0), canal.creditosDevueltos(),
                        "se avisa de 0, no se promete un numero que no se cumplio"));
    }

    @Test
    @DisplayName("anuncia la cancelacion a quienes estaban dentro")
    void anunciaLaCancelacion() {
        Sala sala = salaGratis();

        cancelar.ejecutar(sala.id(), ANFITRION);

        assertAll(
                () -> assertEquals(1, canal.anuncios().size()),
                () -> assertEquals(CanalDeSalaEspia.CANCELACION, canal.anuncios().get(0).tipo()),
                () -> assertEquals(sala.id(), canal.anuncios().get(0).idSala()));
    }

    @Test
    @DisplayName("quien no es el anfitrion recibe 403 y nada cambia")
    void otroNoCancela() {
        Sala sala = salaGratis();

        assertThrows(NoEsElAnfitrion.class, () -> cancelar.ejecutar(sala.id(), VISITANTE));

        assertAll(
                () -> assertEquals(EstadoSala.ABIERTA,
                        almacen.buscarPorId(sala.id()).orElseThrow().estado()),
                () -> assertTrue(canal.noAnuncioNada()),
                () -> assertTrue(creditos.liberadas.isEmpty(),
                        "un intento ajeno no puede devolverle los creditos a nadie"));
    }

    @Test
    @DisplayName("una sala que no existe responde 404")
    void salaInexistente() {
        assertThrows(SalaNoEncontrada.class,
                () -> cancelar.ejecutar(UUID.randomUUID(), ANFITRION));
    }

    @Test
    @DisplayName("HU-JUE-014 CA-03: al cancelar se devuelve la reserva de CADA participante, no solo la del anfitrion")
    void devuelveLasReservasDeTodos() {
        UUID delAnfitrion = UUID.randomUUID();
        UUID delVisitante = UUID.randomUUID();
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 400, false, false, null), ANFITRION);
        sala = sala.conReserva(delAnfitrion);
        sala.unirse(VISITANTE, new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante(
                "Visitante", InventarioEnMemoria.SOMBRA, delVisitante), null);
        almacen.guardar(sala);

        cancelar.ejecutar(sala.id(), ANFITRION);

        assertAll(
                () -> assertEquals(List.of(delAnfitrion, delVisitante), creditos.liberadas),
                () -> assertEquals(List.of(400), canal.creditosDevueltos()));
    }

    @Test
    @DisplayName("cancelar dos veces la misma sala no devuelve los creditos dos veces")
    void noDevuelveDosVeces() {
        UUID reserva = UUID.randomUUID();
        Sala sala = salaConApuesta(reserva);
        cancelar.ejecutar(sala.id(), ANFITRION);

        assertThrows(SalidaNoPermitida.class, () -> cancelar.ejecutar(sala.id(), ANFITRION));

        assertEquals(1, creditos.liberadas.size(),
                "el segundo intento no llega a los creditos: el dominio lo corta antes");
    }
}

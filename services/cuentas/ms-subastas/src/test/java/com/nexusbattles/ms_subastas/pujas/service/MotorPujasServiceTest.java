package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre las 5 reglas de HU-SUB-004: superar oferta + incremento minimo,
 * intervalo de 5 s, prohibicion de pujar en la propia subasta, y los 2
 * limites de participacion (subastas activas / pujas activas). Tambien
 * cubre la reserva/liberacion atomica de creditos y la compra inmediata.
 */
class MotorPujasServiceTest {
    /** Cada puja de prueba usa su propia clave, como la enviaria un cliente distinto. */
    private static String claveUnica() {
        return UUID.randomUUID().toString();
    }


    private static final UUID VENDEDOR = UUID.randomUUID();

    private MutableClock clock;
    private CreditoClientFake creditoClient;
    private ParametrosPuja parametros;
    private MotorPujasService motor;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-11T12:00:00Z"));
        creditoClient = new CreditoClientFake();
        parametros = new ParametrosPuja();
        motor = new MotorPujasService(creditoClient, clock, parametros);
    }

    private Subasta nuevaSubasta(BigDecimal ofertaVigente, BigDecimal incrementoMinimo) {
        return new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, ofertaVigente, incrementoMinimo,
                new BigDecimal("500"), null, EstadoSubasta.ACTIVA, clock.instant().plusSeconds(86400), 0L);
    }

    @Test
    void unaPujaQueSuperaLaOfertaYElIncrementoSeAcepta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        Puja puja = motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica());

        assertEquals(new BigDecimal("110"), subasta.getOfertaVigente());
        assertEquals(jugador, subasta.getMejorPostorId());
        assertEquals(EstadoPuja.ACTIVA, puja.getEstado());
    }

    @Test
    void unaPujaQueNoSuperaElIncrementoMinimoSeRechaza() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("105"), ContextoParticipacion.sinHistorial(), claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE, ex.getMotivo());
    }

    @Test
    void elVendedorNoPuedePujarEnSuPropiaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        creditoClient.acreditar(VENDEDOR, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, VENDEDOR, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.PUJA_PROPIA, ex.getMotivo());
    }

    @Test
    void noSePuedePujarEnUnaSubastaQueNoEstaActiva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setEstado(EstadoSubasta.ADJUDICADA);
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA, ex.getMotivo());
    }

    @Test
    void respetaElIntervaloMinimoDeCincoSegundosEntrePujasDelMismoJugador() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion hacePocoMenosDe5s = new ContextoParticipacion(clock.instant().minusSeconds(3), 0, 0);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), hacePocoMenosDe5s, claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.INTERVALO_MINIMO_NO_CUMPLIDO, ex.getMotivo());
    }

    @Test
    void pasadosLos5sElMismoJugadorPuedeVolverAPujar() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion hace5sExactos = new ContextoParticipacion(clock.instant().minus(Duration.ofSeconds(5)), 0, 0);

        Puja puja = motor.pujar(subasta, null, jugador, new BigDecimal("110"), hace5sExactos, claveUnica());

        assertEquals(EstadoPuja.ACTIVA, puja.getEstado());
    }

    @Test
    void rechazaAlAlcanzarElLimiteDeSubastasActivasSimultaneas() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion enElLimite = new ContextoParticipacion(null, 0, parametros.getMaxSubastasActivasPorJugador());

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), enElLimite, claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.LIMITE_SUBASTAS_ACTIVAS, ex.getMotivo());
    }

    @Test
    void rechazaAlAlcanzarElLimiteDePujasActivasSimultaneas() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion enElLimite = new ContextoParticipacion(null, parametros.getMaxPujasActivasPorJugador(), 0);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), enElLimite, claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.LIMITE_PUJAS_ACTIVAS, ex.getMotivo());
    }

    @Test
    void alSerSuperadoElPostorAnteriorLiberaSuReservaYQuedaSuperado() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID primerPostor = UUID.randomUUID();
        UUID segundoPostor = UUID.randomUUID();
        creditoClient.acreditar(primerPostor, new BigDecimal("1000"));
        creditoClient.acreditar(segundoPostor, new BigDecimal("1000"));

        Puja pujaInicial = motor.pujar(subasta, null, primerPostor, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica());
        BigDecimal disponibleMientrasGana = creditoClient.saldoDisponible(primerPostor);
        assertEquals(new BigDecimal("890"), disponibleMientrasGana);

        motor.pujar(subasta, pujaInicial, segundoPostor, new BigDecimal("125"), ContextoParticipacion.sinHistorial(), claveUnica());

        assertEquals(EstadoPuja.SUPERADA, pujaInicial.getEstado());
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(primerPostor));
        assertEquals(new BigDecimal("875"), creditoClient.saldoDisponible(segundoPostor));
    }

    @Test
    void comprarAhoraAdjudicaLaSubastaYConsumeLaReserva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        Puja puja = motor.comprarAhora(subasta, null, comprador);

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, puja.getEstado());
        assertEquals(new BigDecimal("500"), subasta.getOfertaVigente());
        assertEquals(new BigDecimal("500"), creditoClient.saldoDisponible(comprador));
    }

    @Test
    void comprarAhoraRestituyeLosCreditosDelPostorQueQuedaSuperado() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID postor = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        Puja pujaDelPostor = motor.pujar(subasta, null, postor, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica());
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(postor));

        motor.comprarAhora(subasta, pujaDelPostor, comprador);

        assertEquals(EstadoPuja.SUPERADA, pujaDelPostor.getEstado());
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(postor),
                "la compra inmediata lo superó, así que sus creditos reservados deben volver");
    }

    @Test
    void elVendedorNoPuedeComprarSuPropiaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        creditoClient.acreditar(VENDEDOR, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.comprarAhora(subasta, null, VENDEDOR));

        assertEquals(PujaRechazadaException.Motivo.PUJA_PROPIA, ex.getMotivo());
    }

    @Test
    void noSePuedeComprarUnaSubastaSinPrecioDeCompraInmediata() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setPrecioCompraInmediata(null);
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        assertThrows(IllegalStateException.class, () -> motor.comprarAhora(subasta, null, comprador));
    }

    @Test
    void reintentarLaMismaPujaConLaMismaClaveNoReservaDosVecesLosCreditos() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        String mismaClave = "reintento-del-cliente";

        motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), mismaClave);
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(jugador));

        // El cliente no recibio la respuesta y reintenta con la misma clave.
        subasta.setOfertaVigente(new BigDecimal("100"));
        motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), mismaClave);

        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(jugador),
                "el reintento debe reutilizar la reserva, no crear una segunda");
    }

    // --- cierre por vencimiento (criterio 3) ---

    @Test
    void alVencerSinPujasLaSubastaCierraSinAdjudicacion() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));

        motor.cerrarPorVencimiento(subasta, null);

        assertEquals(EstadoSubasta.SIN_ADJUDICACION, subasta.getEstado());
    }

    @Test
    void alVencerConPujaVigenteEsaPujaGanaYSeCobraLaReserva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID postor = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        Puja puja = motor.pujar(subasta, null, postor, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica());

        motor.cerrarPorVencimiento(subasta, puja);

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, puja.getEstado());
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(postor),
                "la reserva pasa a debito real: los 110 se cobran de verdad");
    }

    @Test
    void noSePuedeCerrarDosVecesLaMismaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        motor.cerrarPorVencimiento(subasta, null);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.cerrarPorVencimiento(subasta, null));

        assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA, ex.getMotivo());
    }
}

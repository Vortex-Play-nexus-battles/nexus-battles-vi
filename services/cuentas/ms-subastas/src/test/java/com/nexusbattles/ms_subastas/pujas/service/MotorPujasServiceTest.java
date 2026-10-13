package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.creditos.ReservaCredito;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    private InventarioClient inventarioClient;
    private MotorPujasService motorConInventario;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-11T12:00:00Z"));
        creditoClient = new CreditoClientFake();
        parametros = new ParametrosPuja();
        motor = new MotorPujasService(creditoClient, clock, parametros);
        inventarioClient = mock(InventarioClient.class);
        motorConInventario = new MotorPujasService(creditoClient, inventarioClient, clock, parametros);
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

        Puja puja = motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        assertEquals(new BigDecimal("110"), subasta.getOfertaVigente());
        assertEquals(jugador, subasta.getMejorPostorId());
        assertEquals(EstadoPuja.ACTIVA, puja.getEstado());
    }

    @Test
    void unaPujaQueNoSuperaElIncrementoMinimoSeRechaza() {
        // Ya hay una puja de 100: la siguiente tiene que llegar a 100 + 10.
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setMejorPostorId(UUID.randomUUID());
        subasta.setCantidadPujas(1);
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("105"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE, ex.getMotivo());
    }

    /**
     * B8 (7.7.2, 7.7.6): la primera puja llega al precio minimo que fijo el
     * vendedor; el incremento es ENTRE pujas. Hasta B8 la primera exigia
     * precio minimo mas incremento.
     */
    @Test
    void laPrimeraPujaPuedeSerExactamenteElPrecioMinimo() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        Puja puja = motor.pujar(subasta, null, jugador, new BigDecimal("100"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        assertEquals(EstadoPuja.ACTIVA, puja.getEstado());
        assertEquals(0, new BigDecimal("100").compareTo(subasta.getOfertaVigente()));
        assertEquals(0, new BigDecimal("110").compareTo(subasta.pujaMinimaSiguiente()),
                "desde la segunda, oferta vigente mas incremento");
    }

    @Test
    void laPrimeraPujaPorDebajoDelPrecioMinimoSeRechaza() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("99.99"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE, ex.getMotivo());
        assertTrue(ex.getMessage().contains("precio minimo"));
    }

    @Test
    void elVendedorNoPuedePujarEnSuPropiaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        creditoClient.acreditar(VENDEDOR, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, VENDEDOR, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.PUJA_PROPIA, ex.getMotivo());
    }

    @Test
    void noSePuedePujarEnUnaSubastaQueNoEstaActiva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setEstado(EstadoSubasta.ADJUDICADA);
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA, ex.getMotivo());
    }

    @Test
    void respetaElIntervaloMinimoDeCincoSegundosEntrePujasDelMismoJugador() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion hacePocoMenosDe5s = new ContextoParticipacion(clock.instant().minusSeconds(3), 0);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), hacePocoMenosDe5s, claveUnica(), TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.INTERVALO_MINIMO_NO_CUMPLIDO, ex.getMotivo());
    }

    @Test
    void pasadosLos5sElMismoJugadorPuedeVolverAPujar() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion hace5sExactos = new ContextoParticipacion(clock.instant().minus(Duration.ofSeconds(5)), 0);

        Puja puja = motor.pujar(subasta, null, jugador, new BigDecimal("110"), hace5sExactos, claveUnica(), TipoPuja.MANUAL);

        assertEquals(EstadoPuja.ACTIVA, puja.getEstado());
    }

    @Test
    void rechazaAlAlcanzarElLimiteDePujasActivasSimultaneas() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        ContextoParticipacion enElLimite = new ContextoParticipacion(null, parametros.getMaxPujasActivasPorJugador());

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, null, jugador, new BigDecimal("110"), enElLimite, claveUnica(), TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.LIMITE_PUJAS_ACTIVAS, ex.getMotivo());
    }

    /**
     * G5 (7.7.10): con exactamente el tope de pujas activas, subir la propia
     * puja vigente la REEMPLAZA (pasa a SUPERADA): no suma una mas. Antes se
     * contaba y no se podia mejorar ni la subasta que ya se iba ganando.
     */
    @Test
    void enElLimiteSePuedeSubirLaPropiaPujaVigentePorqueLaReemplaza() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        Puja suya = motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(),
                claveUnica(), TipoPuja.MANUAL);
        clock.avanzar(Duration.ofSeconds(10));
        ContextoParticipacion enElLimite = new ContextoParticipacion(null, parametros.getMaxPujasActivasPorJugador());

        Puja mejor = motor.pujar(subasta, suya, jugador, new BigDecimal("130"), enElLimite, claveUnica(),
                TipoPuja.MANUAL);

        assertEquals(EstadoPuja.ACTIVA, mejor.getEstado());
        assertEquals(EstadoPuja.SUPERADA, suya.getEstado());
        assertEquals(0, new BigDecimal("130").compareTo(subasta.getOfertaVigente()));
    }

    @Test
    void enElLimiteNoSePuedeSuperarLaPujaDeOtro() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID otro = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(otro, new BigDecimal("1000"));
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        Puja deOtro = motor.pujar(subasta, null, otro, new BigDecimal("110"), ContextoParticipacion.sinHistorial(),
                claveUnica(), TipoPuja.MANUAL);
        ContextoParticipacion enElLimite = new ContextoParticipacion(null, parametros.getMaxPujasActivasPorJugador());

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, deOtro, jugador, new BigDecimal("130"), enElLimite, claveUnica(),
                        TipoPuja.MANUAL));

        assertEquals(PujaRechazadaException.Motivo.LIMITE_PUJAS_ACTIVAS, ex.getMotivo());
    }

    @Test
    void alSerSuperadoElPostorAnteriorLiberaSuReservaYQuedaSuperado() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID primerPostor = UUID.randomUUID();
        UUID segundoPostor = UUID.randomUUID();
        creditoClient.acreditar(primerPostor, new BigDecimal("1000"));
        creditoClient.acreditar(segundoPostor, new BigDecimal("1000"));

        Puja pujaInicial = motor.pujar(subasta, null, primerPostor, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);
        BigDecimal disponibleMientrasGana = creditoClient.saldoDisponible(primerPostor);
        assertEquals(new BigDecimal("890"), disponibleMientrasGana);

        motor.pujar(subasta, pujaInicial, segundoPostor, new BigDecimal("125"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        assertEquals(EstadoPuja.SUPERADA, pujaInicial.getEstado());
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(primerPostor));
        assertEquals(new BigDecimal("875"), creditoClient.saldoDisponible(segundoPostor));
    }

    @Test
    void comprarAhoraAdjudicaLaSubastaYConsumeLaReserva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        Puja puja = motor.comprarAhora(subasta, null, comprador, claveUnica());

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

        Puja pujaDelPostor = motor.pujar(subasta, null, postor, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(postor));

        motor.comprarAhora(subasta, pujaDelPostor, comprador, claveUnica());

        assertEquals(EstadoPuja.SUPERADA, pujaDelPostor.getEstado());
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(postor),
                "la compra inmediata lo superó, así que sus creditos reservados deben volver");
    }

    @Test
    void elVendedorNoPuedeComprarSuPropiaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        creditoClient.acreditar(VENDEDOR, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.comprarAhora(subasta, null, VENDEDOR, claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.PUJA_PROPIA, ex.getMotivo());
    }

    @Test
    void noSePuedeComprarUnaSubastaSinPrecioDeCompraInmediata() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setPrecioCompraInmediata(null);
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.comprarAhora(subasta, null, comprador, claveUnica()));

        // Antes era IllegalStateException, que por HTTP habria salido como un
        // 500: la interfaz ofrecio comprar una subasta que no admite compra
        // inmediata, y eso es un rechazo de negocio, no un fallo del servidor.
        assertEquals(PujaRechazadaException.Motivo.SIN_COMPRA_INMEDIATA, ex.getMotivo());
    }

    /**
     * B8: comprar por debajo de lo que otro jugador ya ofrecio le quitaria el
     * producto al mejor postor. Cuando una puja alcanza el precio de compra
     * inmediata, deja de estar disponible (409, es una carrera).
     */
    @Test
    void laCompraInmediataSeBloqueaCuandoUnaPujaAlcanzaSuPrecio() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID postor = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        creditoClient.acreditar(comprador, new BigDecimal("1000"));
        Puja alPrecio = motor.pujar(subasta, null, postor, new BigDecimal("500"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.comprarAhora(subasta, alPrecio, comprador, claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.COMPRA_INMEDIATA_SUPERADA, ex.getMotivo());
        assertEquals(EstadoSubasta.ACTIVA, subasta.getEstado());
        assertEquals(EstadoPuja.ACTIVA, alPrecio.getEstado(), "el mejor postor sigue siendolo");
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(comprador), "no se reservo nada");
    }

    @Test
    void laCompraInmediataSeBloqueaCuandoUnaPujaSuperaSuPrecio() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID postor = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        Puja porEncima = motor.pujar(subasta, null, postor, new BigDecimal("620"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        assertFalse(subasta.compraInmediataDisponible());
        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.comprarAhora(subasta, porEncima, UUID.randomUUID(), claveUnica()));
        assertEquals(PujaRechazadaException.Motivo.COMPRA_INMEDIATA_SUPERADA, ex.getMotivo());
    }

    @Test
    void laCompraInmediataSigueDisponibleMientrasNingunaPujaLaAlcance() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID postor = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        creditoClient.acreditar(comprador, new BigDecimal("1000"));
        Puja debajo = motor.pujar(subasta, null, postor, new BigDecimal("499.99"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        assertTrue(subasta.compraInmediataDisponible());
        Puja compra = motor.comprarAhora(subasta, debajo, comprador, claveUnica());

        assertEquals(EstadoPuja.GANADORA, compra.getEstado());
        assertEquals(clock.instant(), subasta.getCerradaEn(), "el cierre queda fechado");
    }

    @Test
    void reintentarLaMismaPujaConLaMismaClaveNoReservaDosVecesLosCreditos() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        String mismaClave = "reintento-del-cliente";

        motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), mismaClave, TipoPuja.MANUAL);
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(jugador));

        // El cliente no recibio la respuesta y reintenta con la misma clave.
        subasta.setOfertaVigente(new BigDecimal("100"));
        motor.pujar(subasta, null, jugador, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), mismaClave, TipoPuja.MANUAL);

        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(jugador),
                "el reintento debe reutilizar la reserva, no crear una segunda");
    }

    // --- cierre por vencimiento (criterio 3) ---

    @Test
    void alVencerSinPujasLaSubastaCierraSinAdjudicacion() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));

        motor.cerrarPorVencimiento(subasta, null);

        assertEquals(EstadoSubasta.SIN_ADJUDICACION, subasta.getEstado());
        assertEquals(clock.instant(), subasta.getCerradaEn());
    }

    @Test
    void alVencerConPujaVigenteEsaPujaGanaYSeCobraLaReserva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID postor = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        Puja puja = motor.pujar(subasta, null, postor, new BigDecimal("110"), ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        motor.cerrarPorVencimiento(subasta, puja);

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, puja.getEstado());
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(postor),
                "la reserva pasa a debito real: los 110 se cobran de verdad");
    }

    /**
     * cantidadPujas alimenta el listado de HU-SUB-011: se muestra en la tarjeta
     * y es una de las opciones de "ordenar por". Este motor es el unico que
     * crea pujas en el servicio, asi que si no lo incrementa aqui, el contador
     * se queda en 0 para siempre y ese orden no ordena nada.
     */
    @Test
    void cadaPujaIncrementaElContadorQueMuestraElListado() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        creditoClient.acreditar(primero, new BigDecimal("1000"));
        creditoClient.acreditar(segundo, new BigDecimal("1000"));
        assertEquals(0, subasta.getCantidadPujas());

        Puja inicial = motor.pujar(subasta, null, primero, new BigDecimal("110"),
                ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);
        assertEquals(1, subasta.getCantidadPujas());

        motor.pujar(subasta, inicial, segundo, new BigDecimal("125"),
                ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        assertEquals(2, subasta.getCantidadPujas());
    }

    @Test
    void laCompraInmediataTambienCuentaComoPuja() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        motor.comprarAhora(subasta, null, comprador, claveUnica());

        assertEquals(1, subasta.getCantidadPujas(),
                "la compra inmediata deja una fila en pujas, asi que el contador la refleja");
    }

    @Test
    void noSePuedeCerrarDosVecesLaMismaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        motor.cerrarPorVencimiento(subasta, null);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.cerrarPorVencimiento(subasta, null));

        assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA, ex.getMotivo());
    }

    @Test
    void comprarAhoraTransfiereElItemAlCompradorYConsumeCreditos() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-compra-1");
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));
        String clave = claveUnica();

        Puja puja = motorConInventario.comprarAhora(subasta, null, comprador, clave);

        assertEquals(EstadoPuja.GANADORA, puja.getEstado());
        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(comprador, subasta.getMejorPostorId());

        verify(inventarioClient).transferirProducto("elem-compra-1", comprador, subasta.getId(), clave);
        assertEquals(new BigDecimal("500"), creditoClient.saldoDisponible(comprador));
    }

    @Test
    void comprarAhoraLiberaReservaDeCreditoSiFallaTransferenciaDeItem() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-compra-fail");
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));
        String clave = claveUnica();

        doThrow(new InventarioClientException("Inventario fuera de linea"))
                .when(inventarioClient).transferirProducto(eq("elem-compra-fail"), eq(comprador), eq(subasta.getId()), eq(clave));

        assertThrows(InventarioClientException.class, () ->
                motorConInventario.comprarAhora(subasta, null, comprador, clave));

        assertEquals(EstadoSubasta.ACTIVA, subasta.getEstado());
        // Se le restituyeron los creditos: el saldo disponible sigue siendo 1000
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(comprador));
    }

    @Test
    void cerrarPorVencimientoConGanadorTransfiereElItemAlGanador() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-vencida-1");
        UUID ganador = UUID.randomUUID();
        creditoClient.acreditar(ganador, new BigDecimal("1000"));
        Puja pujaVigente = motorConInventario.pujar(subasta, null, ganador, new BigDecimal("150"),
                ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        motorConInventario.cerrarPorVencimiento(subasta, pujaVigente);

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, pujaVigente.getEstado());
        verify(inventarioClient).transferirProducto("elem-vencida-1", ganador, subasta.getId(), "cierre-" + subasta.getId());
        assertEquals(new BigDecimal("850"), creditoClient.saldoDisponible(ganador));
    }

    @Test
    void cerrarPorVencimientoConGanadorNoConsumeCreditosSiFallaTransferencia() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-vencida-fail");
        UUID ganador = UUID.randomUUID();
        creditoClient.acreditar(ganador, new BigDecimal("1000"));
        Puja pujaVigente = motorConInventario.pujar(subasta, null, ganador, new BigDecimal("150"),
                ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);

        doThrow(new InventarioClientException("Fallo transferencia"))
                .when(inventarioClient).transferirProducto(eq("elem-vencida-fail"), eq(ganador), eq(subasta.getId()), any());

        assertThrows(InventarioClientException.class, () ->
                motorConInventario.cerrarPorVencimiento(subasta, pujaVigente));

        assertEquals(EstadoSubasta.ACTIVA, subasta.getEstado());
        assertEquals(EstadoPuja.ACTIVA, pujaVigente.getEstado());
    }

    @Test
    void comprarAhoraRevierteTransferenciaDeItemSiFallaConsumoDeCreditos() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-compra-fail-debito");
        UUID comprador = UUID.randomUUID();
        String clave = claveUnica();

        CreditoClient creditoMock = mock(CreditoClient.class);
        ReservaCredito reserva = new ReservaCredito(UUID.randomUUID(), comprador, new BigDecimal("500"), ReservaCredito.EstadoReserva.RESERVADA);
        when(creditoMock.reservar(eq(comprador), any(BigDecimal.class), eq(subasta.getId()), eq(clave))).thenReturn(reserva);
        doThrow(new RuntimeException("Fallo al consumir creditos en ms-finanzas"))
                .when(creditoMock).consumir(eq(reserva.id()), any());

        MotorPujasService motorTest = new MotorPujasService(creditoMock, inventarioClient, clock, parametros);

        assertThrows(RuntimeException.class, () ->
                motorTest.comprarAhora(subasta, null, comprador, clave));

        // Se transfirio primero al comprador.
        verify(inventarioClient).transferirProducto("elem-compra-fail-debito", comprador, subasta.getId(), clave);
        // Y ante el fallo del cobro se devolvio al vendedor.
        verify(inventarioClient).transferirProducto("elem-compra-fail-debito", VENDEDOR, subasta.getId(), "compensar-" + clave);
        // Ya NO se vuelve a reservar. Esa segunda llamada estaba aqui y en el
        // codigo, y contra el inventario real no podia funcionar nunca:
        // reservar() acaba en bloquear(), que exige que el propietarioUid
        // declarado sea el dueno actual del elemento, y en ese momento el dueno
        // es el comprador, no el vendedor. Devolvia InventarioAjenoException.
        // Pasaba solo porque aqui inventarioClient es un mock. FI-TRANSFER-1
        // conserva el bloqueo a traves de la transferencia, asi que la vuelta
        // ya no necesita re-bloquear nada.
        verify(inventarioClient, never()).reservar(eq("elem-compra-fail-debito"), any(), any(), any());
        // Verifica que se liberó la reserva de crédito
        verify(creditoMock).liberar(reserva.id());
    }

    /**
     * El cierre por vencimiento transfiere el producto ANTES de cobrar, asi que
     * si el cobro falla el ganador se queda con el objeto sin haberlo pagado.
     * La transferencia sale por HTTP y no la revierte el rollback de la
     * transaccion, que es lo que si deshace el estado en la base de datos.
     *
     * <p>No es un caso rebuscado: basta reiniciar el servicio. Las pujas viven
     * en PostgreSQL y las reservas del doble de creditos solo en memoria, asi
     * que al arrancar hay pujas apuntando a reservas que ya no existen y
     * consumir() las rechaza.
     *
     * <p>comprarAhora ya compensaba este mismo caso; el cierre no.
     */
    @Test
    void siElCobroFallaAlCerrarSeDevuelveElProductoAlVendedor() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-cierre-fail-debito");
        UUID ganador = UUID.randomUUID();
        String reservaId = UUID.randomUUID().toString();

        CreditoClient creditoMock = mock(CreditoClient.class);
        doThrow(new RuntimeException("Fallo al consumir creditos en ms-finanzas"))
                .when(creditoMock).consumir(eq(UUID.fromString(reservaId)), any());

        MotorPujasService motorTest = new MotorPujasService(creditoMock, inventarioClient, clock, parametros);
        Puja pujaVigente = new Puja(UUID.randomUUID(), subasta.getId(), ganador, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, clock.instant().minusSeconds(30), reservaId);

        assertThrows(RuntimeException.class, () -> motorTest.cerrarPorVencimiento(subasta, pujaVigente));

        String claveCierre = "cierre-" + subasta.getId();
        verify(inventarioClient).transferirProducto("elem-cierre-fail-debito", ganador, subasta.getId(), claveCierre);
        verify(inventarioClient).transferirProducto("elem-cierre-fail-debito", VENDEDOR, subasta.getId(),
                "compensar-" + claveCierre);
        // Sin re-reserva, por la misma razon que en comprarAhora: el bloqueo ya
        // viaja con el elemento, y re-bloquear a nombre del vendedor cuando el
        // dueno es el ganador siempre daba InventarioAjenoException.
        verify(inventarioClient, never()).reservar(eq("elem-cierre-fail-debito"), any(), any(), any());
        // Y el bloqueo NO se suelta: la venta no llego a ser definitiva.
        verify(inventarioClient, never()).liberarReserva(eq("elem-cierre-fail-debito"), any(), any());
    }

    /**
     * Y la otra mitad: compensar no puede convertirse en devolver el producto
     * siempre. Si el cobro prospera, el ganador se lo queda.
     */
    @Test
    void siElCobroProsperaAlCerrarElProductoSeQuedaConElGanador() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-cierre-ok");
        UUID ganador = UUID.randomUUID();
        creditoClient.acreditar(ganador, new BigDecimal("1000"));
        ReservaCredito reserva = creditoClient.reservar(ganador, new BigDecimal("110"), subasta.getId(), claveUnica());
        Puja pujaVigente = new Puja(UUID.randomUUID(), subasta.getId(), ganador, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, clock.instant().minusSeconds(30), reserva.id().toString());

        motorConInventario.cerrarPorVencimiento(subasta, pujaVigente);

        verify(inventarioClient).transferirProducto("elem-cierre-ok", ganador, subasta.getId(),
                "cierre-" + subasta.getId());
        verify(inventarioClient, never()).transferirProducto(eq("elem-cierre-ok"), eq(VENDEDOR), any(), any());
        // B8 (7.7.9): la venta es definitiva pero el producto queda PENDIENTE
        // DE RECOGER, en custodia de la subasta: el bloqueo NO se suelta aqui.
        // Lo suelta PendientesService cuando el ganador lo recoge, o al vencer
        // los 7 dias.
        verify(inventarioClient, never()).liberarReserva(eq("elem-cierre-ok"), any(), any());
        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, pujaVigente.getEstado());
        assertEquals(clock.instant(), subasta.getCerradaEn());
    }

    /**
     * El bloqueo se suelta DESPUES de dar la subasta por adjudicada, y su fallo
     * no puede tumbar el cierre — FI-TRANSFER-1.
     *
     * <p>En ese punto el cobro ya entro. Entre «objeto bloqueado de mas» y
     * «objeto pagado y devuelto al vendedor», lo primero es un defecto
     * reparable con una llamada y lo segundo es un robo. Asi que si inventario
     * no responde, se registra y se sigue.
     */
    @Test
    void siFallaSoltarElBloqueoTrasLaCompraInmediataLaSubastaSigueAdjudicada() {
        // La compra inmediata SI suelta el bloqueo en el acto (7.7.6,
        // «transferencia automatica»); si inventario no responde en ese ultimo
        // paso, el cobro ya entro y no se deshace nada.
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-compra-bloqueo-fail");
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(comprador, new BigDecimal("1000"));
        doThrow(new InventarioClientException("inventario no responde"))
                .when(inventarioClient).liberarReserva(eq("elem-compra-bloqueo-fail"), any(), any());

        Puja ganadora = motorConInventario.comprarAhora(subasta, null, comprador, claveUnica());

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, ganadora.getEstado());
        // Y sobre todo: no se devolvio el objeto a un vendedor que ya cobro.
        verify(inventarioClient, never())
                .transferirProducto(eq("elem-compra-bloqueo-fail"), eq(VENDEDOR), any(), any());
    }

    @Test
    void comprarAhoraConPujaVigenteLiberaCreditosDePostorAnteriorYTransfiereItemAlComprador() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-compra-con-postor");
        UUID postorAnterior = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(postorAnterior, new BigDecimal("1000"));
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        Puja pujaVigente = motorConInventario.pujar(subasta, null, postorAnterior, new BigDecimal("150"),
                ContextoParticipacion.sinHistorial(), claveUnica(), TipoPuja.MANUAL);
        assertEquals(new BigDecimal("850"), creditoClient.saldoDisponible(postorAnterior));

        String claveCompra = claveUnica();
        Puja ganadora = motorConInventario.comprarAhora(subasta, pujaVigente, comprador, claveCompra);

        assertEquals(EstadoPuja.GANADORA, ganadora.getEstado());
        assertEquals(EstadoPuja.SUPERADA, pujaVigente.getEstado());
        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        // El postor anterior recupera sus 1000 créditos
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(postorAnterior));
        // El comprador pagó 500
        assertEquals(new BigDecimal("500"), creditoClient.saldoDisponible(comprador));
        // El ítem se transfirió al comprador
        verify(inventarioClient).transferirProducto("elem-compra-con-postor", comprador, subasta.getId(), claveCompra);
    }

    @Test
    void cerrarPorVencimientoSinOfertasLiberaReservaDeInventario() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("elem-sin-ofertas");

        motorConInventario.cerrarPorVencimiento(subasta, null);

        assertEquals(EstadoSubasta.SIN_ADJUDICACION, subasta.getEstado());
        verify(inventarioClient).liberarReserva("elem-sin-ofertas", subasta.getId(), "cierre-" + subasta.getId());
    }

    @Test
    void cerrarPorVencimientoIgnoraInventarioSiElementoEsBlanco() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), new BigDecimal("10"));
        subasta.setElementoInventarioId("   ");

        motorConInventario.cerrarPorVencimiento(subasta, null);

        assertEquals(EstadoSubasta.SIN_ADJUDICACION, subasta.getEstado());
        verifyNoInteractions(inventarioClient);
    }
}

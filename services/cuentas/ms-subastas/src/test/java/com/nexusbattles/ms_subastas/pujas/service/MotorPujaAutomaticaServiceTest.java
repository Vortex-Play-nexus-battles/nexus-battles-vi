package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MotorPujaAutomaticaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    private ParametrosPuja parametros;
    private MotorPujaAutomaticaService motor;

    @BeforeEach
    void setUp() {
        parametros = new ParametrosPuja();
        motor = new MotorPujaAutomaticaService(Clock.fixed(AHORA, ZoneOffset.UTC), parametros);
    }

    private Subasta nuevaSubasta(BigDecimal ofertaVigente, UUID mejorPostorId) {
        return new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, ofertaVigente,
                new BigDecimal("10"), null, mejorPostorId, EstadoSubasta.ACTIVA, AHORA.plusSeconds(3600), 0L);
    }

    private PujaAutomatica automatico(UUID subastaId, UUID jugadorId, String limite) {
        return new PujaAutomatica(UUID.randomUUID(), subastaId, jugadorId, new BigDecimal(limite), true);
    }

    // --- calcularRespuesta ---

    @Test
    void ofertaElIncrementoMinimoCuandoAunNoAlcanzaSuLimite() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), UUID.randomUUID());
        PujaAutomatica auto = automatico(subasta.getId(), UUID.randomUUID(), "200");

        assertEquals(Optional.of(new BigDecimal("110")), motor.calcularRespuesta(subasta, auto));
        assertTrue(auto.isActiva());
    }

    @Test
    void noRespondeASiMismoCuandoYaEsElMejorPostor() {
        UUID jugador = UUID.randomUUID();
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), jugador);
        PujaAutomatica auto = automatico(subasta.getId(), jugador, "200");

        assertTrue(motor.calcularRespuesta(subasta, auto).isEmpty());
        assertTrue(auto.isActiva(), "seguir siendo el mejor postor no agota su limite");
    }

    @Test
    void seDesactivaAlAlcanzarElLimiteConfigurado() {
        Subasta subasta = nuevaSubasta(new BigDecimal("195"), UUID.randomUUID());
        PujaAutomatica auto = automatico(subasta.getId(), UUID.randomUUID(), "200");

        assertTrue(motor.calcularRespuesta(subasta, auto).isEmpty(), "195 + 10 = 205 excede el limite de 200");
        assertFalse(auto.isActiva(), "debe desactivarse para que el caller notifique el limite alcanzado");
    }

    @Test
    void pujaExactamenteHastaSuLimiteCuandoCalzaJusto() {
        Subasta subasta = nuevaSubasta(new BigDecimal("190"), UUID.randomUUID());
        PujaAutomatica auto = automatico(subasta.getId(), UUID.randomUUID(), "200");

        assertEquals(Optional.of(new BigDecimal("200")), motor.calcularRespuesta(subasta, auto));
        assertTrue(auto.isActiva());
    }

    @Test
    void unaPujaAutomaticaYaInactivaNuncaResponde() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), UUID.randomUUID());
        PujaAutomatica auto = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), UUID.randomUUID(), new BigDecimal("200"), false);

        assertTrue(motor.calcularRespuesta(subasta, auto).isEmpty());
    }

    // --- elegirSiguiente: guerra entre varias pujas automaticas ---

    @Test
    void entreVariasAutomaticasRespondeLaDeMayorLimite() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), UUID.randomUUID());
        PujaAutomatica modesta = automatico(subasta.getId(), UUID.randomUUID(), "150");
        PujaAutomatica agresiva = automatico(subasta.getId(), UUID.randomUUID(), "900");

        Optional<PujaAutomatica> elegida = motor.elegirSiguiente(subasta, List.of(modesta, agresiva));

        assertEquals(Optional.of(agresiva), elegida);
    }

    @Test
    void lasAutomaticasQueYaNoAlcanzanQuedanDesactivadasParaNotificarlas() {
        Subasta subasta = nuevaSubasta(new BigDecimal("145"), UUID.randomUUID());
        PujaAutomatica agotada = automatico(subasta.getId(), UUID.randomUUID(), "150");
        PujaAutomatica conMargen = automatico(subasta.getId(), UUID.randomUUID(), "900");

        Optional<PujaAutomatica> elegida = motor.elegirSiguiente(subasta, List.of(agotada, conMargen));

        assertEquals(Optional.of(conMargen), elegida);
        assertFalse(agotada.isActiva(), "145 + 10 = 155 excede su limite de 150");
        assertTrue(conMargen.isActiva());
    }

    @Test
    void noEligeAlJugadorQueYaEsElMejorPostor() {
        UUID lider = UUID.randomUUID();
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), lider);
        PujaAutomatica delLider = automatico(subasta.getId(), lider, "900");
        PujaAutomatica delRival = automatico(subasta.getId(), UUID.randomUUID(), "300");

        Optional<PujaAutomatica> elegida = motor.elegirSiguiente(subasta, List.of(delLider, delRival));

        assertEquals(Optional.of(delRival), elegida, "no tiene sentido que el lider se supere a si mismo");
    }

    @Test
    void siNingunaAlcanzaNoEligeANadieYTodasQuedanDesactivadas() {
        Subasta subasta = nuevaSubasta(new BigDecimal("500"), UUID.randomUUID());
        PujaAutomatica una = automatico(subasta.getId(), UUID.randomUUID(), "150");
        PujaAutomatica otra = automatico(subasta.getId(), UUID.randomUUID(), "200");

        assertTrue(motor.elegirSiguiente(subasta, List.of(una, otra)).isEmpty());
        assertFalse(una.isActiva());
        assertFalse(otra.isActiva());
    }

    // --- configurar ---

    @Test
    void configurarUnaPujaAutomaticaValidaLaDejaActiva() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), null);
        UUID jugador = UUID.randomUUID();

        PujaAutomatica auto = motor.configurar(subasta, jugador, new BigDecimal("500"), new BigDecimal("1000"));

        assertTrue(auto.isActiva());
        assertEquals(new BigDecimal("500"), auto.getLimite());
        assertEquals(jugador, auto.getJugadorId());
    }

    @Test
    void rechazaUnLimiteQueNuncaPodriaPujar() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), null);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.configurar(subasta, UUID.randomUUID(), new BigDecimal("105"), new BigDecimal("1000")));

        assertEquals(PujaRechazadaException.Motivo.LIMITE_AUTOMATICO_INALCANZABLE, ex.getMotivo());
    }

    @Test
    void rechazaUnLimiteQueElSaldoNoCubre() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), null);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.configurar(subasta, UUID.randomUUID(), new BigDecimal("500"), new BigDecimal("300")));

        assertEquals(PujaRechazadaException.Motivo.SALDO_INSUFICIENTE_PARA_LIMITE, ex.getMotivo());
    }

    @Test
    void elVendedorNoPuedeConfigurarAutomaticaEnSuPropiaSubasta() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), null);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.configurar(subasta, VENDEDOR, new BigDecimal("500"), new BigDecimal("1000")));

        assertEquals(PujaRechazadaException.Motivo.PUJA_PROPIA, ex.getMotivo());
    }

    @Test
    void noSePuedeConfigurarAutomaticaEnUnaSubastaCerrada() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), null);
        subasta.setEstado(EstadoSubasta.ADJUDICADA);

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> motor.configurar(subasta, UUID.randomUUID(), new BigDecimal("500"), new BigDecimal("1000")));

        assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA, ex.getMotivo());
    }

    // --- intervalo minimo ---

    @Test
    void unJugadorQueNuncaPujoPuedeEmitirDeInmediato() {
        assertEquals(AHORA, motor.disponibleDesde(null));
    }

    @Test
    void trasPujarDebeEsperarElIntervaloMinimo() {
        Instant ultimaPuja = AHORA.minusSeconds(2);

        Instant disponible = motor.disponibleDesde(ultimaPuja);

        assertEquals(ultimaPuja.plusSeconds(parametros.getIntervaloMinimoSegundos()), disponible);
        assertTrue(disponible.isAfter(AHORA), "todavia no puede emitir");
    }
}

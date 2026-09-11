package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MotorPujaAutomaticaServiceTest {

    private final MotorPujaAutomaticaService motor = new MotorPujaAutomaticaService();

    private Subasta nuevaSubasta(BigDecimal ofertaVigente, UUID mejorPostorId) {
        return new Subasta(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ofertaVigente,
                new BigDecimal("10"), null, mejorPostorId, EstadoSubasta.ACTIVA, Instant.now().plusSeconds(3600), 0L);
    }

    @Test
    void ofertaElIncrementoMinimoCuandoAunNoAlcanzaSuLimite() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), UUID.randomUUID());
        PujaAutomatica automatico = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), UUID.randomUUID(), new BigDecimal("200"), true);

        Optional<BigDecimal> respuesta = motor.calcularRespuesta(subasta, automatico);

        assertEquals(Optional.of(new BigDecimal("110")), respuesta);
        assertTrue(automatico.isActiva());
    }

    @Test
    void noRespondeASiMismoCuandoYaEsElMejorPostor() {
        UUID jugador = UUID.randomUUID();
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), jugador);
        PujaAutomatica automatico = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), jugador, new BigDecimal("200"), true);

        Optional<BigDecimal> respuesta = motor.calcularRespuesta(subasta, automatico);

        assertTrue(respuesta.isEmpty());
    }

    @Test
    void seDesactivaYNotificaAlAlcanzarElLimiteConfigurado() {
        Subasta subasta = nuevaSubasta(new BigDecimal("195"), UUID.randomUUID());
        PujaAutomatica automatico = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), UUID.randomUUID(), new BigDecimal("200"), true);

        Optional<BigDecimal> respuesta = motor.calcularRespuesta(subasta, automatico);

        assertTrue(respuesta.isEmpty(), "110 > 200 no aplica aqui: 195 + 10 = 205 > limite 200");
        assertFalse(automatico.isActiva(), "debe desactivarse para que el caller dispare la notificacion de limite alcanzado");
    }

    @Test
    void unaPujaAutomaticaYaInactivaNuncaResponde() {
        Subasta subasta = nuevaSubasta(new BigDecimal("100"), UUID.randomUUID());
        PujaAutomatica automatico = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), UUID.randomUUID(), new BigDecimal("200"), false);

        Optional<BigDecimal> respuesta = motor.calcularRespuesta(subasta, automatico);

        assertTrue(respuesta.isEmpty());
    }
}

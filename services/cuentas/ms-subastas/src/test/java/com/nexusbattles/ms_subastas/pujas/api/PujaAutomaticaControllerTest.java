package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.dto.PujaAutomaticaRequest;
import com.nexusbattles.ms_subastas.pujas.dto.PujaAutomaticaResponse;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.service.PujaAutomaticaApplicationService;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PujaAutomaticaControllerTest {

    @Mock
    private PujaAutomaticaApplicationService pujasAutomaticas;

    @Mock
    private IdentidadClient identidad;

    private PujaAutomaticaController controlador;

    @BeforeEach
    void setUp() {
        controlador = new PujaAutomaticaController(pujasAutomaticas, identidad);
    }

    @Test
    void configurarUsaElJugadorDelTokenYDevuelveLoQuedoGuardado() {
        UUID subastaId = UUID.randomUUID();
        UUID jugadorDelToken = UUID.randomUUID();
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(jugadorDelToken, false));
        when(pujasAutomaticas.configurar(subastaId, jugadorDelToken, new BigDecimal("400")))
                .thenReturn(new PujaAutomatica(UUID.randomUUID(), subastaId, jugadorDelToken,
                        new BigDecimal("400"), true));

        PujaAutomaticaResponse respuesta = controlador.configurar(
                subastaId, new PujaAutomaticaRequest(new BigDecimal("400")));

        assertEquals(jugadorDelToken, respuesta.jugadorId());
        assertEquals(new BigDecimal("400"), respuesta.limite());
        assertTrue(respuesta.activa());
    }

    @Test
    void desactivarUsaElJugadorDelToken() {
        UUID subastaId = UUID.randomUUID();
        UUID jugadorDelToken = UUID.randomUUID();
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(jugadorDelToken, false));

        controlador.desactivar(subastaId);

        verify(pujasAutomaticas).desactivar(subastaId, jugadorDelToken);
    }
}

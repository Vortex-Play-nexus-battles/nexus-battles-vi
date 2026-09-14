package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.dto.CompraInmediataRequest;
import com.nexusbattles.ms_subastas.pujas.dto.PujaResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujarRequest;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.pujas.service.PujaRechazadaException;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que importa de esta capa es exactamente una cosa: que el jugador salga del
 * token y no de la peticion. Estos dos endpoints mueven creditos, asi que
 * aceptar un jugadorId del cuerpo dejaria pujar en nombre de otro.
 */
@ExtendWith(MockitoExtension.class)
class PujaControllerTest {

    private static final String CLAVE = "clave-de-idempotencia-del-cliente";

    @Mock
    private PujaApplicationService pujas;

    @Mock
    private IdentidadClient identidad;

    private PujaController controlador;

    @BeforeEach
    void setUp() {
        controlador = new PujaController(pujas, identidad);
    }

    private Puja pujaDe(UUID subastaId, UUID jugadorId, String monto, EstadoPuja estado) {
        return new Puja(UUID.randomUUID(), subastaId, jugadorId, new BigDecimal(monto), TipoPuja.MANUAL,
                estado, Instant.parse("2026-09-13T12:00:00Z"), UUID.randomUUID().toString());
    }

    @Test
    void pujarUsaElJugadorDelTokenYLaClaveDeLaCabecera() {
        UUID subastaId = UUID.randomUUID();
        UUID jugadorDelToken = UUID.randomUUID();
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(jugadorDelToken, false));
        when(pujas.pujar(eq(subastaId), eq(jugadorDelToken), eq(new BigDecimal("110")), eq(CLAVE)))
                .thenReturn(pujaDe(subastaId, jugadorDelToken, "110", EstadoPuja.ACTIVA));

        PujaResponse respuesta = controlador.pujar(subastaId, CLAVE, new PujarRequest(new BigDecimal("110")));

        assertEquals(jugadorDelToken, respuesta.jugadorId());
        assertEquals(new BigDecimal("110"), respuesta.monto());
        assertEquals(EstadoPuja.ACTIVA, respuesta.estado());
    }

    @Test
    void comprarAhoraConfirmadaDelegaEnElServicio() {
        UUID subastaId = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(comprador, false));
        when(pujas.comprarAhora(subastaId, comprador, CLAVE))
                .thenReturn(pujaDe(subastaId, comprador, "500", EstadoPuja.GANADORA));

        PujaResponse respuesta = controlador.comprarAhora(subastaId, CLAVE, new CompraInmediataRequest(true));

        assertEquals(EstadoPuja.GANADORA, respuesta.estado());
        verify(pujas).comprarAhora(subastaId, comprador, CLAVE);
    }

    /**
     * La confirmacion se comprueba en el servidor, no solo en el dialogo de la
     * interfaz: un bug de front no puede cerrar una compra que el jugador no
     * pidio. Y se comprueba ANTES de resolver la identidad o tocar creditos.
     */
    @Test
    void comprarAhoraSinConfirmarSeRechazaYNoLlegaAlServicio() {
        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> controlador.comprarAhora(UUID.randomUUID(), CLAVE, new CompraInmediataRequest(false)));

        assertEquals(PujaRechazadaException.Motivo.CONFIRMACION_REQUERIDA, ex.getMotivo());
        verify(pujas, never()).comprarAhora(any(), any(), any());
        verifyNoInteractions(identidad);
    }

    /**
     * Por HTTP un cuerpo sin el campo lo corta la validacion con un 400, pero el
     * controlador no depende de eso: con confirmado nulo tampoco compra.
     */
    @Test
    void comprarAhoraConConfirmacionNulaTampocoCompra() {
        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> controlador.comprarAhora(UUID.randomUUID(), CLAVE, new CompraInmediataRequest(null)));

        assertEquals(PujaRechazadaException.Motivo.CONFIRMACION_REQUERIDA, ex.getMotivo());
        verify(pujas, never()).comprarAhora(any(), any(), any());
    }
}

package com.nexusbattles.ms_subastas.notificaciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El outbox persiste la intencion del aviso en la MISMA transaccion que el
 * cambio de negocio, para que no se pueda perder por una caida. Desde B8 es
 * idempotente por evento: el id de la fila se deriva del hecho, y el mismo
 * hecho encolado dos veces es un solo aviso (y una sola {@code Idempotency-Key}
 * ante correo).
 */
@ExtendWith(MockitoExtension.class)
class NotificacionOutboxTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");

    @Mock
    private NotificacionPendienteRepository repositorio;

    private NotificacionOutbox outbox;

    @BeforeEach
    void setUp() {
        outbox = new NotificacionOutbox(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void encolaSiempre() {
        when(repositorio.encolarSiNoExiste(any(), anyString(), any(), any(), anyString(), anyString(), any(),
                anyBoolean(), anyString(), anyString())).thenReturn(1);
    }

    // --- idempotencia por evento ---------------------------------------------

    @Test
    void elMismoHechoTieneSiempreElMismoIdYOtroHechoOtro() {
        UUID subasta = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        UUID uno = NotificacionOutbox.idDelEvento(TipoNotificacion.PUJA_SUPERADA, subasta, jugador, "puja-1");
        UUID otraVez = NotificacionOutbox.idDelEvento(TipoNotificacion.PUJA_SUPERADA, subasta, jugador, "puja-1");
        UUID otraPuja = NotificacionOutbox.idDelEvento(TipoNotificacion.PUJA_SUPERADA, subasta, jugador, "puja-2");
        UUID otroTipo = NotificacionOutbox.idDelEvento(TipoNotificacion.NUEVA_PUJA, subasta, jugador, "puja-1");
        UUID otroJugador = NotificacionOutbox.idDelEvento(TipoNotificacion.PUJA_SUPERADA, subasta,
                UUID.randomUUID(), "puja-1");

        assertEquals(uno, otraVez);
        assertNotEquals(uno, otraPuja);
        assertNotEquals(uno, otroTipo);
        assertNotEquals(uno, otroJugador);
    }

    @Test
    void encolarUsaElIdDelEventoYDiceSiEntroONo() {
        UUID subasta = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();
        UUID id = NotificacionOutbox.idDelEvento(TipoNotificacion.PUJA_SUPERADA, subasta, jugador, "p");
        when(repositorio.encolarSiNoExiste(eq(id), anyString(), any(), any(), anyString(), anyString(), any(),
                anyBoolean(), anyString(), anyString())).thenReturn(1, 0);

        assertTrue(outbox.encolar(TipoNotificacion.PUJA_SUPERADA, jugador, subasta, "p", null, "cuerpo"));
        assertFalse(outbox.encolar(TipoNotificacion.PUJA_SUPERADA, jugador, subasta, "p", null, "cuerpo"),
                "el segundo intento del mismo hecho no es otro aviso");
    }

    @Test
    void sinTituloPropioUsaElDelTipoYSinCorreoNoMarcaCorreo() {
        encolaSiempre();
        UUID subasta = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        outbox.encolar(TipoNotificacion.PUJA_SUPERADA, jugador, subasta, "p", null, "cuerpo");

        verify(repositorio).encolarSiNoExiste(any(), eq("PUJA_SUPERADA"), eq(jugador), eq(subasta),
                eq(TipoNotificacion.PUJA_SUPERADA.getTitulo()), eq("cuerpo"), eq(AHORA),
                eq(false), eq(""), eq(""));
    }

    @Test
    void conCorreoHabilitadoLosTiposDeCorreoQuedanPendientesDeCorreo() {
        encolaSiempre();
        outbox = new NotificacionOutbox(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC), true);
        UUID subasta = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        outbox.encolar(TipoNotificacion.SUBASTA_GANADA, jugador, subasta, "cierre", "Ganaste", "cuerpo");
        outbox.encolar(TipoNotificacion.PUJA_REGISTRADA, jugador, subasta, "p", null, "cuerpo");

        verify(repositorio).encolarSiNoExiste(any(), eq("SUBASTA_GANADA"), eq(jugador), eq(subasta),
                eq("Ganaste"), eq("cuerpo"), eq(AHORA), eq(true), eq("Ganaste"), eq("PENDIENTE"));
        // PUJA_REGISTRADA es solo de bandeja: confirma lo que el jugador acaba de hacer.
        verify(repositorio).encolarSiNoExiste(any(), eq("PUJA_REGISTRADA"), eq(jugador), eq(subasta),
                anyString(), eq("cuerpo"), eq(AHORA), eq(false), eq(""), eq(""));
        assertTrue(outbox.correoHabilitado());
    }

    @Test
    void unCuerpoNuloViajaComoVacio() {
        encolaSiempre();
        outbox.encolar(TipoNotificacion.SUBASTA_PUBLICADA, UUID.randomUUID(), UUID.randomUUID(), "x", null, null);

        verify(repositorio).encolarSiNoExiste(any(), anyString(), any(), any(), anyString(), eq(""), any(),
                anyBoolean(), anyString(), anyString());
    }

    @Test
    void faltarElDestinatarioOLaSubastaEsUnErrorDeProgramacion() {
        assertThrows(NullPointerException.class,
                () -> outbox.encolar(TipoNotificacion.PUJA_SUPERADA, null, UUID.randomUUID(), "x", null, ""));
        assertThrows(NullPointerException.class,
                () -> outbox.encolar(TipoNotificacion.PUJA_SUPERADA, UUID.randomUUID(), null, "x", null, ""));
        verifyNoInteractions(repositorio);
    }

    // --- los avisos de HU-SUB-004 (criterios 2 y 4) ----------------------------

    @Test
    void encolaUnAvisoPorCadaPostorCuandoUnaCompraInmediataCierraLaSubasta() {
        encolaSiempre();
        UUID subastaId = UUID.randomUUID();
        UUID postorUno = UUID.randomUUID();
        UUID postorDos = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();

        outbox.avisarCierrePorCompraInmediata(subastaId, List.of(postorUno, postorDos, postorUno), comprador);

        ArgumentCaptor<UUID> destinatarios = ArgumentCaptor.forClass(UUID.class);
        verify(repositorio, times(2)).encolarSiNoExiste(any(), eq("SUBASTA_CERRADA_POR_COMPRA_INMEDIATA"),
                destinatarios.capture(), eq(subastaId), anyString(), anyString(), eq(AHORA), anyBoolean(),
                anyString(), anyString());
        assertEquals(List.of(postorUno, postorDos), destinatarios.getAllValues());
    }

    @Test
    void noAvisaAlPropioCompradorPorqueYaSabeQueCompro() {
        encolaSiempre();
        UUID subastaId = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        UUID otroPostor = UUID.randomUUID();

        outbox.avisarCierrePorCompraInmediata(subastaId, List.of(comprador, otroPostor), comprador);

        verify(repositorio).encolarSiNoExiste(any(), anyString(), eq(otroPostor), eq(subastaId), anyString(),
                anyString(), any(), anyBoolean(), anyString(), anyString());
        verify(repositorio, never()).encolarSiNoExiste(any(), anyString(), eq(comprador), any(), anyString(),
                anyString(), any(), anyBoolean(), anyString(), anyString());
    }

    @Test
    void siNadieHabiaPujadoNoEncolaNada() {
        outbox.avisarCierrePorCompraInmediata(UUID.randomUUID(), List.of(), UUID.randomUUID());

        verifyNoInteractions(repositorio);
    }

    @Test
    void encolaElAvisoDeLimiteAlcanzadoDeUnaPujaAutomatica() {
        encolaSiempre();
        UUID subastaId = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        outbox.avisarLimiteAutomaticoAlcanzado(subastaId, jugador, new BigDecimal("200.00"));

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(repositorio).encolarSiNoExiste(any(), eq("LIMITE_AUTOMATICO_ALCANZADO"), eq(jugador), eq(subastaId),
                anyString(), detalle.capture(), any(), anyBoolean(), anyString(), anyString());
        assertTrue(detalle.getValue().contains("200.00"), "el aviso debe decirle al jugador cual era su limite");
    }

    @Test
    void encolaElAvisoDeAutomaticaSinSaldo() {
        encolaSiempre();
        UUID subastaId = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        outbox.avisarAutomaticaSinSaldo(subastaId, jugador);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(repositorio).encolarSiNoExiste(any(), eq("AUTOMATICA_SIN_SALDO"), eq(jugador), eq(subastaId),
                anyString(), detalle.capture(), any(), anyBoolean(), anyString(), anyString());
        assertTrue(detalle.getValue().contains("saldo disponible no alcanza"),
                "el aviso debe ser honesto y explicar que la causa fue la falta de saldo");
    }
}

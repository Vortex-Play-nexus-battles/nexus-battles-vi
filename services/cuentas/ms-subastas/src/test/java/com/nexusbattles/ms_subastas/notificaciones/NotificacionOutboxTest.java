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
import static org.mockito.Mockito.*;

/**
 * El outbox cubre los dos avisos que exige HU-SUB-004 ("notificando a quienes
 * hubieran pujado" en la compra inmediata, y "se detiene notificando al
 * alcanzar el limite" en la puja automatica) hasta la frontera de este
 * servicio. El envio real es del microservicio de notificaciones, que no esta
 * en el Sprint 2: aqui solo se persiste la intencion, en la MISMA transaccion
 * que el cambio de negocio, para que no se pueda perder un aviso por una caida.
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

    @Test
    void encolaUnAvisoPorCadaPostorCuandoUnaCompraInmediataCierraLaSubasta() {
        UUID subastaId = UUID.randomUUID();
        UUID postorUno = UUID.randomUUID();
        UUID postorDos = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();

        outbox.avisarCierrePorCompraInmediata(subastaId, List.of(postorUno, postorDos), comprador);

        ArgumentCaptor<List<NotificacionPendiente>> captor = ArgumentCaptor.captor();
        verify(repositorio).saveAll(captor.capture());

        List<NotificacionPendiente> encoladas = captor.getValue();
        assertEquals(2, encoladas.size());
        assertTrue(encoladas.stream().allMatch(n -> n.getTipo() == TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA));
        assertTrue(encoladas.stream().allMatch(n -> n.getSubastaId().equals(subastaId)));
        assertTrue(encoladas.stream().allMatch(n -> n.getCreadaEn().equals(AHORA)));
        assertTrue(encoladas.stream().allMatch(n -> n.getEnviadaEn() == null));
        assertEquals(List.of(postorUno, postorDos),
                encoladas.stream().map(NotificacionPendiente::getDestinatarioId).toList());
    }

    @Test
    void noAvisaAlPropioCompradorPorqueYaSabeQueCompro() {
        UUID subastaId = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        UUID otroPostor = UUID.randomUUID();

        outbox.avisarCierrePorCompraInmediata(subastaId, List.of(comprador, otroPostor), comprador);

        ArgumentCaptor<List<NotificacionPendiente>> captor = ArgumentCaptor.captor();
        verify(repositorio).saveAll(captor.capture());

        assertEquals(List.of(otroPostor),
                captor.getValue().stream().map(NotificacionPendiente::getDestinatarioId).toList());
    }

    @Test
    void siNadieHabiaPujadoNoEncolaNada() {
        outbox.avisarCierrePorCompraInmediata(UUID.randomUUID(), List.of(), UUID.randomUUID());

        verify(repositorio, never()).saveAll(any());
    }

    @Test
    void encolaElAvisoDeLimiteAlcanzadoDeUnaPujaAutomatica() {
        UUID subastaId = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        outbox.avisarLimiteAutomaticoAlcanzado(subastaId, jugador, new BigDecimal("200.00"));

        ArgumentCaptor<NotificacionPendiente> captor = ArgumentCaptor.captor();
        verify(repositorio).save(captor.capture());

        NotificacionPendiente encolada = captor.getValue();
        assertEquals(TipoNotificacion.LIMITE_AUTOMATICO_ALCANZADO, encolada.getTipo());
        assertEquals(jugador, encolada.getDestinatarioId());
        assertEquals(subastaId, encolada.getSubastaId());
        assertTrue(encolada.getDetalle().contains("200.00"),
                "el aviso debe decirle al jugador cual era su limite");
    }
}

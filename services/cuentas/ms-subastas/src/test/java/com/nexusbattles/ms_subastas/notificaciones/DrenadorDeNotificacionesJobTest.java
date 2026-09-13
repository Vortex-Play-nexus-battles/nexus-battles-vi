package com.nexusbattles.ms_subastas.notificaciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El outbox ya escribia los avisos; lo que faltaba era sacarlos de la tabla.
 * Lo que se fija aqui es el comportamiento ante fallos, que es donde un
 * drenador mal hecho duplica avisos o los pierde.
 */
@ExtendWith(MockitoExtension.class)
class DrenadorDeNotificacionesJobTest {

    private static final Instant AHORA = Instant.parse("2026-09-13T18:00:00Z");

    @Mock
    private NotificacionPendienteRepository repositorio;

    @Mock
    private NotificacionesClient notificaciones;

    private DrenadorDeNotificacionesJob drenador;

    @BeforeEach
    void setUp() {
        drenador = new DrenadorDeNotificacionesJob(repositorio, notificaciones,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private NotificacionPendiente pendiente(TipoNotificacion tipo, String detalle) {
        return new NotificacionPendiente(UUID.randomUUID(), tipo, UUID.randomUUID(), UUID.randomUUID(),
                detalle, AHORA.minusSeconds(30), null);
    }

    @Test
    void entregaElAvisoYMarcaLaFilaComoEnviada() {
        NotificacionPendiente aviso = pendiente(TipoNotificacion.LIMITE_AUTOMATICO_ALCANZADO, "llegaste al limite");
        when(repositorio.findByEnviadaEnIsNullOrderByCreadaEnAsc()).thenReturn(List.of(aviso));

        drenador.drenar();

        ArgumentCaptor<NotificacionesClient.Aviso> enviado =
                ArgumentCaptor.forClass(NotificacionesClient.Aviso.class);
        verify(notificaciones).entregar(enviado.capture());

        assertEquals(aviso.getId(), enviado.getValue().eventoId(),
                "el id del outbox viaja como eventoId: es lo que evita duplicados al reintentar");
        assertEquals(aviso.getDestinatarioId(), enviado.getValue().destinatarioId());
        assertEquals("llegaste al limite", enviado.getValue().cuerpo());
        assertEquals("Tu puja automatica se detuvo", enviado.getValue().titulo());

        assertEquals(AHORA, aviso.getEnviadaEn());
        verify(repositorio).save(aviso);
    }

    /**
     * Si se atrasa el drenaje, la bandeja tiene que mostrar cuando se cerro la
     * subasta, no cuando el drenador consiguio entregar el aviso.
     */
    @Test
    void elAvisoLlevaLaHoraDelHechoNoLaDeLaEntrega() {
        NotificacionPendiente aviso = pendiente(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, "cerrada");
        when(repositorio.findByEnviadaEnIsNullOrderByCreadaEnAsc()).thenReturn(List.of(aviso));

        drenador.drenar();

        ArgumentCaptor<NotificacionesClient.Aviso> enviado =
                ArgumentCaptor.forClass(NotificacionesClient.Aviso.class);
        verify(notificaciones).entregar(enviado.capture());
        assertEquals(AHORA.minusSeconds(30), enviado.getValue().creadaEn());
    }

    /**
     * Lo que separa un drenador correcto de uno que duplica avisos: cada fila
     * se marca por su cuenta. Con una transaccion unica, el fallo del tercero
     * desharia el marcado de los dos primeros y el jugador los recibiria otra vez.
     */
    @Test
    void unFalloAMitadDeLoteNoDesmarcaLoYaEntregado() {
        NotificacionPendiente primero = pendiente(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, "uno");
        NotificacionPendiente segundo = pendiente(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, "dos");
        NotificacionPendiente tercero = pendiente(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, "tres");
        when(repositorio.findByEnviadaEnIsNullOrderByCreadaEnAsc())
                .thenReturn(List.of(primero, segundo, tercero));
        // Un unico stub que cubre las tres llamadas y decide dentro. Stubbing
        // por matcher solo para la tercera hace que Mockito, en modo estricto,
        // lance PotentialStubbingProblem en la primera: el drenador lo
        // atrapa como si el modulo hubiera fallado y la prueba mide otra cosa.
        doAnswer(invocacion -> {
            NotificacionesClient.Aviso aviso = invocacion.getArgument(0);
            if (tercero.getId().equals(aviso.eventoId())) {
                throw new NotificacionesClientException("el modulo no responde");
            }
            return null;
        }).when(notificaciones).entregar(any());

        drenador.drenar();

        assertNotNull(primero.getEnviadaEn());
        assertNotNull(segundo.getEnviadaEn());
        assertNull(tercero.getEnviadaEn(), "el que fallo se queda en el outbox para reintentarlo");
        verify(repositorio).save(primero);
        verify(repositorio).save(segundo);
        verify(repositorio, never()).save(tercero);
    }

    /**
     * Si el modulo esta caido no lo va a estar menos para los 200 avisos
     * siguientes: se corta el lote en vez de castigar a un servicio caido y
     * llenar el log de la misma linea repetida.
     */
    @Test
    void alPrimerFalloSeCortaElLoteEnVezDeSeguirIntentando() {
        NotificacionPendiente primero = pendiente(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, "uno");
        NotificacionPendiente segundo = pendiente(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, "dos");
        when(repositorio.findByEnviadaEnIsNullOrderByCreadaEnAsc()).thenReturn(List.of(primero, segundo));
        doThrow(new NotificacionesClientException("el modulo no responde"))
                .when(notificaciones).entregar(any());

        drenador.drenar();

        verify(notificaciones, times(1)).entregar(any());
        verify(repositorio, never()).save(any());
    }

    @Test
    void sinAvisosPendientesNoLlamaAlModuloDeNotificaciones() {
        when(repositorio.findByEnviadaEnIsNullOrderByCreadaEnAsc()).thenReturn(List.of());

        drenador.drenar();

        verifyNoInteractions(notificaciones);
    }
}

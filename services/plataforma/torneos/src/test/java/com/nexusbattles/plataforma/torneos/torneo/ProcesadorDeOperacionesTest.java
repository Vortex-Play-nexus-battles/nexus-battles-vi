package com.nexusbattles.plataforma.torneos.torneo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El procesador sin base de datos: el presupuesto de tiempo de la peticion,
 * el reclamo perdido frente a otra ejecucion y los fallos sin clasificar.
 * El recorrido completo contra PostgreSQL esta en {@code CobroIdempotenteIT}.
 */
@DisplayName("Torneos · procesador de operaciones")
class ProcesadorDeOperacionesTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");

    private final OperacionRepository operaciones = mock(OperacionRepository.class);
    private final LibroDeCreditos libro = mock(LibroDeCreditos.class);
    private final TransactionTemplate transaccion = mock(TransactionTemplate.class);
    private final PoliticaDeReintentos politica = new PoliticaDeReintentos(Duration.ofSeconds(15),
            Duration.ofMinutes(30), 3, Duration.ofMinutes(2));

    @BeforeEach
    void transaccionesEnElMismoHilo() {
        when(transaccion.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        doAnswer(inv -> {
            Consumer<TransactionStatus> accion = inv.getArgument(0);
            accion.accept(new SimpleTransactionStatus());
            return null;
        }).when(transaccion).executeWithoutResult(any());
    }

    private ProcesadorDeOperaciones procesador(long presupuestoMs) {
        return new ProcesadorDeOperaciones(operaciones, mock(TorneoRepository.class), mock(EquipoRepository.class),
                libro, mock(EntregaDeInventario.class), mock(AvisosAlJugador.class), mock(ConsultaDeSanciones.class),
                mock(Hitos.class), politica, transaccion, Clock.fixed(AHORA, ZoneOffset.UTC), presupuestoMs);
    }

    private void pendientesDelTorneo(UUID torneo, UUID... ids) {
        when(operaciones.porAtenderDelTorneo(eq(torneo), anyCollection(), anyCollection(), any(), any(), any()))
                .thenReturn(List.of(ids));
    }

    @Test
    @DisplayName("con los proveedores sanos la peticion intenta todas las operaciones del torneo")
    void conPresupuestoSeIntentanTodas() {
        UUID torneo = UUID.randomUUID();
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        pendientesDelTorneo(torneo, primera, segunda);

        procesador(3000).procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);

        verify(operaciones).reclamar(eq(primera), anyCollection(), any(), any(), any());
        verify(operaciones).reclamar(eq(segunda), anyCollection(), any(), any(), any());
    }

    @Test
    @DisplayName("si un proveedor tarda, la peticion deja de esperar y lo que falta queda para la tarea")
    void presupuestoAgotado() {
        UUID torneo = UUID.randomUUID();
        UUID lenta = UUID.randomUUID();
        UUID siguiente = UUID.randomUUID();
        pendientesDelTorneo(torneo, lenta, siguiente);
        when(operaciones.reclamar(eq(lenta), anyCollection(), any(), any(), any())).thenAnswer(inv -> {
            Thread.sleep(80);
            return 0;
        });

        procesador(20).procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);

        verify(operaciones).reclamar(eq(lenta), anyCollection(), any(), any(), any());
        verify(operaciones, never()).reclamar(eq(siguiente), anyCollection(), any(), any(), any());
    }

    @Test
    @DisplayName("si otra ejecucion ya la reclamo, no se toca")
    void reclamoPerdido() {
        UUID id = UUID.randomUUID();
        when(operaciones.reclamar(eq(id), anyCollection(), any(), any(), any())).thenReturn(0);

        procesador(3000).procesar(id);

        verify(operaciones, never()).findById(any());
        verify(libro, never()).consumir(any());
    }

    @Test
    @DisplayName("un fallo sin clasificar se reintenta con espera en vez de perderse")
    void falloInesperadoSeReintenta() {
        Operacion op = Operacion.cobro(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                10, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        when(operaciones.reclamar(eq(op.id()), anyCollection(), any(), any(), any())).thenReturn(1);
        when(operaciones.findById(op.id())).thenReturn(Optional.of(op));
        doThrow(new IllegalStateException("fallo raro")).when(libro).consumir(op.reservaId());

        procesador(3000).procesar(op.id());

        assertThat(op.estado()).isEqualTo(Operacion.Estado.REINTENTABLE);
        assertThat(op.ultimoError()).contains("IllegalStateException");
        assertThat(op.proximoIntento()).isAfter(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("agotados los intentos, un fallo sin clasificar la deja FALLIDA para revision")
    void falloInesperadoAgotado() {
        UUID id = UUID.randomUUID();
        UUID reserva = UUID.randomUUID();
        Operacion agotada = mock(Operacion.class);
        when(agotada.id()).thenReturn(id);
        when(agotada.tipo()).thenReturn(Operacion.Tipo.COBRO_INSCRIPCION);
        when(agotada.reservaId()).thenReturn(reserva);
        when(agotada.intentos()).thenReturn(3);
        when(operaciones.reclamar(eq(id), anyCollection(), any(), any(), any())).thenReturn(1);
        when(operaciones.findById(id)).thenReturn(Optional.of(agotada));
        doThrow(new IllegalStateException("fallo raro")).when(libro).consumir(reserva);

        procesador(3000).procesar(id);

        verify(agotada).fallida(org.mockito.ArgumentMatchers.startsWith("agotados 3 intentos"), any());
        verify(agotada, never()).reintentable(any(), any(), any());
    }
}

package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.panel.model.Seguimiento;
import com.nexusbattles.ms_subastas.panel.repository.SeguimientoRepository;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El «Panel de gestion personal» de 7.7.9: mis subastas (con si se pueden
 * cancelar y cuanto costaria), la lista de seguimiento y el historial de
 * transacciones con su exportacion.
 */
@DisplayName("Panel de gestion personal (7.7.9)")
class PanelDelJugadorTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final UUID YO = UUID.randomUUID();

    private final SubastaRepository subastas = mock(SubastaRepository.class);

    private static Subasta subasta(UUID vendedor, EstadoSubasta estado, Duration faltan) {
        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), vendedor, new BigDecimal("100"),
                BigDecimal.TEN, null, null, estado, AHORA.plus(faltan), 0L);
        subasta.setNombreProducto("Producto " + subasta.getId().toString().substring(0, 4));
        subasta.setComisionCobrada(new BigDecimal("3"));
        subasta.setFechaPublicacion(AHORA.minus(Duration.ofHours(1)));
        return subasta;
    }

    @Nested
    @DisplayName("mis subastas")
    class MisSubastas {

        private final MisPublicacionesService servicio = new MisPublicacionesService(subastas, RELOJ);

        @Test
        @DisplayName("dice cuales se pueden cancelar todavia y cuanto costaria")
        void cancelableYPenalizacion() {
            Subasta cancelable = subasta(YO, EstadoSubasta.ACTIVA, Duration.ofHours(30));
            Subasta conPujas = subasta(YO, EstadoSubasta.ACTIVA, Duration.ofHours(30));
            conPujas.setMejorPostorId(UUID.randomUUID());
            conPujas.setCantidadPujas(1);
            Subasta porCerrar = subasta(YO, EstadoSubasta.ACTIVA, Duration.ofHours(5));
            Subasta vendida = subasta(YO, EstadoSubasta.ADJUDICADA, Duration.ofHours(-5));
            when(subastas.findByVendedorIdOrderByFechaFinDesc(YO))
                    .thenReturn(List.of(cancelable, conPujas, porCerrar, vendida));

            List<PanelDtos.MiPublicacion> mias = servicio.de(YO, null);

            assertEquals(List.of(true, false, false, false),
                    mias.stream().map(PanelDtos.MiPublicacion::cancelable).toList());
            assertEquals(0, new BigDecimal("1.50").compareTo(mias.getFirst().penalizacionSiCancela()));
            assertNull(mias.get(1).penalizacionSiCancela(), "si no se puede cancelar, no hay cifra que mostrar");
            assertEquals("ADJUDICADA", mias.get(3).estado());
        }

        @Test
        @DisplayName("con filtro de estado pregunta solo por ese estado")
        void filtraPorEstado() {
            when(subastas.findByVendedorIdAndEstadoOrderByFechaFinDesc(YO, EstadoSubasta.CANCELADA))
                    .thenReturn(List.of());

            assertTrue(servicio.de(YO, EstadoSubasta.CANCELADA).isEmpty());
            verify(subastas, never()).findByVendedorIdOrderByFechaFinDesc(any());
        }

        @Test
        @DisplayName("la regla del boton es la misma que la de la cancelacion")
        void reglaDeCancelable() {
            assertTrue(MisPublicacionesService.esCancelable(subasta(YO, EstadoSubasta.ACTIVA, Duration.ofHours(7)), AHORA));
            assertFalse(MisPublicacionesService.esCancelable(subasta(YO, EstadoSubasta.ACTIVA, Duration.ofHours(6)), AHORA));
            assertFalse(MisPublicacionesService.esCancelable(subasta(YO, EstadoSubasta.CANCELADA, Duration.ofHours(30)), AHORA));
        }
    }

    @Nested
    @DisplayName("lista de seguimiento")
    class ListaDeSeguimiento {

        private final SeguimientoRepository seguimientos = mock(SeguimientoRepository.class);
        private final SeguimientoService servicio = new SeguimientoService(subastas, seguimientos, RELOJ);

        @Test
        @DisplayName("seguir una activa la agrega (idempotente en la base)")
        void seguirUnaActiva() {
            Subasta activa = subasta(UUID.randomUUID(), EstadoSubasta.ACTIVA, Duration.ofHours(3));
            when(subastas.findById(activa.getId())).thenReturn(Optional.of(activa));

            servicio.seguir(activa.getId(), YO);

            verify(seguimientos).seguir(activa.getId(), YO, AHORA);
        }

        @Test
        @DisplayName("seguir una terminada no avisaria de nada: 409")
        void seguirUnaTerminada() {
            Subasta terminada = subasta(UUID.randomUUID(), EstadoSubasta.SIN_ADJUDICACION, Duration.ofHours(-3));
            when(subastas.findById(terminada.getId())).thenReturn(Optional.of(terminada));

            OperacionRechazadaException rechazo = assertThrows(OperacionRechazadaException.class,
                    () -> servicio.seguir(terminada.getId(), YO));

            assertEquals(OperacionRechazadaException.Motivo.SUBASTA_NO_ACTIVA, rechazo.getMotivo());
            verifyNoInteractions(seguimientos);
        }

        @Test
        @DisplayName("seguir o dejar de seguir una que no existe es 404")
        void inexistente() {
            UUID id = UUID.randomUUID();
            when(subastas.findById(id)).thenReturn(Optional.empty());
            when(subastas.existsById(id)).thenReturn(false);

            assertThrows(SubastaNoEncontradaException.class, () -> servicio.seguir(id, YO));
            assertThrows(SubastaNoEncontradaException.class, () -> servicio.dejarDeSeguir(id, YO));
            verifyNoInteractions(seguimientos);
        }

        @Test
        @DisplayName("dejar de seguir funciona tambien con subastas ya terminadas")
        void dejarDeSeguir() {
            UUID id = UUID.randomUUID();
            when(subastas.existsById(id)).thenReturn(true);

            servicio.dejarDeSeguir(id, YO);

            verify(seguimientos).dejarDeSeguir(id, YO);
        }

        @Test
        @DisplayName("activas primero por la que cierra antes; despues las terminadas, la mas reciente primero")
        void ordenDeLaLista() {
            Subasta cierraPronto = subasta(UUID.randomUUID(), EstadoSubasta.ACTIVA, Duration.ofHours(1));
            Subasta cierraTarde = subasta(UUID.randomUUID(), EstadoSubasta.ACTIVA, Duration.ofHours(20));
            Subasta terminoAyer = subasta(UUID.randomUUID(), EstadoSubasta.ADJUDICADA, Duration.ofHours(-24));
            Subasta terminoHoy = subasta(UUID.randomUUID(), EstadoSubasta.CANCELADA, Duration.ofHours(-1));
            UUID borrada = UUID.randomUUID();
            List<Seguimiento> filas = List.of(
                    new Seguimiento(terminoAyer.getId(), YO, AHORA.minusSeconds(40)),
                    new Seguimiento(cierraTarde.getId(), YO, AHORA.minusSeconds(30)),
                    new Seguimiento(borrada, YO, AHORA.minusSeconds(25)),
                    new Seguimiento(terminoHoy.getId(), YO, AHORA.minusSeconds(20)),
                    new Seguimiento(cierraPronto.getId(), YO, AHORA.minusSeconds(10)));
            when(seguimientos.findByJugadorIdOrderByCreadoEnDesc(YO)).thenReturn(filas);
            when(subastas.findAllById(any())).thenReturn(List.of(cierraPronto, cierraTarde, terminoAyer, terminoHoy));

            List<PanelDtos.SubastaSeguida> lista = servicio.lista(YO);

            assertEquals(List.of(cierraPronto.getId(), cierraTarde.getId(), terminoHoy.getId(), terminoAyer.getId()),
                    lista.stream().map(PanelDtos.SubastaSeguida::subastaId).toList());
            assertEquals(AHORA.minusSeconds(10), lista.getFirst().seguidaDesde());
        }

        @Test
        @DisplayName("sin nada seguido, lista vacia")
        void vacia() {
            when(seguimientos.findByJugadorIdOrderByCreadoEnDesc(YO)).thenReturn(List.of());

            assertTrue(servicio.lista(YO).isEmpty());
            verify(subastas, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("historial de transacciones")
    class Historial {

        private final PujaRepository pujas = mock(PujaRepository.class);
        private final HistorialService servicio = new HistorialService(subastas, pujas);

        private Puja ganadora(Subasta subasta, String monto) {
            return new Puja(UUID.randomUUID(), subasta.getId(), YO, new BigDecimal(monto), TipoPuja.MANUAL,
                    EstadoPuja.GANADORA, AHORA.minus(Duration.ofDays(3)), UUID.randomUUID().toString());
        }

        @Test
        @DisplayName("compras, ventas, comisiones y penalizaciones, con el balance")
        void historialConTotales() {
            Subasta comprada = subasta(UUID.randomUUID(), EstadoSubasta.ADJUDICADA, Duration.ofDays(-2));
            comprada.setCerradaEn(AHORA.minus(Duration.ofDays(2)));
            Subasta vendida = subasta(YO, EstadoSubasta.ADJUDICADA, Duration.ofDays(-1));
            vendida.setOfertaVigente(new BigDecimal("300"));
            vendida.setFechaPublicacion(AHORA.minus(Duration.ofDays(4)));
            vendida.setCerradaEn(AHORA.minus(Duration.ofDays(1)));
            Subasta cancelada = subasta(YO, EstadoSubasta.CANCELADA, Duration.ofDays(1));
            cancelada.setComisionCobrada(BigDecimal.ONE);
            cancelada.setPenalizacionCobrada(new BigDecimal("0.50"));
            cancelada.setFechaPublicacion(AHORA.minus(Duration.ofHours(3)));
            cancelada.setCerradaEn(AHORA.minus(Duration.ofHours(1)));
            when(pujas.findByJugadorIdAndEstado(YO, EstadoPuja.GANADORA)).thenReturn(List.of(ganadora(comprada, "150")));
            when(subastas.findAllById(List.of(comprada.getId()))).thenReturn(List.of(comprada));
            when(subastas.findByVendedorIdOrderByFechaFinDesc(YO)).thenReturn(List.of(vendida, cancelada));

            PanelDtos.Historial historial = servicio.de(YO);

            assertEquals(List.of("PENALIZACION", "COMISION", "VENTA", "COMPRA", "COMISION"),
                    historial.movimientos().stream().map(PanelDtos.Movimiento::tipo).toList(),
                    "del mas reciente al mas antiguo");
            assertEquals(0, new BigDecimal("300").compareTo(historial.totalGanado()));
            // 150 de la compra + 3 + 1 de comisiones + 0,50 de penalizacion.
            assertEquals(0, new BigDecimal("154.50").compareTo(historial.totalGastado()));
            assertEquals(0, new BigDecimal("4.50").compareTo(historial.comisionesPagadas()));
            assertEquals(0, new BigDecimal("145.50").compareTo(historial.balance()));
        }

        @Test
        @DisplayName("sin actividad, todo a cero")
        void historialVacio() {
            when(pujas.findByJugadorIdAndEstado(YO, EstadoPuja.GANADORA)).thenReturn(List.of());
            when(subastas.findByVendedorIdOrderByFechaFinDesc(YO)).thenReturn(List.of());

            PanelDtos.Historial historial = servicio.de(YO);

            assertTrue(historial.movimientos().isEmpty());
            assertEquals(0, BigDecimal.ZERO.compareTo(historial.balance()));
            verify(subastas, never()).findAllById(any());
        }

        @Test
        @DisplayName("se exporta en CSV con cabecera")
        void exportaCsv() {
            Subasta vendida = subasta(YO, EstadoSubasta.ADJUDICADA, Duration.ofDays(-1));
            vendida.setNombreProducto("Espada \"legendaria\"");
            vendida.setComisionCobrada(null);
            vendida.setCerradaEn(AHORA.minus(Duration.ofDays(1)));
            when(pujas.findByJugadorIdAndEstado(YO, EstadoPuja.GANADORA)).thenReturn(List.of());
            when(subastas.findByVendedorIdOrderByFechaFinDesc(YO)).thenReturn(List.of(vendida));

            String csv = servicio.comoCsv(YO);

            String[] lineas = csv.split("\n");
            assertEquals(HistorialService.CABECERA_CSV, lineas[0]);
            assertEquals("VENTA," + vendida.getId() + ",\"Espada \"\"legendaria\"\"\",100,"
                    + AHORA.minus(Duration.ofDays(1)), lineas[1]);
            assertEquals(2, lineas.length);
        }

        @Test
        @DisplayName("un nombre que empieza por = + - @ no se ejecuta como formula en la hoja de calculo")
        void celdaContraInyeccionCsv() {
            assertEquals("\"'=HYPERLINK(1)\"", HistorialService.celda("=HYPERLINK(1)"));
            assertEquals("\"'+1\"", HistorialService.celda("+1"));
            assertEquals("\"'-1\"", HistorialService.celda("-1"));
            assertEquals("\"'@a\"", HistorialService.celda("@a"));
            assertEquals("\"normal\"", HistorialService.celda("normal"));
            assertEquals("\"\"", HistorialService.celda(""));
            assertEquals("", HistorialService.celda(null));
        }
    }
}

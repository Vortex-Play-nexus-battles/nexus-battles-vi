package nexus.alertas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

class AlertasCatalogoServicioTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T15:30:00Z");

    private AlertaCatalogoRepository alertas;
    private ConsultaAlertasJugadorRepository consultas;
    private ProductoRepository productos;
    private AlertasCatalogoServicio servicio;

    @BeforeEach
    void preparar() {
        alertas = mock(AlertaCatalogoRepository.class);
        consultas = mock(ConsultaAlertasJugadorRepository.class);
        productos = mock(ProductoRepository.class);
        servicio = new AlertasCatalogoServicio(
                alertas,
                consultas,
                productos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(alertas.save(any(AlertaCatalogo.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("registra el cambio con descripcion y fecha de implementacion")
    void registraCambioConInformacionClara() {
        Producto producto = producto(Instant.parse("2026-09-11T14:20:00Z"));

        AlertaCatalogo alerta = servicio.registrar(
                TipoCambioCatalogo.CAMBIO_BALANCE,
                producto);

        assertEquals("producto-1", alerta.productoId());
        assertEquals("Espada solar", alerta.productoNombre());
        assertEquals(TipoCambioCatalogo.CAMBIO_BALANCE, alerta.tipo());
        assertEquals("Se actualizo el balance de Espada solar.", alerta.descripcion());
        assertEquals(AHORA, alerta.implementadaEn());
        verify(alertas).save(alerta);
    }

    @Test
    @DisplayName("entrega al jugador solo los cambios posteriores a su ultimo ingreso")
    void entregaPendientesYActualizaLaConsulta() {
        Instant ingresoAnterior = Instant.parse("2026-09-10T12:00:00Z");
        AlertaCatalogo pendiente = new AlertaCatalogo(
                "alerta-1",
                "producto-1",
                "Espada solar",
                TipoCambioCatalogo.PRODUCTO_MODIFICADO,
                "El producto Espada solar fue modificado.",
                Instant.parse("2026-09-11T10:00:00Z"));
        when(consultas.findById("jugador-7"))
                .thenReturn(Optional.of(new ConsultaAlertasJugador(
                        "jugador-7",
                        ingresoAnterior)));
        when(alertas
                .buscarImplementadasEntre(
                        ingresoAnterior,
                        AHORA))
                .thenReturn(List.of(pendiente));

        List<AlertaCatalogo> resultado = servicio
                .consultarAlIniciarSesion("jugador-7");

        assertEquals(List.of(pendiente), resultado);
        ArgumentCaptor<ConsultaAlertasJugador> marcador =
                ArgumentCaptor.forClass(ConsultaAlertasJugador.class);
        verify(consultas).save(marcador.capture());
        assertEquals("jugador-7", marcador.getValue().jugadorId());
        assertEquals(pendiente.implementadaEn(), marcador.getValue().consultadoHasta());
    }

    @Test
    @DisplayName("un primer ingreso no vuelca el historial: guarda la linea base y no entrega alertas")
    void primerIngresoGuardaLineaBaseSinEntregarHistorial() {
        when(consultas.findById("jugador-nuevo")).thenReturn(Optional.empty());

        List<AlertaCatalogo> resultado = servicio.consultarAlIniciarSesion("jugador-nuevo");

        assertEquals(List.of(), resultado);
        verify(alertas, never()).buscarImplementadasEntre(any(), any());
        ArgumentCaptor<ConsultaAlertasJugador> lineaBase =
                ArgumentCaptor.forClass(ConsultaAlertasJugador.class);
        verify(consultas).save(lineaBase.capture());
        assertEquals("jugador-nuevo", lineaBase.getValue().jugadorId());
        assertEquals(AHORA, lineaBase.getValue().consultadoHasta());
    }

    @Test
    @DisplayName("tras la linea base, el siguiente ingreso recibe solo lo posterior a ella")
    void siguienteIngresoPideSoloLoPosteriorALaLineaBase() {
        when(consultas.findById("jugador-7"))
                .thenReturn(Optional.of(new ConsultaAlertasJugador("jugador-7", AHORA)));
        when(alertas.buscarImplementadasEntre(AHORA, AHORA)).thenReturn(List.of());

        assertEquals(List.of(), servicio.consultarAlIniciarSesion("jugador-7"));
        verify(alertas).buscarImplementadasEntre(AHORA, AHORA);
        verify(consultas, never()).save(any());
    }

    @Test
    @DisplayName("rechaza un inicio sin identidad de jugador")
    void exigeIdentidad() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.consultarAlIniciarSesion(" "));
    }

    /**
     * HU-NOT-001 (#532), productos.yaml 1.6.0 — la lectura de los cambios para
     * otro servicio (notificaciones). Es de solo lectura: el cursor por jugador
     * de inicio-sesion ({@code consultas}) no se toca en ningun caso; el punto
     * de lectura lo guarda quien consulta, con el {@code hasta} del lote.
     */
    @Nested
    @DisplayName("cambios del catalogo para otro servicio (solo lectura)")
    class CambiosParaOtroServicio {

        private final Instant desde = Instant.parse("2026-09-10T12:00:00Z");
        private final Instant t1 = Instant.parse("2026-09-10T13:00:00Z");
        private final Instant t2 = Instant.parse("2026-09-10T14:00:00Z");
        private final Instant t3 = Instant.parse("2026-09-10T15:00:00Z");

        @Test
        @DisplayName("sin desde es la linea base: ninguna alerta, hasta = ahora y completo, sin leer el historial")
        void lineaBaseSinDesde() {
            LoteDeAlertasCatalogo lote = servicio.consultarCambios(null, 50);

            assertEquals(List.of(), lote.alertas());
            assertEquals(AHORA, lote.hasta());
            assertTrue(lote.completo());
            verify(alertas, never()).buscarPrimerasImplementadasEntre(any(), any(), any());
            verify(alertas, never()).buscarImplementadasEntre(any(), any());
            verifyNoInteractions(consultas);
        }

        @Test
        @DisplayName("la linea base lleva la precision de MongoDB (milisegundos), no la del reloj")
        void lineaBaseEnMilisegundos() {
            AlertasCatalogoServicio conNanos = new AlertasCatalogoServicio(
                    alertas, consultas, productos,
                    Clock.fixed(Instant.parse("2026-09-11T15:30:00.123456789Z"), ZoneOffset.UTC));

            assertEquals(
                    Instant.parse("2026-09-11T15:30:00.123Z"),
                    conNanos.consultarCambios(null, 50).hasta());
        }

        @Test
        @DisplayName("con desde entrega (desde, ahora] de la mas antigua a la mas reciente y hasta = la ultima")
        void entregaElRangoEnOrden() {
            AlertaCatalogo primera = alerta("a", t1);
            AlertaCatalogo segunda = alerta("b", t2);
            when(alertas.buscarPrimerasImplementadasEntre(desde, AHORA, PageRequest.of(0, 51)))
                    .thenReturn(List.of(segunda, primera));

            LoteDeAlertasCatalogo lote = servicio.consultarCambios(desde, 50);

            assertEquals(List.of(primera, segunda), lote.alertas());
            assertEquals(t2, lote.hasta());
            assertTrue(lote.completo());
            verifyNoInteractions(consultas);
        }

        @Test
        @DisplayName("sin cambios en el rango, hasta = desde: un cambio registrado a la vez no se pierde")
        void sinCambiosHastaEsDesde() {
            when(alertas.buscarPrimerasImplementadasEntre(desde, AHORA, PageRequest.of(0, 51)))
                    .thenReturn(List.of());

            LoteDeAlertasCatalogo lote = servicio.consultarCambios(desde, 50);

            assertEquals(List.of(), lote.alertas());
            assertEquals(desde, lote.hasta());
            assertTrue(lote.completo());
            verifyNoInteractions(consultas);
        }

        @Test
        @DisplayName("con mas de limite corta en limite sin partir el grupo de la ultima marca y dice que no esta completo")
        void cortaSinPartirElGrupo() {
            AlertaCatalogo a = alerta("a", t1);
            AlertaCatalogo b = alerta("b", t2);
            AlertaCatalogo c = alerta("c", t2);
            when(alertas.buscarPrimerasImplementadasEntre(desde, AHORA, PageRequest.of(0, 3)))
                    .thenReturn(List.of(a, b, c));
            when(alertas.buscarImplementadasEntre(desde, t2)).thenReturn(List.of(c, a, b));
            when(alertas.buscarPrimerasImplementadasEntre(t2, AHORA, PageRequest.of(0, 1)))
                    .thenReturn(List.of(alerta("d", t3)));

            LoteDeAlertasCatalogo lote = servicio.consultarCambios(desde, 2);

            assertEquals(List.of(a, b, c), lote.alertas());
            assertEquals(t2, lote.hasta());
            assertFalse(lote.completo());
            verifyNoInteractions(consultas);
        }

        @Test
        @DisplayName("si el grupo de la ultima marca agota los cambios, el lote mas largo que limite si esta completo")
        void elGrupoQueAgotaLosCambiosEsCompleto() {
            AlertaCatalogo a = alerta("a", t1);
            AlertaCatalogo b = alerta("b", t2);
            AlertaCatalogo c = alerta("c", t2);
            AlertaCatalogo e = alerta("e", t2);
            when(alertas.buscarPrimerasImplementadasEntre(desde, AHORA, PageRequest.of(0, 3)))
                    .thenReturn(List.of(a, b, c));
            when(alertas.buscarImplementadasEntre(desde, t2)).thenReturn(List.of(a, b, c, e));
            when(alertas.buscarPrimerasImplementadasEntre(t2, AHORA, PageRequest.of(0, 1)))
                    .thenReturn(List.of());

            LoteDeAlertasCatalogo lote = servicio.consultarCambios(desde, 2);

            assertEquals(List.of(a, b, c, e), lote.alertas());
            assertEquals(t2, lote.hasta());
            assertTrue(lote.completo());
        }

        @Test
        @DisplayName("exactamente limite cambios: un solo viaje a la base y completo")
        void exactamenteLimite() {
            AlertaCatalogo a = alerta("a", t1);
            AlertaCatalogo b = alerta("b", t2);
            when(alertas.buscarPrimerasImplementadasEntre(desde, AHORA, PageRequest.of(0, 3)))
                    .thenReturn(List.of(a, b));

            LoteDeAlertasCatalogo lote = servicio.consultarCambios(desde, 2);

            assertEquals(List.of(a, b), lote.alertas());
            assertEquals(t2, lote.hasta());
            assertTrue(lote.completo());
            verify(alertas, never()).buscarImplementadasEntre(any(), any());
        }

        @Test
        @DisplayName("un limite fuera de 1..200 es un error de quien llama")
        void limiteFueraDeRango() {
            assertThrows(IllegalArgumentException.class, () -> servicio.consultarCambios(desde, 0));
            assertThrows(IllegalArgumentException.class, () -> servicio.consultarCambios(desde, 201));
            verifyNoInteractions(consultas);
        }

        private AlertaCatalogo alerta(String id, Instant implementadaEn) {
            return new AlertaCatalogo(
                    id,
                    "producto-1",
                    "Espada solar",
                    TipoCambioCatalogo.PRODUCTO_MODIFICADO,
                    "El producto Espada solar fue modificado.",
                    implementadaEn);
        }
    }

    private Producto producto(Instant modificadoEn) {
        return new Producto(
                "producto-1",
                "Espada solar",
                "productos/espada-solar.webp",
                "Arma del catalogo",
                TipoProducto.ARMA,
                100,
                500,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                40,
                new BigDecimal("12.5"),
                EstadoProducto.ACTIVO,
                1,
                modificadoEn.minusSeconds(3600),
                modificadoEn);
    }
}

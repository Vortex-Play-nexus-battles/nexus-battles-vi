package nexus.alertas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                .findByImplementadaEnAfterAndImplementadaEnLessThanEqualOrderByImplementadaEnAsc(
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
    @DisplayName("un primer ingreso consulta todo el historial aplicable")
    void primerIngresoParteDesdeElInicio() {
        when(consultas.findById("jugador-nuevo")).thenReturn(Optional.empty());
        when(alertas
                .findByImplementadaEnAfterAndImplementadaEnLessThanEqualOrderByImplementadaEnAsc(
                        Instant.EPOCH,
                        AHORA))
                .thenReturn(List.of());

        assertEquals(
                List.of(),
                servicio.consultarAlIniciarSesion("jugador-nuevo"));
    }

    @Test
    @DisplayName("rechaza un inicio sin identidad de jugador")
    void exigeIdentidad() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.consultarAlIniciarSesion(" "));
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

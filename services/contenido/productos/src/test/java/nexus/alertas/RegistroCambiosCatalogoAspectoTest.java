package nexus.alertas;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import jakarta.validation.Validator;
import nexus.api.SolicitudCrearProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.aplicacion.ModificarProductoServicio;
import nexus.aplicacion.ProductoMapper;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import nexus.productos.dominio.RepositorioDisponibilidadEnMemoria;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class RegistroCambiosCatalogoAspectoTest {

    private AlertasCatalogoServicio alertas;
    private RegistroCambiosCatalogoAspecto aspecto;

    @BeforeEach
    void preparar() {
        alertas = mock(AlertasCatalogoServicio.class);
        aspecto = new RegistroCambiosCatalogoAspecto(alertas);
    }

    @Test
    @DisplayName("la creacion exitosa genera una alerta de nuevo producto")
    void detectaCreacion() {
        Producto producto = producto();

        aspecto.despuesDeCrear(producto);

        verify(alertas).registrar(TipoCambioCatalogo.NUEVO_PRODUCTO, producto);
    }

    @Test
    @DisplayName("una falla al guardar la alerta no revierte el cambio de catalogo")
    void aislaFallaDePersistencia() {
        Producto producto = producto();
        doThrow(new IllegalStateException("Mongo no disponible"))
                .when(alertas)
                .registrar(TipoCambioCatalogo.NUEVO_PRODUCTO, producto);

        aspecto.despuesDeCrear(producto);

        verify(alertas).registrar(TipoCambioCatalogo.NUEVO_PRODUCTO, producto);
    }

    @Test
    @DisplayName("distingue una modificacion general de un cambio de balance")
    void clasificaModificaciones() {
        JoinPoint llamada = mock(JoinPoint.class);
        Producto producto = producto();
        when(llamada.getArgs()).thenReturn(new Object[] {
                "producto-1",
                new CambiosProducto("Nombre actualizado", null)
        });

        aspecto.despuesDeModificar(llamada, producto);

        verify(alertas).registrar(TipoCambioCatalogo.PRODUCTO_MODIFICADO, producto);

        clearInvocations(alertas);
        when(llamada.getArgs()).thenReturn(new Object[] {
                "producto-1",
                new CambiosProducto(null, 55)
        });

        aspecto.despuesDeModificar(llamada, producto);

        verify(alertas).registrar(TipoCambioCatalogo.CAMBIO_BALANCE, producto);
    }

    @Test
    @DisplayName("solo registra suspension cuando el estado cambia")
    void evitaSuspensionesDuplicadas() throws Throwable {
        CatalogoProductos catalogo = catalogoActivo();
        ProceedingJoinPoint llamada = llamada(catalogo, true);

        aspecto.alSuspender(llamada);

        verify(alertas).registrarEstado(
                TipoCambioCatalogo.PRODUCTO_SUSPENDIDO,
                "producto-1");

        clearInvocations(alertas);
        aspecto.alSuspender(llamada);

        verify(alertas, never()).registrarEstado(
                TipoCambioCatalogo.PRODUCTO_SUSPENDIDO,
                "producto-1");
    }

    @Test
    @DisplayName("la reactivacion efectiva genera su propia alerta")
    void detectaReactivacion() throws Throwable {
        CatalogoProductos catalogo = catalogoActivo();
        catalogo.suspender("producto-1");
        ProceedingJoinPoint llamada = llamada(catalogo, false);

        aspecto.alReactivar(llamada);

        verify(alertas).registrarEstado(
                TipoCambioCatalogo.PRODUCTO_REACTIVADO,
                "producto-1");
    }

    @Test
    @DisplayName("spring enlaza la suspension del catalogo con el registro de alertas")
    void integraElAspectoConElCatalogo() {
        CatalogoProductos catalogo = catalogoActivo();
        AspectJProxyFactory fabrica = new AspectJProxyFactory(catalogo);
        fabrica.addAspect(aspecto);
        CatalogoProductos proxy = fabrica.getProxy();

        proxy.suspender("producto-1");

        verify(alertas).registrarEstado(
                TipoCambioCatalogo.PRODUCTO_SUSPENDIDO,
                "producto-1");
    }

    @Test
    @DisplayName("spring enlaza la modificacion real de producto sin cambiar su servicio")
    void integraElAspectoConLaModificacion() {
        Producto producto = producto();
        ProductoRepository productos = mock(ProductoRepository.class);
        RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
        ProductoMapper mapper = mock(ProductoMapper.class);
        Validator validator = mock(Validator.class);
        SolicitudCrearProducto fusionada = mock(SolicitudCrearProducto.class);
        SolicitudModificarProducto cambios = cambioBalance();
        when(productos.findById("producto-1")).thenReturn(Optional.of(producto));
        when(mapper.fusionar(producto, cambios)).thenReturn(fusionada);
        when(validator.validate(fusionada)).thenReturn(Set.of());
        when(mapper.actualizar(any(), any(), any(), any())).thenReturn(producto);
        when(productos.save(producto)).thenReturn(producto);
        ModificarProductoServicio servicio = new ModificarProductoServicio(
                productos,
                respaldos,
                mapper,
                validator);
        AspectJProxyFactory fabrica = new AspectJProxyFactory(servicio);
        fabrica.addAspect(aspecto);
        ModificarProductoServicio proxy = fabrica.getProxy();

        proxy.modificar("producto-1", cambios, "administrador-1");

        verify(alertas).registrar(TipoCambioCatalogo.CAMBIO_BALANCE, producto);
    }

    private ProceedingJoinPoint llamada(
            CatalogoProductos catalogo,
            boolean suspender) throws Throwable {
        ProceedingJoinPoint llamada = mock(ProceedingJoinPoint.class);
        when(llamada.getTarget()).thenReturn(catalogo);
        when(llamada.getArgs()).thenReturn(new Object[] {"producto-1"});
        when(llamada.proceed()).thenAnswer(invocacion -> suspender
                ? catalogo.suspender("producto-1")
                : catalogo.reactivar("producto-1"));
        return llamada;
    }

    private CatalogoProductos catalogoActivo() {
        CatalogoProductos catalogo = new CatalogoProductos(
                new RepositorioDisponibilidadEnMemoria());
        catalogo.registrar(DisponibilidadProducto.nueva(
                "producto-1",
                10,
                EstadoProducto.ACTIVO));
        return catalogo;
    }

    private Producto producto() {
        Instant ahora = Instant.parse("2026-09-11T15:30:00Z");
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
                ahora,
                ahora);
    }

    private SolicitudModificarProducto cambioBalance() {
        return new SolicitudModificarProducto(
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                55,
                null);
    }

    private record CambiosProducto(String nombre, Integer poderDeAtaque) {
    }
}

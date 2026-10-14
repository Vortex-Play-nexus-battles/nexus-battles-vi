package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import nexus.api.SolicitudCrearProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.auditoria.RegistroDeOperaciones;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * SA-18 — cada cambio de producto, una vez aplicado, pasa al registro de
 * operaciones administrativas con su entrada del historial; uno que falla no
 * pasa (no se registra lo que no ocurrió).
 */
@DisplayName("Los cambios de producto llegan al registro de operaciones (SA-18)")
class OperacionesEnLosServiciosDelCatalogoTest {

        private static final String ADMIN = "5a0c3e1e-7b8d-4f60-9d8e-2c4b1a6f9e10";
        private static final Instant FECHA = Instant.parse("2026-10-05T10:00:00Z");

        private final ProductoRepository productos = mock(ProductoRepository.class);
        private final RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
        private final RegistroDeOperaciones operaciones = mock(RegistroDeOperaciones.class);

        private static Producto arma(EstadoProducto estado, int version) {
                return new Producto(
                                "550e8400-e29b-41d4-a716-446655440000", "Espada", "productos/espada.webp",
                                "Arma", TipoProducto.ARMA, 10, 500, null, false, null, null, null, null,
                                null, null, null, null, null, null, null, 20, new BigDecimal("10"), estado,
                                version, FECHA, FECHA, null, null, null, ADMIN, null, List.of());
        }

        private static SolicitudCrearProducto solicitudDeArma() {
                return new SolicitudCrearProducto(
                                "Espada solar", "productos/espada-solar.webp", "Arma de prueba", TipoProducto.ARMA,
                                100, 500, null, false, null, null, null, null, null, null, null, null, null, null,
                                null, 40, new BigDecimal("12.5"));
        }

        private RespaldoProducto respaldoGuardado() {
                ArgumentCaptor<RespaldoProducto> captor = ArgumentCaptor.forClass(RespaldoProducto.class);
                verify(respaldos).save(captor.capture());
                return captor.getValue();
        }

        @Nested
        @DisplayName("modificar")
        class Modificar {

                private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
                private final ModificarProductoServicio servicio = new ModificarProductoServicio(
                                productos, respaldos, Mappers.getMapper(ProductoMapper.class), validator, operaciones);

                private SolicitudModificarProducto conNombre(String nombre) {
                        return new SolicitudModificarProducto(nombre, null, null, null, null, null, null, null, null,
                                        null, null, null, null, null, null, null, null, null, null, null, null);
                }

                @Test
                @DisplayName("la modificación aplicada se registra con su entrada del historial")
                void aplicada() {
                        Producto existente = arma(EstadoProducto.ACTIVO, 3);
                        when(productos.findById(existente.id())).thenReturn(Optional.of(existente));
                        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0, Producto.class));

                        servicio.modificar(existente.id(), conNombre("Espada solar"), ADMIN);

                        RespaldoProducto respaldo = respaldoGuardado();
                        assertEquals(TipoCambioProducto.MODIFICACION, respaldo.tipoCambio());
                        verify(operaciones).deProducto(respaldo);
                }

                @Test
                @DisplayName("la que choca con otra escritura no se registra")
                void conflicto() {
                        Producto existente = arma(EstadoProducto.ACTIVO, 3);
                        when(productos.findById(existente.id())).thenReturn(Optional.of(existente));
                        when(productos.save(any(Producto.class)))
                                        .thenThrow(new OptimisticLockingFailureException("otro escribió antes"));

                        assertThrows(OptimisticLockingFailureException.class,
                                        () -> servicio.modificar(existente.id(), conNombre("Espada solar"), ADMIN));

                        verifyNoInteractions(operaciones);
                }
        }

        @Nested
        @DisplayName("crear")
        class Crear {

                private final CrearProductoServicio servicio = new CrearProductoServicio(
                                productos, Mappers.getMapper(ProductoMapper.class), respaldos, operaciones);

                @Test
                @DisplayName("el alta que no se pudo insertar no se registra")
                void insercionFallida() {
                        when(productos.insert(any(Producto.class)))
                                        .thenThrow(new IllegalStateException("MongoDB no responde"));

                        assertThrows(IllegalStateException.class,
                                        () -> servicio.crear(solicitudDeArma(), ADMIN));

                        verifyNoInteractions(operaciones);
                }

                @Test
                @DisplayName("el alta insertada se registra con su entrada de creación")
                void insertada() {
                        when(productos.insert(any(Producto.class))).thenAnswer(i -> i.getArgument(0, Producto.class));

                        servicio.crear(solicitudDeArma(), ADMIN);

                        RespaldoProducto respaldo = respaldoGuardado();
                        assertEquals(TipoCambioProducto.CREACION, respaldo.tipoCambio());
                        verify(operaciones).deProducto(respaldo);
                }
        }

        @Nested
        @DisplayName("suspender, reactivar y revertir")
        class Estado {

                private final CatalogoProductos catalogo = mock(CatalogoProductos.class);

                @Test
                @DisplayName("una suspensión aplicada se registra; una que el catálogo rechaza, no")
                void suspension() {
                        Producto activo = arma(EstadoProducto.ACTIVO, 3);
                        when(productos.findById(activo.id())).thenReturn(Optional.of(activo));
                        when(catalogo.suspender(activo.id()))
                                        .thenReturn(DisponibilidadProducto.desde(arma(EstadoProducto.SUSPENDIDO, 4),
                                                        EstadoProducto.ACTIVO));
                        DisponibilidadDelCatalogoServicio servicio =
                                        new DisponibilidadDelCatalogoServicio(catalogo, productos, respaldos, operaciones);

                        servicio.suspender(activo.id(), ADMIN, "Balance roto");

                        RespaldoProducto respaldo = respaldoGuardado();
                        assertEquals("Balance roto", respaldo.motivo());
                        verify(operaciones).deProducto(respaldo);
                }

                @Test
                @DisplayName("si el catálogo falla al suspender, no hay operación que registrar")
                void suspensionFallida() {
                        Producto activo = arma(EstadoProducto.ACTIVO, 3);
                        when(productos.findById(activo.id())).thenReturn(Optional.of(activo));
                        when(catalogo.suspender(activo.id())).thenThrow(new IllegalStateException("caído"));
                        DisponibilidadDelCatalogoServicio servicio =
                                        new DisponibilidadDelCatalogoServicio(catalogo, productos, respaldos, operaciones);

                        assertThrows(IllegalStateException.class, () -> servicio.suspender(activo.id(), ADMIN, "x"));

                        verifyNoInteractions(operaciones);
                }

                @Test
                @DisplayName("suspender lo ya suspendido no escribe nada ni lo registra")
                void suspensionRepetida() {
                        Producto suspendido = arma(EstadoProducto.SUSPENDIDO, 3);
                        when(productos.findById(suspendido.id())).thenReturn(Optional.of(suspendido));
                        when(catalogo.suspender(suspendido.id()))
                                        .thenReturn(DisponibilidadProducto.desde(suspendido, EstadoProducto.ACTIVO));
                        DisponibilidadDelCatalogoServicio servicio =
                                        new DisponibilidadDelCatalogoServicio(catalogo, productos, respaldos, operaciones);

                        servicio.suspender(suspendido.id(), ADMIN, "otra vez");

                        verifyNoInteractions(operaciones);
                }

                @Test
                @DisplayName("una reversión aplicada se registra con su entrada; una que falla al guardar, no")
                void reversion() {
                        Producto anterior = arma(EstadoProducto.ACTIVO, 2);
                        Producto actual = arma(EstadoProducto.ACTIVO, 3);
                        RespaldoProducto objetivo = new RespaldoProducto("r-1", actual.id(), anterior, actual, FECHA,
                                        ADMIN, TipoCambioProducto.MODIFICACION, null);
                        when(productos.findById(actual.id())).thenReturn(Optional.of(actual));
                        when(respaldos.findById("r-1")).thenReturn(Optional.of(objetivo));
                        when(productos.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0, Producto.class));
                        RevertirProductoServicio servicio = new RevertirProductoServicio(productos, respaldos, operaciones);

                        servicio.revertir(actual.id(), "r-1", ADMIN, "Precio mal puesto");

                        RespaldoProducto respaldo = respaldoGuardado();
                        assertEquals(TipoCambioProducto.REVERSION, respaldo.tipoCambio());
                        verify(operaciones).deProducto(respaldo);
                }

                @Test
                @DisplayName("una reversión que no se pudo guardar no se registra")
                void reversionFallida() {
                        Producto anterior = arma(EstadoProducto.ACTIVO, 2);
                        Producto actual = arma(EstadoProducto.ACTIVO, 3);
                        when(productos.findById(actual.id())).thenReturn(Optional.of(actual));
                        when(respaldos.findById("r-1")).thenReturn(Optional.of(new RespaldoProducto("r-1",
                                        actual.id(), anterior, actual, FECHA, ADMIN, TipoCambioProducto.MODIFICACION,
                                        null)));
                        when(productos.save(any(Producto.class))).thenThrow(new IllegalStateException("caído"));
                        RevertirProductoServicio servicio = new RevertirProductoServicio(productos, respaldos, operaciones);

                        assertThrows(IllegalStateException.class,
                                        () -> servicio.revertir(actual.id(), "r-1", ADMIN, "x"));

                        verifyNoInteractions(operaciones);
                }
        }
}

package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import nexus.api.SolicitudModificarProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.ModificacionProductoInvalidaException;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ModificarProductoServicioTest {

        // Validator real (Jakarta Bean Validation puro, sin contexto de Spring):
        // el punto de este servicio es que la fusion se valide con las MISMAS
        // reglas que la creacion, asi que se prueba con el validador real, no
        // con uno simulado que solo confirmaria que llame a un metodo.
        private final Validator validator =
                Validation.buildDefaultValidatorFactory().getValidator();
        private final ProductoMapper mapper = Mappers.getMapper(ProductoMapper.class);

        @Test
        @DisplayName("modifica un campo, guarda respaldo del estado anterior y persiste el actualizado")
        void modificarProductoExistoso() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto existente = productoArma();
                when(repositorio.findById(existente.id())).thenReturn(Optional.of(existente));
                when(repositorio.save(any(Producto.class)))
                        .thenAnswer(invocacion -> invocacion.getArgument(0, Producto.class));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                SolicitudModificarProducto cambios = solicitudConNombre("Espada solar+1");

                Producto resultado = servicio.modificar(existente.id(), cambios);

                assertEquals("Espada solar+1", resultado.nombre());
                assertEquals(existente.id(), resultado.id());
                assertEquals(existente.version() + 1, resultado.version());

                verify(respaldoRepositorio).save(any(RespaldoProducto.class));
                verify(repositorio).save(any(Producto.class));
        }

        @Test
        @DisplayName("el respaldo guardado contiene el estado ANTERIOR completo, no el nuevo")
        void respaldoContieneElEstadoAnterior() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto existente = productoArma();
                when(repositorio.findById(existente.id())).thenReturn(Optional.of(existente));
                when(repositorio.save(any(Producto.class)))
                        .thenAnswer(invocacion -> invocacion.getArgument(0, Producto.class));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1"));

                org.mockito.ArgumentCaptor<RespaldoProducto> captor =
                        org.mockito.ArgumentCaptor.forClass(RespaldoProducto.class);
                verify(respaldoRepositorio).save(captor.capture());

                RespaldoProducto respaldo = captor.getValue();
                assertEquals(existente.id(), respaldo.productoId());
                assertEquals(existente, respaldo.estadoAnterior());
                assertEquals("Espada solar", respaldo.estadoAnterior().nombre());
        }

        @Test
        @DisplayName("defensa sobre un producto HEROE: la fusion se rechaza y no se guarda nada")
        void rechazaDefensaSobreHeroe() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto existente = productoHeroe();
                when(repositorio.findById(existente.id())).thenReturn(Optional.of(existente));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                SolicitudModificarProducto cambios = new SolicitudModificarProducto(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, 50, null, null,
                        null, null);

                assertThrows(
                        ModificacionProductoInvalidaException.class,
                        () -> servicio.modificar(existente.id(), cambios));

                verify(respaldoRepositorio, never()).save(any(RespaldoProducto.class));
                verify(repositorio, never()).save(any(Producto.class));
        }

        @Test
        @DisplayName("producto inexistente lanza ProductoNoEncontradoException y no toca ningun repositorio de escritura")
        void productoInexistente() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                String id = UUID.randomUUID().toString();
                when(repositorio.findById(id)).thenReturn(Optional.empty());

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                assertThrows(
                        ProductoNoEncontradoException.class,
                        () -> servicio.modificar(id, solicitudConNombre("Nuevo nombre")));

                verify(respaldoRepositorio, never()).save(any(RespaldoProducto.class));
        }

        @Test
        @DisplayName("si falla el guardado del respaldo, el producto original nunca se toca")
        void fallaElRespaldo_noTocaElProducto() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto existente = productoArma();
                when(repositorio.findById(existente.id())).thenReturn(Optional.of(existente));
                when(respaldoRepositorio.save(any(RespaldoProducto.class)))
                        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Mongo caido"));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                assertThrows(
                        org.springframework.dao.DataAccessResourceFailureException.class,
                        () -> servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1")));

                verify(repositorio, never()).save(any(Producto.class));
        }

        @Test
        @DisplayName("si falla el guardado final, se revierte el respaldo recien creado y se propaga el error")
        void fallaElGuardadoFinal_revierteElRespaldo() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto existente = productoArma();
                when(repositorio.findById(existente.id())).thenReturn(Optional.of(existente));
                when(repositorio.save(any(Producto.class)))
                        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Mongo caido"));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                assertThrows(
                        org.springframework.dao.DataAccessResourceFailureException.class,
                        () -> servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1")));

                verify(respaldoRepositorio, times(1)).save(any(RespaldoProducto.class));
                verify(respaldoRepositorio, times(1)).deleteById(org.mockito.ArgumentMatchers.anyString());
        }

        private static SolicitudModificarProducto solicitudConNombre(String nombre) {
                return new SolicitudModificarProducto(
                        nombre, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, null);
        }

        private static Producto productoArma() {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        UUID.randomUUID().toString(),            // id
                        "Espada solar",                          // nombre
                        "productos/espada-solar.webp",           // imagen
                        "Arma de prueba",                        // descripcion
                        TipoProducto.ARMA,                       // tipo
                        100,                                     // tiraje
                        500,                                     // precioCreditos
                        null,                                    // precioMonedaReal
                        false,                                   // premium
                        null,                                    // prototipo
                        null,                                    // heroe
                        null,                                    // costoPoder
                        null,                                    // multiplicadorNivel
                        null,                                    // turnosCarga
                        null,                                    // turnosRecarga
                        null,                                    // efectoGeneral
                        null,                                    // efectoPotenciado
                        null,                                    // defensa
                        null,                                    // parte
                        null,                                    // efecto
                        40,                                      // poderDeAtaque
                        new BigDecimal("12.5"),                  // tasaDeCaida
                        EstadoProducto.ACTIVO,                   // estado
                        1,                                       // version
                        ahora,                                   // creadoEn
                        ahora);                                  // modificadoEn
        }

        private static Producto productoHeroe() {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        UUID.randomUUID().toString(),            // id
                        "Heroe de prueba",                       // nombre
                        "productos/heroe-prueba.webp",           // imagen
                        "Heroe de prueba",                       // descripcion
                        TipoProducto.HEROE,                      // tipo
                        -1,                                      // tiraje
                        1000,                                    // precioCreditos
                        null,                                    // precioMonedaReal
                        false,                                   // premium
                        "Guerrero Tanque",                       // prototipo
                        null,                                    // heroe
                        null,                                    // costoPoder
                        null,                                    // multiplicadorNivel
                        null,                                    // turnosCarga
                        null,                                    // turnosRecarga
                        null,                                    // efectoGeneral
                        null,                                    // efectoPotenciado
                        null,                                    // defensa
                        null,                                    // parte
                        null,                                    // efecto
                        null,                                    // poderDeAtaque
                        null,                                    // tasaDeCaida
                        EstadoProducto.ACTIVO,                   // estado
                        1,                                       // version
                        ahora,                                   // creadoEn
                        ahora);                                  // modificadoEn
        }
}

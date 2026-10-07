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
                        .thenAnswer(invocacion -> guardadoVersionado(invocacion.getArgument(0, Producto.class)));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                SolicitudModificarProducto cambios = solicitudConNombre("Espada solar+1");

                Producto resultado = servicio.modificar(existente.id(), cambios, AUTOR);

                assertEquals("Espada solar+1", resultado.nombre());
                assertEquals(existente.id(), resultado.id());
                assertEquals(existente.version() + 1, resultado.version());
                // B4: quien edita queda en el producto, y eso le dice a la
                // semilla versionada que ya no lo pone al dia.
                assertEquals(AUTOR, resultado.modificadoPor());

                verify(respaldoRepositorio).save(any(RespaldoProducto.class));
                // Se guarda con la version LEIDA: la sube el guardado versionado.
                org.mockito.ArgumentCaptor<Producto> guardado =
                        org.mockito.ArgumentCaptor.forClass(Producto.class);
                verify(repositorio).save(guardado.capture());
                assertEquals(existente.version(), guardado.getValue().version());
        }

        @Test
        @DisplayName("B4: si otro escribio entre la lectura y el guardado, el conflicto sale y el respaldo se revierte")
        void conflictoDeVersionRevierteElRespaldo() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto existente = productoArma();
                when(repositorio.findById(existente.id())).thenReturn(Optional.of(existente));
                when(repositorio.save(any(Producto.class)))
                        .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("version 1 ya no existe"));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                assertThrows(
                        org.springframework.dao.OptimisticLockingFailureException.class,
                        () -> servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1"), AUTOR));

                verify(respaldoRepositorio).deleteById(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("B4: un producto anterior a @Version (version 0) se normaliza y se relee antes de editarlo")
        void productoSinVersionSeNormalizaAntes() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);

                Producto conVersion = productoArma();
                Producto sinVersion = conVersion(conVersion, 0);
                when(repositorio.findById(conVersion.id()))
                        .thenReturn(Optional.of(sinVersion), Optional.of(conVersion));
                when(repositorio.save(any(Producto.class)))
                        .thenAnswer(invocacion -> guardadoVersionado(invocacion.getArgument(0, Producto.class)));

                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                Producto resultado = servicio.modificar(
                        conVersion.id(), solicitudConNombre("Espada solar+1"), AUTOR);

                verify(repositorio).normalizarVersion(conVersion.id());
                assertEquals(2, resultado.version());
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

                servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1"), AUTOR);

                org.mockito.ArgumentCaptor<RespaldoProducto> captor =
                        org.mockito.ArgumentCaptor.forClass(RespaldoProducto.class);
                verify(respaldoRepositorio).save(captor.capture());

                RespaldoProducto respaldo = captor.getValue();
                assertEquals(existente.id(), respaldo.productoId());
                assertEquals(existente, respaldo.estadoAnterior());
                assertEquals("Espada solar", respaldo.estadoAnterior().nombre());
                // B4: el respaldo dice quien hizo el cambio.
                assertEquals(AUTOR, respaldo.autor());
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
                        () -> servicio.modificar(existente.id(), cambios, AUTOR));

                verify(respaldoRepositorio, never()).save(any(RespaldoProducto.class));
                verify(repositorio, never()).save(any(Producto.class));
        }

        // RG-085 / RF-MOT-36: la fusion de una epica se valida con la regla de que
        // la unica fuente de epicas es derrotar al Master: sin precio y sin premium.
        @Test
        @DisplayName("RG-085: ponerle precio en creditos a una epica se rechaza y no se guarda ni respalda nada")
        void rechazaPrecioEnCreditosSobreEpica() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(repositorio.findById(epica.id())).thenReturn(Optional.of(epica));
                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                ModificacionProductoInvalidaException error = assertThrows(
                        ModificacionProductoInvalidaException.class,
                        () -> servicio.modificar(epica.id(), cambiosDePrecio(500, null, null), AUTOR));

                org.junit.jupiter.api.Assertions.assertTrue(
                        error.getMessage().contains("derrotando al M\u00e1ster"), error.getMessage());
                verify(respaldoRepositorio, never()).save(any(RespaldoProducto.class));
                verify(repositorio, never()).save(any(Producto.class));
        }

        @Test
        @DisplayName("RG-085: ponerle precio en moneda real a una epica se rechaza")
        void rechazaPrecioEnMonedaRealSobreEpica() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(repositorio.findById(epica.id())).thenReturn(Optional.of(epica));
                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                assertThrows(
                        ModificacionProductoInvalidaException.class,
                        () -> servicio.modificar(epica.id(), cambiosDePrecio(null, new BigDecimal("10000"), null), AUTOR));

                verify(repositorio, never()).save(any(Producto.class));
        }

        @Test
        @DisplayName("RG-085: hacer premium a una epica se rechaza")
        void rechazaPremiumSobreEpica() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(repositorio.findById(epica.id())).thenReturn(Optional.of(epica));
                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                assertThrows(
                        ModificacionProductoInvalidaException.class,
                        () -> servicio.modificar(epica.id(), cambiosDePrecio(null, null, true), AUTOR));

                verify(repositorio, never()).save(any(Producto.class));
        }

        @Test
        @DisplayName("RG-085: una epica sin precio se edita sin que la regla estorbe")
        void editaUnaEpicaSinPrecio() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(repositorio.findById(epica.id())).thenReturn(Optional.of(epica));
                when(repositorio.save(any(Producto.class)))
                        .thenAnswer(invocacion -> guardadoVersionado(invocacion.getArgument(0, Producto.class)));
                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                Producto resultado = servicio.modificar(epica.id(), solicitudConNombre("Epica renombrada"), AUTOR);

                assertEquals("Epica renombrada", resultado.nombre());
                verify(respaldoRepositorio).save(any(RespaldoProducto.class));
        }

        @Test
        @DisplayName("RG-085: una epica que quedo con precio (editada a mano antes de la regla) se puede dejar en cero")
        void unaEpicaConPrecioSePuedeDejarEnCero() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldoRepositorio = mock(RespaldoProductoRepository.class);
                Producto conPrecio = productoEpica(500, new BigDecimal("10000"));
                when(repositorio.findById(conPrecio.id())).thenReturn(Optional.of(conPrecio));
                when(repositorio.save(any(Producto.class)))
                        .thenAnswer(invocacion -> guardadoVersionado(invocacion.getArgument(0, Producto.class)));
                ModificarProductoServicio servicio = new ModificarProductoServicio(
                        repositorio, respaldoRepositorio, mapper, validator);

                Producto resultado = servicio.modificar(
                        conPrecio.id(), cambiosDePrecio(0, BigDecimal.ZERO, false), AUTOR);

                assertEquals(0, resultado.precioCreditos());
                assertEquals(0, resultado.precioMonedaReal().signum());
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
                        () -> servicio.modificar(id, solicitudConNombre("Nuevo nombre"), AUTOR));

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
                        () -> servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1"), AUTOR));

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
                        () -> servicio.modificar(existente.id(), solicitudConNombre("Espada solar+1"), AUTOR));

                verify(respaldoRepositorio, times(1)).save(any(RespaldoProducto.class));
                verify(respaldoRepositorio, times(1)).deleteById(org.mockito.ArgumentMatchers.anyString());
        }

        /** El uid del administrador que edita (claim uid del token). */
        private static final String AUTOR = "5a0c3e1e-7b8d-4f60-9d8e-2c4b1a6f9e10";

        /** Lo que hace el guardado real con @Version: devuelve el producto con la version siguiente. */
        private static Producto guardadoVersionado(Producto producto) {
                return conVersion(producto, producto.version() + 1);
        }

        private static Producto conVersion(Producto p, int version) {
                return new Producto(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                        p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.prototipo(), p.heroe(),
                        p.costoPoder(), p.multiplicadorNivel(), p.turnosCarga(), p.turnosRecarga(),
                        p.efectoGeneral(), p.efectoPotenciado(), p.defensa(), p.parte(), p.efecto(),
                        p.poderDeAtaque(), p.tasaDeCaida(), p.estado(), version, p.creadoEn(),
                        p.modificadoEn(), p.promocion(), p.origen(), p.semillaVersion(), p.modificadoPor(),
                        p.estadoAnteriorSuspension(), p.reservasRecientes());
        }

        private static SolicitudModificarProducto solicitudConNombre(String nombre) {
                return new SolicitudModificarProducto(
                        nombre, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, null);
        }

        private static SolicitudModificarProducto cambiosDePrecio(
                        Integer precioCreditos, BigDecimal precioMonedaReal, Boolean premium) {
                return new SolicitudModificarProducto(
                        null, null, null, null, precioCreditos, precioMonedaReal, premium, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, null);
        }

        private static Producto productoEpica(int precioCreditos, BigDecimal precioMonedaReal) {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        UUID.randomUUID().toString(),            // id
                        "Epica de prueba",                       // nombre
                        "productos/epica-prueba.webp",           // imagen
                        "Epica de prueba",                       // descripcion
                        TipoProducto.EPICA,                      // tipo
                        -1,                                      // tiraje
                        precioCreditos,                          // precioCreditos
                        precioMonedaReal,                        // precioMonedaReal
                        false,                                   // premium
                        null,                                    // prototipo
                        "550e8400-e29b-41d4-a716-446655440000",  // heroe
                        null,                                    // costoPoder
                        null,                                    // multiplicadorNivel
                        null,                                    // turnosCarga
                        2,                                       // turnosRecarga
                        "Aumenta el poder de todo el equipo",    // efectoGeneral
                        "Duplica el poder durante dos turnos",   // efectoPotenciado
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

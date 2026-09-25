package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Instant;

import nexus.api.SolicitudCrearProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ProductoMapperFusionTest {

        private final ProductoMapper mapper = Mappers.getMapper(ProductoMapper.class);

        @Test
        @DisplayName("fusionar conserva los campos que el cambio no toca")
        void fusionarConservaCamposNoModificados() {
                Producto existente = productoArma();

                SolicitudModificarProducto cambios = new SolicitudModificarProducto(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        null, new BigDecimal("99.9"));

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, cambios);

                assertEquals(existente.nombre(), fusionada.nombre());
                assertEquals(existente.imagen(), fusionada.imagen());
                assertEquals(existente.descripcion(), fusionada.descripcion());
                assertEquals(existente.tipo(), fusionada.tipo());
                assertEquals(existente.tiraje(), fusionada.tiraje());
                assertEquals(existente.precioCreditos(), fusionada.precioCreditos());
                assertEquals(existente.premium(), fusionada.premium());
                assertEquals(existente.poderDeAtaque(), fusionada.poderDeAtaque());
                assertEquals(new BigDecimal("99.9"), fusionada.tasaDeCaida());
        }

        @Test
        @DisplayName("fusionar sobrescribe solo los campos presentes en el cambio")
        void fusionarSobrescribeCamposModificados() {
                Producto existente = productoArma();

                SolicitudModificarProducto cambios = new SolicitudModificarProducto(
                        "Espada solar+1", null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        99, null);

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, cambios);

                assertEquals("Espada solar+1", fusionada.nombre());
                assertEquals(99, fusionada.poderDeAtaque());
                assertEquals(existente.imagen(), fusionada.imagen());
                assertEquals(existente.tasaDeCaida(), fusionada.tasaDeCaida());
        }

        @Test
        @DisplayName("fusionar siempre toma el tipo del producto existente, nunca del cambio")
        void fusionarConservaElTipoDelExistente() {
                Producto existente = productoHeroe();

                SolicitudModificarProducto cambios = new SolicitudModificarProducto(
                        null, null, null, null, null, null, null,
                        "Mago Hielo", null, null, null, null, null, null, null,
                        null, null, null, null, null);

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, cambios);

                assertEquals(TipoProducto.HEROE, fusionada.tipo());
                assertEquals("Mago Hielo", fusionada.prototipo());
        }

        @Test
        @DisplayName("fusionar NO filtra por tipo: defensa sobre HEROE queda en el resultado sin rechazarse aqui")
        void fusionarNoFiltraPorTipo() {
                Producto existente = productoHeroe();

                SolicitudModificarProducto cambios = new SolicitudModificarProducto(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, 50, null, null,
                        null, null);

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, cambios);

                assertEquals(TipoProducto.HEROE, fusionada.tipo());
                assertNotNull(fusionada.defensa());
                assertEquals(50, fusionada.defensa());
                // La combinacion resultante es invalida para HEROE, pero fusionar()
                // por si solo no lo evita - eso depende de validar el resultado
                // (ver ModificarProductoServicioTest, que ejercita ese caso completo).
        }

        @Test
        @DisplayName("actualizar preserva id, estado y creadoEn del existente y suma uno a version")
        void actualizarPreservaCamposDeSistema() {
                Producto existente = productoArma();
                Instant ahora = Instant.now();

                SolicitudCrearProducto fusionada = mapper.fusionar(existente, new SolicitudModificarProducto(
                        "Espada solar+1", null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        null, null));

                Producto actualizado = mapper.actualizar(fusionada, existente, ahora);

                assertEquals(existente.id(), actualizado.id());
                assertEquals(existente.estado(), actualizado.estado());
                assertEquals(existente.creadoEn(), actualizado.creadoEn());
                assertEquals(existente.version() + 1, actualizado.version());
                assertEquals(ahora, actualizado.modificadoEn());
                assertEquals("Espada solar+1", actualizado.nombre());
        }

        private static Producto productoArma() {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        "550e8400-e29b-41d4-a716-446655440000", // id
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
                        "660e8400-e29b-41d4-a716-446655440000", // id
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

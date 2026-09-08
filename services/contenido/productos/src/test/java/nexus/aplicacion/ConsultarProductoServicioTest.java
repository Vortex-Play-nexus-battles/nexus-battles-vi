package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultarProductoServicioTest {

        @Test
        @DisplayName("devuelve el producto cuando el repositorio lo encuentra")
        void devuelveProductoExistente() {
                ProductoRepository repositorio = mock(ProductoRepository.class);

                String id = UUID.randomUUID().toString();
                Instant ahora = Instant.now();
                Producto producto = new Producto(
                        id,
                        "Espada solar",
                        "productos/espada-solar.webp",
                        "Arma creada para probar la consulta",
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

                when(repositorio.findById(id)).thenReturn(Optional.of(producto));

                ConsultarProductoServicio servicio = new ConsultarProductoServicio(repositorio);

                Producto encontrado = servicio.consultar(id);

                assertEquals(producto, encontrado);
        }

        @Test
        @DisplayName("lanza ProductoNoEncontradoException cuando el id no existe")
        void lanzaExcepcionCuandoNoExiste() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                String id = UUID.randomUUID().toString();

                when(repositorio.findById(id)).thenReturn(Optional.empty());

                ConsultarProductoServicio servicio = new ConsultarProductoServicio(repositorio);

                assertThrows(
                        ProductoNoEncontradoException.class,
                        () -> servicio.consultar(id));
        }
}

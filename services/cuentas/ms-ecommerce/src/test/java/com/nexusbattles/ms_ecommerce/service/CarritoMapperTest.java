package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Carrito → DTO: la forma que lee el frontend, sin la entidad")
class CarritoMapperTest {

    private final CarritoMapper mapper = new CarritoMapper();

    private static Carrito carrito() {
        Carrito carrito = new Carrito();
        carrito.setId(1L);
        carrito.setUsuarioId("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
        carrito.setItems(new ArrayList<>());
        return carrito;
    }

    private static ItemCarrito linea(Long id, String referencia, String nombre, String moneda, String precio, int cantidad) {
        ItemCarrito linea = new ItemCarrito();
        linea.setId(id);
        linea.setProductoRef(referencia);
        linea.setProductoNombre(nombre);
        linea.setMoneda(moneda);
        linea.setCantidad(cantidad);
        linea.setPrecioUnitario(precio == null ? null : new BigDecimal(precio));
        linea.calcularSubtotal();
        return linea;
    }

    @Test
    @DisplayName("copia cada campo del carrito y de sus lineas")
    void copiaLosCampos() {
        Carrito carrito = carrito();
        carrito.getItems().add(linea(10L, "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a", "Espada", "COP", "6000.00", 2));
        carrito.recalcularTotal();

        CarritoDto dto = mapper.aDto(carrito);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.usuarioId()).isEqualTo("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
        assertThat(dto.total()).isEqualByComparingTo("12000.00");
        assertThat(dto.moneda()).isEqualTo("COP");
        assertThat(dto.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(10L);
            assertThat(item.producto().id()).isEqualTo("5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a");
            assertThat(item.producto().nombre()).isEqualTo("Espada");
            assertThat(item.producto().moneda()).isEqualTo("COP");
            assertThat(item.cantidad()).isEqualTo(2);
            assertThat(item.precioUnitario()).isEqualByComparingTo("6000.00");
            assertThat(item.subtotal()).isEqualByComparingTo("12000.00");
        });
    }

    @Test
    @DisplayName("un carrito vacio no tiene moneda")
    void vacioSinMoneda() {
        CarritoDto dto = mapper.aDto(carrito());

        assertThat(dto.items()).isEmpty();
        assertThat(dto.moneda()).isNull();
        assertThat(dto.total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("la moneda del carrito es la de la primera linea que la declara; una legada sin precio no cuenta")
    void monedaDeLasLineas() {
        Carrito carrito = carrito();
        carrito.getItems().add(linea(5L, null, "Pocion legada", null, null, 1));
        carrito.getItems().add(linea(10L, "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a", "Espada", "COP", "6000", 1));

        assertThat(mapper.aDto(carrito).moneda()).isEqualTo("COP");
    }
}

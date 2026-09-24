package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.Producto;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private CarritoService carritoService;

    @Test
    void agregarProducto_nuevoItem_debeCrearYCalcularSubtotal() {
        // Arrange
        String usuarioId = "usr_123";
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setPrecioBaseCop(new BigDecimal("10000"));

        Carrito carritoBase = new Carrito();
        carritoBase.setUsuarioId(usuarioId);
        carritoBase.setItems(new ArrayList<>()); // Inicializamos la lista vacía

        AgregarItemRequest request = new AgregarItemRequest();
        request.setProductoId(1L);
        request.setCantidad(2);

        when(carritoRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(carritoBase));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        // Simulamos que al guardar, retorna el mismo carrito que se le pasó
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        Carrito resultado = carritoService.agregarProducto(usuarioId, request);

        // Assert
        assertEquals(1, resultado.getItems().size());
        assertEquals(2, resultado.getItems().get(0).getCantidad());
        assertEquals(new BigDecimal("20000"), resultado.getTotal());
    }
}
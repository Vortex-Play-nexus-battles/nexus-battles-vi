package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoControllerTest {

    @Mock
    private CarritoService carritoService;

    @InjectMocks
    private CarritoController carritoController;

    @Test
    void obtenerCarrito_debeRetornarOkConCarrito() {
        // Arrange
        String usuarioId = "usr_12345";
        Carrito carritoMock = new Carrito();
        carritoMock.setUsuarioId(usuarioId);

        when(carritoService.obtenerOCrearCarrito(usuarioId)).thenReturn(carritoMock);

        // Act
        ResponseEntity<Carrito> response = carritoController.obtenerCarrito(usuarioId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(usuarioId, response.getBody().getUsuarioId());
    }

    @Test
    void agregarItem_debeRetornarOkConCarritoActualizado() {
        // Arrange
        String usuarioId = "usr_12345";
        AgregarItemRequest request = new AgregarItemRequest();
        request.setProductoId(1L);
        request.setCantidad(2);

        Carrito carritoActualizado = new Carrito();
        carritoActualizado.setUsuarioId(usuarioId);

        when(carritoService.agregarProducto(eq(usuarioId), any(AgregarItemRequest.class)))
                .thenReturn(carritoActualizado);

        // Act
        ResponseEntity<Carrito> response = carritoController.agregarItem(usuarioId, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
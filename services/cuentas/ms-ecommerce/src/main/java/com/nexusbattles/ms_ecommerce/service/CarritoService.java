package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import com.nexusbattles.ms_ecommerce.model.Producto;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final ProductoRepository productoRepository;

    @Transactional
    public Carrito obtenerOCrearCarrito(String usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> {
                    Carrito nuevoCarrito = new Carrito();
                    nuevoCarrito.setUsuarioId(usuarioId);
                    return carritoRepository.save(nuevoCarrito);
                });
    }

    @Transactional
    public Carrito agregarProducto(String usuarioId, AgregarItemRequest request) {
        Carrito carrito = obtenerOCrearCarrito(usuarioId);
        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        Optional<ItemCarrito> itemExistente = carrito.getItems().stream()
                .filter(item -> item.getProducto().getId().equals(producto.getId()))
                .findFirst();

        if (itemExistente.isPresent()) {
            ItemCarrito item = itemExistente.get();
            item.setCantidad(item.getCantidad() + request.getCantidad());
            item.calcularSubtotal();
        } else {
            ItemCarrito nuevoItem = new ItemCarrito();
            nuevoItem.setCarrito(carrito);
            nuevoItem.setProducto(producto);
            nuevoItem.setCantidad(request.getCantidad());
            nuevoItem.setPrecioUnitario(producto.getPrecioBaseCop());
            nuevoItem.calcularSubtotal();
            carrito.getItems().add(nuevoItem);
        }

        carrito.recalcularTotal();
        return carritoRepository.save(carrito);
    }

    @Transactional
    public Carrito eliminarItem(String usuarioId, Long itemId) {
        Carrito carrito = obtenerOCrearCarrito(usuarioId);
        carrito.getItems().removeIf(item -> item.getId().equals(itemId));
        carrito.recalcularTotal();
        return carritoRepository.save(carrito);
    }
}
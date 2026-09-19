package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.client.FinanzasClient;
import com.nexusbattles.ms_ecommerce.dto.CheckoutRequestDTO;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class CheckoutService {

    private final CarritoRepository carritoRepository;
    private final FinanzasClient finanzasClient;

    public CheckoutService(CarritoRepository carritoRepository, FinanzasClient finanzasClient) {
        this.carritoRepository = carritoRepository;
        this.finanzasClient = finanzasClient;
    }

    @Transactional
    public void ejecutarCompra(String usuarioId, CheckoutRequestDTO request) {
        // 1. Obtener carrito
        Carrito carrito = carritoRepository.findByIdAndUsuarioId(request.carritoId(), usuarioId)
            .orElseThrow(() -> new RuntimeException("Carrito no encontrado"));

        if (carrito.getItems().isEmpty()) {
            throw new RuntimeException("El carrito está vacío");
        }

        // 2. Procesar pago con ms-finanzas
        try {
            Map<String, Object> payloadPago = Map.of(
                "monto", carrito.getTotal(),
                "tarjeta", request.tarjeta()
            );
            finanzasClient.procesarPago(payloadPago);
        } catch (FeignException e) {
            throw new RuntimeException("Pago rechazado por la pasarela de finanzas");
        }

        // 3. Vaciar carrito al confirmar el pago
        carrito.getItems().clear();
        carrito.setTotal(BigDecimal.ZERO);
        carritoRepository.save(carrito);
    }
}

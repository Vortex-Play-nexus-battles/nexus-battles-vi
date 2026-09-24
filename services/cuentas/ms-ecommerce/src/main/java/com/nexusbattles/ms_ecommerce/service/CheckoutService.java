package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.client.FinanzasClient;
import com.nexusbattles.ms_ecommerce.dto.CheckoutRequestDTO;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Ejecuta la compra del carrito contra la pasarela simulada de ms-finanzas
 * (HU-PAG-001).
 *
 * <p>Los datos de la tarjeta solo atraviesan este servicio: no se guardan en
 * ninguna entidad y no se escriben en ningún log. Por eso los mensajes de log
 * de aquí nunca incluyen el payload ni el mensaje de la {@link FeignException},
 * que puede reproducir el cuerpo de la respuesta.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    /**
     * Respuestas de ms-finanzas que significan «la tarjeta no pasó». Un 401/403
     * NO está aquí: eso es un problema de configuración entre servicios, no de
     * la tarjeta del jugador.
     */
    private static final Set<Integer> ESTADOS_DE_RECHAZO = Set.of(400, 402, 409, 422);

    private final CarritoRepository carritoRepository;
    private final FinanzasClient finanzasClient;

    public CheckoutService(CarritoRepository carritoRepository, FinanzasClient finanzasClient) {
        this.carritoRepository = carritoRepository;
        this.finanzasClient = finanzasClient;
    }

    @Transactional
    public ResultadoCompra ejecutarCompra(String usuarioId, CheckoutRequestDTO request) {
        // 1. El carrito tiene que ser del usuario del token: si el id es de
        //    otro, no se encuentra y no se puede pagar.
        Carrito carrito = carritoRepository.findByIdAndUsuarioId(request.carritoId(), usuarioId)
            .orElseThrow(() -> new CarritoNoDisponibleException(
                "No encontramos tu carrito. Recarga la tienda e inténtalo de nuevo."));

        if (carrito.getItems().isEmpty()) {
            throw new CarritoNoDisponibleException("Tu carrito está vacío.");
        }

        // 2. Pago en ms-finanzas.
        Map<String, Object> payloadPago = Map.of(
            "monto", carrito.getTotal(),
            "tarjeta", request.tarjeta());
        try {
            finanzasClient.procesarPago(payloadPago);
        } catch (FeignException e) {
            if (ESTADOS_DE_RECHAZO.contains(e.status())) {
                log.info("Pago rechazado por ms-finanzas (HTTP {})", e.status());
                return ResultadoCompra.rechazado(
                    "La pasarela rechazó el pago. Revisa los datos o usa otra tarjeta.");
            }
            // Caído, sin red (status -1), 5xx o credenciales entre servicios:
            // no es culpa de la tarjeta y no se cobró nada.
            log.warn("ms-finanzas no disponible para procesar el pago (HTTP {})", e.status());
            throw new PasarelaNoDisponibleException(
                "No pudimos procesar el pago en este momento. No se hizo ningún cobro.");
        }

        // 3. Pago aprobado: se vacía el carrito.
        carrito.getItems().clear();
        carrito.setTotal(BigDecimal.ZERO);
        carritoRepository.save(carrito);

        return ResultadoCompra.aprobado(
            "¡Pago aprobado! Los productos se añadieron a tu inventario. "
                + "Te enviamos la confirmación por correo.");
    }

    /** Resultado de la compra tal como lo ve el jugador. */
    public record ResultadoCompra(boolean aprobado, String mensaje) {

        public static ResultadoCompra aprobado(String mensaje) {
            return new ResultadoCompra(true, mensaje);
        }

        public static ResultadoCompra rechazado(String mensaje) {
            return new ResultadoCompra(false, mensaje);
        }
    }

    /** El carrito no existe, no es del usuario o está vacío (409). */
    public static class CarritoNoDisponibleException extends RuntimeException {
        public CarritoNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    /** ms-finanzas no pudo atender el pago (503). */
    public static class PasarelaNoDisponibleException extends RuntimeException {
        public PasarelaNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}

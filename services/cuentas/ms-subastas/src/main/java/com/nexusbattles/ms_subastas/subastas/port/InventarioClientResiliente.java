package com.nexusbattles.ms_subastas.subastas.port;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.Optional;
import java.util.UUID;

/**
 * Envuelve al cliente de inventario con cortacircuitos y reintento, igual que
 * {@code CreditoClientResiliente} hace con los creditos.
 *
 * <p><b>Por que hace falta.</b> Estas llamadas ocurren DENTRO de la transaccion
 * que mantiene el lock pesimista sobre la fila de la subasta. Si inventario deja
 * de responder y no se corta rapido, cada puja se queda esperando con el lock
 * tomado y se congelan todas las pujas de esa subasta. Con la transferencia del
 * producto en este mismo camino, ya son dos servicios remotos dentro del lock
 * —finanzas e inventario— asi que es el riesgo de rendimiento principal.
 *
 * <p>Solo se reintenta y solo cuenta como fallo {@link
 * InventarioNoDisponibleException}. Los rechazos de negocio (un elemento ya
 * bloqueado por otra subasta, una identidad ajena) son respuestas correctas:
 * reintentarlas da lo mismo y abrir el cortacircuitos por ellas dejaria el
 * servicio sin inventario por algo que no es una averia. La configuracion lo
 * declara en {@code resilience4j.*.instances.inventario.*}.
 *
 * <p>El reintento de {@code reservar} y {@code liberarReserva} solo es seguro
 * porque inventario las declara idempotentes por {@code Idempotency-Key} y por
 * {@code subastaId} respectivamente.
 */
public class InventarioClientResiliente implements InventarioClient, InventarioPublicacionClient {

    private static final String INSTANCIA = "inventario";

    private final InventarioClient delegado;

    public InventarioClientResiliente(InventarioClient delegado) {
        this.delegado = delegado;
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public Optional<ElementoInventario> buscar(String elementoInventarioId) {
        return delegado.buscar(elementoInventarioId);
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public void reservar(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey) {
        delegado.reservar(elementoInventarioId, propietarioId, subastaId, idempotencyKey);
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public void liberarReserva(String elementoInventarioId, UUID subastaId, String idempotencyKey) {
        delegado.liberarReserva(elementoInventarioId, subastaId, idempotencyKey);
    }

    @Override
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public void transferirProducto(String elementoInventarioId, UUID nuevoPropietarioId,
                                   UUID subastaId, String idempotencyKey) {
        delegado.transferirProducto(elementoInventarioId, nuevoPropietarioId, subastaId, idempotencyKey);
    }
}

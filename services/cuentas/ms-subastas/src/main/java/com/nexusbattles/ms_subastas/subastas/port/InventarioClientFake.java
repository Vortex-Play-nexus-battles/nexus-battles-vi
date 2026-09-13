package com.nexusbattles.ms_subastas.subastas.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Doble en memoria de InventarioClient para desarrollo local y pruebas sin depender
 * del microservicio real de inventario.
 */
public class InventarioClientFake implements InventarioClient {

    private static final Logger log = LoggerFactory.getLogger(InventarioClientFake.class);

    private final Map<String, ElementoInventario> elementos = new ConcurrentHashMap<>();
    private final Map<String, ReservaRegistro> reservas = new ConcurrentHashMap<>();
    private final List<TransferenciaRegistro> transferencias = new CopyOnWriteArrayList<>();
    private volatile boolean simularFallo = false;
    private volatile String mensajeFallo = "Fallo simulado en el servicio de inventario";

    public record ReservaRegistro(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey) {}
    public record TransferenciaRegistro(String elementoInventarioId, UUID nuevoPropietarioId, UUID subastaId, String idempotencyKey) {}

    public void registrarElemento(ElementoInventario elemento) {
        if (elemento != null) {
            elementos.put(elemento.id(), elemento);
        }
    }

    public void simularFallo(boolean simular) {
        this.simularFallo = simular;
    }

    public void simularFallo(boolean simular, String mensaje) {
        this.simularFallo = simular;
        this.mensajeFallo = mensaje;
    }

    public List<TransferenciaRegistro> getTransferencias() {
        return Collections.unmodifiableList(transferencias);
    }

    public Map<String, ReservaRegistro> getReservas() {
        return Collections.unmodifiableMap(reservas);
    }

    public void limpiar() {
        elementos.clear();
        reservas.clear();
        transferencias.clear();
        simularFallo = false;
    }

    @Override
    public Optional<ElementoInventario> buscar(String elementoInventarioId) {
        if (simularFallo) {
            throw new InventarioClientException(mensajeFallo);
        }
        ElementoInventario elem = elementos.get(elementoInventarioId);
        return Optional.ofNullable(elem);
    }

    @Override
    public synchronized void reservar(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey) {
        if (simularFallo) {
            throw new InventarioClientException(mensajeFallo);
        }
        ElementoInventario actual = elementos.get(elementoInventarioId);
        if (actual != null && actual.enUso()) {
            throw new InventarioClientException("El producto ya está en uso");
        }
        if (actual != null) {
            elementos.put(elementoInventarioId, new ElementoInventario(actual.id(), actual.productoId(), actual.propietarioId(), true));
        } else {
            elementos.put(elementoInventarioId, new ElementoInventario(elementoInventarioId, UUID.randomUUID(), propietarioId, true));
        }
        reservas.put(subastaId.toString(), new ReservaRegistro(elementoInventarioId, propietarioId, subastaId, idempotencyKey));
        log.info("Elemento {} reservado para subasta {}", elementoInventarioId, subastaId);
    }

    @Override
    public synchronized void liberarReserva(String elementoInventarioId, UUID subastaId, String idempotencyKey) {
        if (simularFallo) {
            throw new InventarioClientException(mensajeFallo);
        }
        reservas.remove(subastaId.toString());
        ElementoInventario actual = elementos.get(elementoInventarioId);
        if (actual != null) {
            elementos.put(elementoInventarioId, new ElementoInventario(actual.id(), actual.productoId(), actual.propietarioId(), false));
        }
        log.info("Reserva del elemento {} liberada para subasta {}", elementoInventarioId, subastaId);
    }

    @Override
    public synchronized void transferirProducto(String elementoInventarioId, UUID nuevoPropietarioId, UUID subastaId, String idempotencyKey) {
        if (simularFallo) {
            throw new InventarioClientException(mensajeFallo);
        }
        reservas.remove(subastaId.toString());
        ElementoInventario actual = elementos.get(elementoInventarioId);
        UUID productoId = actual != null ? actual.productoId() : UUID.randomUUID();
        elementos.put(elementoInventarioId, new ElementoInventario(elementoInventarioId, productoId, nuevoPropietarioId, false));
        transferencias.add(new TransferenciaRegistro(elementoInventarioId, nuevoPropietarioId, subastaId, idempotencyKey));
        log.info("Elemento {} transferido formalmente a {} por subasta {}", elementoInventarioId, nuevoPropietarioId, subastaId);
    }
}

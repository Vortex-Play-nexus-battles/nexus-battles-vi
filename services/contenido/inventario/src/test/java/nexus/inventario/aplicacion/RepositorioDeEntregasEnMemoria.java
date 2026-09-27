package nexus.inventario.aplicacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import nexus.inventario.dominio.ClaveDeEntregaOcupadaException;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.RepositorioDeEntregas;

/**
 * Doble de la coleccion {@code entregas}: una por clave, como el indice unico
 * de Mongo, y con fallos a pedido para simular una caida entre pasos (B4).
 */
public class RepositorioDeEntregasEnMemoria implements RepositorioDeEntregas {

    private final Map<String, Entrega> porClave = new LinkedHashMap<>();
    private boolean fallarAlCompletar;
    private Runnable antesDeRegistrar;

    @Override
    public synchronized Optional<Entrega> buscarPorClave(String clave) {
        return Optional.ofNullable(porClave.get(clave));
    }

    @Override
    public void registrar(Entrega entrega) {
        if (antesDeRegistrar != null) {
            // Otra peticion con la misma clave que llega entre la busqueda y el registro.
            Runnable otraPeticion = antesDeRegistrar;
            antesDeRegistrar = null;
            otraPeticion.run();
        }
        synchronized (this) {
            if (porClave.containsKey(entrega.clave())) {
                throw new ClaveDeEntregaOcupadaException(new RuntimeException("clave duplicada"));
            }
            porClave.put(entrega.clave(), entrega);
        }
    }

    @Override
    public synchronized void completar(String entregaId, Instant entregadaEn) {
        if (fallarAlCompletar) {
            fallarAlCompletar = false;
            throw new FalloPersistenciaInventarioException(new RuntimeException("caida simulada al completar"));
        }
        porClave.replaceAll((clave, entrega) -> entrega.id().equals(entregaId) && !entrega.completada()
                ? entrega.completadaEn(entregadaEn)
                : entrega);
    }

    /** El siguiente {@code completar} falla: la entrega queda PENDIENTE con el inventario ya escrito. */
    public void fallarAlCompletar() {
        fallarAlCompletar = true;
    }

    /** Ejecuta otra peticion justo antes del siguiente registro. */
    public void antesDeRegistrar(Runnable otraPeticion) {
        antesDeRegistrar = otraPeticion;
    }

    public synchronized int cantidad() {
        return porClave.size();
    }
}

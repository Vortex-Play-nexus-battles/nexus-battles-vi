package nexus.inventario.aplicacion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;

public class RepositorioInventariosEnMemoria implements RepositorioDeInventarios {

    private final Map<String, Inventario> inventarios = new LinkedHashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();
    private boolean fallarSiguienteGuardado;

    @Override
    public Inventario guardar(Inventario inventario) {
        if (fallarSiguienteGuardado) {
            fallarSiguienteGuardado = false;
            throw new FalloPersistenciaInventarioException(new RuntimeException("fallo simulado"));
        }
        Inventario guardado = inventario.id() == null
                ? new Inventario("inventario-" + secuencia.incrementAndGet(),
                        inventario.propietarioId(), inventario.elementos())
                : inventario;
        inventarios.put(guardado.propietarioId(), guardado);
        return guardado;
    }

    public void fallarSiguienteGuardado() {
        fallarSiguienteGuardado = true;
    }

    @Override
    public Optional<Inventario> buscarPorPropietario(String propietarioId) {
        return Optional.ofNullable(inventarios.get(propietarioId));
    }

    @Override
    public Optional<Inventario> buscarPorElementoId(String elementoId) {
        return inventarios.values().stream()
                .filter(inventario -> inventario.elementos().stream()
                        .anyMatch(elemento -> elemento.id().equals(elementoId)))
                .findFirst();
    }
}

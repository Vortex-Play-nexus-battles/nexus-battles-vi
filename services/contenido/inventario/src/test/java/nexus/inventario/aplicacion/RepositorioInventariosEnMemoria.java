package nexus.inventario.aplicacion;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import nexus.inventario.dominio.ConflictoDeEscrituraException;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;

public class RepositorioInventariosEnMemoria implements RepositorioDeInventarios {

    private final Map<String, Inventario> inventarios = new LinkedHashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();
    private final AtomicInteger guardados = new AtomicInteger();
    private boolean fallarSiguienteGuardado;
    private int conflictosPendientes;
    private Runnable antesDelSiguienteGuardado;

    @Override
    public Inventario guardar(Inventario inventario) {
        if (antesDelSiguienteGuardado != null) {
            // Otra escritura que llega entre la lectura y el guardado.
            Runnable otraEscritura = antesDelSiguienteGuardado;
            antesDelSiguienteGuardado = null;
            otraEscritura.run();
        }
        if (fallarSiguienteGuardado) {
            fallarSiguienteGuardado = false;
            throw new FalloPersistenciaInventarioException(new RuntimeException("fallo simulado"));
        }
        if (conflictosPendientes > 0) {
            conflictosPendientes--;
            throw new ConflictoDeEscrituraException(new RuntimeException("conflicto simulado"));
        }
        // B4: un inventario nuevo conserva sus entregas y su version (puede
        // nacer de una entrega, con la entrega ya anotada).
        Inventario guardado = inventario.id() == null
                ? new Inventario("inventario-" + secuencia.incrementAndGet(),
                        inventario.propietarioId(), inventario.elementos(), inventario.equipamientos(),
                        inventario.entregas(), inventario.version())
                : inventario;
        inventarios.put(guardado.propietarioId(), guardado);
        guardados.incrementAndGet();
        return guardado;
    }

    public void fallarSiguienteGuardado() {
        fallarSiguienteGuardado = true;
    }

    /** Los siguientes {@code veces} guardados chocan con otra escritura (B4, {@code @Version}). */
    public void conflictoEnLosSiguientesGuardados(int veces) {
        conflictosPendientes = veces;
    }

    /** Ejecuta otra escritura justo antes del siguiente guardado. */
    public void antesDelSiguienteGuardado(Runnable otraEscritura) {
        antesDelSiguienteGuardado = otraEscritura;
    }

    /** Cuantos guardados se completaron. */
    public int guardados() {
        return guardados.get();
    }

    @Override
    public Optional<Inventario> buscarPorPropietario(String propietarioId) {
        return Optional.ofNullable(inventarios.get(propietarioId));
    }

    @Override
    public Optional<Inventario> buscarPorElementoId(String elementoId) {
        return buscarTodosPorElementoId(elementoId).stream().findFirst();
    }

    @Override
    public List<Inventario> buscarTodosPorElementoId(String elementoId) {
        return inventarios.values().stream()
                .filter(inventario -> inventario.elementos().stream()
                        .anyMatch(elemento -> elemento.id().equals(elementoId)))
                .toList();
    }

    @Override
    public List<ElementoInventario> buscarElementos(String propietarioId, String criterio) {
        String buscado = normalizar(criterio);
        return buscarPorPropietario(propietarioId).stream()
                .flatMap(inventario -> inventario.elementos().stream())
                .filter(elemento -> List.of(
                                elemento.productoId(), elemento.tipo().name(),
                                elemento.nombrePropio(), elemento.parteArmadura() == null
                                        ? "" : elemento.parteArmadura().name())
                        .stream().map(this::normalizar)
                        .anyMatch(valor -> valor.contains(buscado)))
                .toList();
    }

    private String normalizar(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}

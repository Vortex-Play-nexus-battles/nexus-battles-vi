package nexus.inventario.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Agregado persistido como un documento por jugador. */
public record Inventario(
        String id,
        String propietarioId,
        List<ElementoInventario> elementos) {

    public Inventario {
        if (id != null && id.isBlank()) {
            throw new IllegalArgumentException("id no puede estar vacio");
        }
        if (propietarioId == null || propietarioId.isBlank()) {
            throw new IllegalArgumentException("propietarioId no puede estar vacio");
        }
        elementos = List.copyOf(Objects.requireNonNull(elementos, "elementos no puede ser nulo"));
    }

    public static Inventario vacio(String propietarioId) {
        return new Inventario(null, propietarioId, List.of());
    }

    public Inventario agregar(ElementoInventario elemento) {
        Objects.requireNonNull(elemento, "elemento no puede ser nulo");
        if (elementos.stream().anyMatch(actual -> actual.id().equals(elemento.id()))) {
            throw new IllegalArgumentException("Ya existe un elemento con id " + elemento.id());
        }
        List<ElementoInventario> actualizados = new ArrayList<>(elementos);
        actualizados.add(elemento);
        return new Inventario(id, propietarioId, actualizados);
    }

    public Inventario renombrarElemento(String elementoId, String nuevoNombre) {
        boolean existe = elementos.stream().anyMatch(elemento -> elemento.id().equals(elementoId));
        if (!existe) {
            throw new ElementoNoEncontradoException();
        }
        List<ElementoInventario> actualizados = elementos.stream()
                .map(elemento -> elemento.id().equals(elementoId)
                        ? elemento.renombrar(nuevoNombre)
                        : elemento)
                .toList();
        return new Inventario(id, propietarioId, actualizados);
    }

    public ElementoInventario elemento(String elementoId) {
        return elementos.stream()
                .filter(elemento -> elemento.id().equals(elementoId))
                .findFirst()
                .orElseThrow(ElementoNoEncontradoException::new);
    }
}

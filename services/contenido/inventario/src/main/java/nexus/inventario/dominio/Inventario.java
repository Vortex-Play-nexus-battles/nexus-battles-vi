package nexus.inventario.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Agregado persistido como un documento por jugador. */
public record Inventario(
        String id,
        String propietarioId,
        List<ElementoInventario> elementos,
        List<EquipamientoHeroe> equipamientos) {

    public Inventario(String id, String propietarioId, List<ElementoInventario> elementos) {
        this(id, propietarioId, elementos, List.of());
    }

    public Inventario {
        if (id != null && id.isBlank()) {
            throw new IllegalArgumentException("id no puede estar vacio");
        }
        if (propietarioId == null || propietarioId.isBlank()) {
            throw new IllegalArgumentException("propietarioId no puede estar vacio");
        }
        elementos = List.copyOf(Objects.requireNonNull(elementos, "elementos no puede ser nulo"));
        equipamientos = equipamientos == null ? List.of() : List.copyOf(equipamientos);
    }

    public static Inventario vacio(String propietarioId) {
        return new Inventario(null, propietarioId, List.of(), List.of());
    }

    public Inventario agregar(ElementoInventario elemento) {
        Objects.requireNonNull(elemento, "elemento no puede ser nulo");
        if (elementos.stream().anyMatch(actual -> actual.id().equals(elemento.id()))) {
            throw new IllegalArgumentException("Ya existe un elemento con id " + elemento.id());
        }
        List<ElementoInventario> actualizados = new ArrayList<>(elementos);
        actualizados.add(elemento);
        return new Inventario(id, propietarioId, actualizados, equipamientos);
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
        return new Inventario(id, propietarioId, actualizados, equipamientos);
    }

    public ElementoInventario elemento(String elementoId) {
        return elementos.stream()
                .filter(elemento -> elemento.id().equals(elementoId))
                .findFirst()
                .orElseThrow(ElementoNoEncontradoException::new);
    }

    public EquipamientoHeroe equipamiento(String heroeId) {
        validarHeroe(heroeId);
        return equipamientos.stream()
                .filter(equipamiento -> equipamiento.heroeId().equals(heroeId))
                .findFirst()
                .orElseGet(() -> EquipamientoHeroe.vacio(heroeId));
    }

    public Inventario equipar(String heroeId, String elementoId) {
        validarHeroe(heroeId);
        ElementoInventario elemento = elemento(elementoId);
        boolean equipadoEnOtroHeroe = equipamientos.stream()
                .filter(equipamiento -> !equipamiento.heroeId().equals(heroeId))
                .anyMatch(equipamiento -> equipamiento.contiene(elementoId));
        if (equipadoEnOtroHeroe) {
            throw new ElementoYaEquipadoException();
        }
        return reemplazarEquipamiento(equipamiento(heroeId).equipar(elemento));
    }

    public Inventario desequipar(String heroeId, String elementoId) {
        validarHeroe(heroeId);
        return reemplazarEquipamiento(equipamiento(heroeId).desequipar(elementoId));
    }

    private void validarHeroe(String heroeId) {
        ElementoInventario heroe = elemento(heroeId);
        if (heroe.tipo() != TipoElementoInventario.HEROE) {
            throw new ElementoNoEquipableException("El destino del equipamiento debe ser un heroe");
        }
    }

    private Inventario reemplazarEquipamiento(EquipamientoHeroe actualizado) {
        List<EquipamientoHeroe> nuevos = new ArrayList<>(equipamientos);
        boolean reemplazado = false;
        for (int indice = 0; indice < nuevos.size(); indice++) {
            if (nuevos.get(indice).heroeId().equals(actualizado.heroeId())) {
                nuevos.set(indice, actualizado);
                reemplazado = true;
                break;
            }
        }
        if (!reemplazado) {
            nuevos.add(actualizado);
        }
        return new Inventario(id, propietarioId, elementos, nuevos);
    }
}

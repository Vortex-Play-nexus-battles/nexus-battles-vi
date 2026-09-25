package nexus.inventario.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agregado persistido como un documento por jugador.
 *
 * <h2>B4</h2>
 * <ul>
 *   <li>{@code entregas}: identificadores de las entregas ({@code POST
 *       /entregas}) ya aplicadas a este inventario. Viven en el MISMO documento
 *       que los elementos para que aplicar una entrega —anadir sus elementos y
 *       anotarla— sea una sola escritura atomica, y para que reaplicarla tras un
 *       fallo no la duplique aunque el jugador haya movido o borrado despues los
 *       elementos que recibio.</li>
 *   <li>{@code version}: bloqueo optimista del documento ({@code @Version} en
 *       la persistencia). Cada escritura reemplaza el documento entero; sin
 *       version, dos escrituras que leyeron lo mismo se pisaban y la segunda
 *       borraba en silencio lo que anadio la primera — por ejemplo, los objetos
 *       de una compra entregados mientras el jugador renombraba otro. Nulo en un
 *       inventario nuevo o anterior a B4.</li>
 * </ul>
 */
public record Inventario(
        String id,
        String propietarioId,
        List<ElementoInventario> elementos,
        List<EquipamientoHeroe> equipamientos,
        List<String> entregas,
        Long version) {

    public Inventario(String id, String propietarioId, List<ElementoInventario> elementos) {
        this(id, propietarioId, elementos, List.of());
    }

    public Inventario(
            String id,
            String propietarioId,
            List<ElementoInventario> elementos,
            List<EquipamientoHeroe> equipamientos) {
        this(id, propietarioId, elementos, equipamientos, List.of(), null);
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
        entregas = entregas == null ? List.of() : List.copyOf(entregas);
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
        return con(actualizados, equipamientos);
    }

    /** Si esa entrega ya se aplico a este inventario. */
    public boolean recibio(String entregaId) {
        return entregas.contains(entregaId);
    }

    /**
     * Aplica una entrega: todos sus elementos y la anotacion de que llego, o
     * nada. Si ya estaba aplicada no cambia nada (idempotente).
     */
    public Inventario recibirEntrega(String entregaId, List<ElementoInventario> recibidos) {
        if (entregaId == null || entregaId.isBlank()) {
            throw new IllegalArgumentException("entregaId no puede estar vacio");
        }
        if (recibio(entregaId)) {
            return this;
        }
        Inventario conLosElementos = this;
        for (ElementoInventario elemento : recibidos) {
            conLosElementos = conLosElementos.agregar(elemento);
        }
        List<String> anotadas = new ArrayList<>(entregas);
        anotadas.add(entregaId);
        return new Inventario(id, propietarioId, conLosElementos.elementos, equipamientos, anotadas, version);
    }

    public Inventario renombrarElemento(String elementoId, String nuevoNombre) {
        elemento(elementoId).exigirDisponible();
        List<ElementoInventario> actualizados = elementos.stream()
                .map(elemento -> elemento.id().equals(elementoId)
                        ? elemento.renombrar(nuevoNombre)
                        : elemento)
                .toList();
        return con(actualizados, equipamientos);
    }

    public Inventario eliminarElemento(String elementoId) {
        ElementoInventario elemento = elemento(elementoId);
        elemento.exigirDisponible();
        if (estaEnUso(elementoId)) {
            throw new ElementoNoDisponibleException(
                    "El producto esta equipado y no se puede eliminar.");
        }
        List<ElementoInventario> actualizados = elementos.stream()
                .filter(actual -> !actual.id().equals(elementoId))
                .toList();
        List<EquipamientoHeroe> equipamientosActualizados = equipamientos.stream()
                .filter(equipamiento -> !equipamiento.heroeId().equals(elementoId))
                .toList();
        return con(actualizados, equipamientosActualizados);
    }

    public Inventario bloquearEnSubasta(String elementoId, String subastaId) {
        ElementoInventario elemento = elemento(elementoId);
        if (estaEnUso(elementoId)) {
            throw new ElementoNoDisponibleException(
                    "El producto esta equipado y no se puede publicar en subasta.");
        }
        List<ElementoInventario> actualizados = elementos.stream()
                .map(actual -> actual.id().equals(elementoId)
                        ? elemento.bloquearEnSubasta(subastaId)
                        : actual)
                .toList();
        return con(actualizados, equipamientos);
    }

    public Inventario liberarBloqueoSubasta(String elementoId, String subastaId) {
        ElementoInventario elemento = elemento(elementoId);
        List<ElementoInventario> actualizados = elementos.stream()
                .map(actual -> actual.id().equals(elementoId)
                        ? elemento.liberarBloqueoSubasta(subastaId)
                        : actual)
                .toList();
        return con(actualizados, equipamientos);
    }

    public boolean estaEnUso(String elementoId) {
        return equipamientos.stream().anyMatch(equipamiento -> equipamiento.contiene(elementoId));
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
        return equipar(heroeId, elementoId, null);
    }

    /**
     * Equipa un elemento en un heroe propio.
     *
     * <p>B4: el heroe tiene que estar disponible (uno comprometido en una subasta
     * no cambia de equipo, igual que no cambia de dueno ni de nombre), y la
     * ranura de una armadura la decide el catalogo: si llega
     * {@code parteDelCatalogo} y no coincide con la guardada —los elementos
     * anteriores guardaron la que mando el cliente—, se corrige antes de
     * equipar.
     *
     * @param parteDelCatalogo la parte del producto en el catalogo, o nulo si
     *                         el elemento no es una armadura
     */
    public Inventario equipar(String heroeId, String elementoId, ParteArmadura parteDelCatalogo) {
        validarHeroe(heroeId);
        if (!elemento(heroeId).disponible()) {
            throw new ElementoNoDisponibleException(
                    "El heroe esta bloqueado por una subasta vigente y no se puede equipar.");
        }
        ElementoInventario elemento = elemento(elementoId);
        elemento.exigirDisponible();
        boolean equipadoEnOtroHeroe = equipamientos.stream()
                .filter(equipamiento -> !equipamiento.heroeId().equals(heroeId))
                .anyMatch(equipamiento -> equipamiento.contiene(elementoId));
        if (equipadoEnOtroHeroe) {
            throw new ElementoYaEquipadoException();
        }
        Inventario base = this;
        if (parteDelCatalogo != null && parteDelCatalogo != elemento.parteArmadura()) {
            elemento = elemento.conParte(parteDelCatalogo);
            ElementoInventario corregido = elemento;
            base = con(elementos.stream()
                    .map(actual -> actual.id().equals(elementoId) ? corregido : actual)
                    .toList(), equipamientos);
        }
        return base.reemplazarEquipamiento(equipamiento(heroeId).equipar(elemento));
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
        return con(elementos, nuevos);
    }

    /** El mismo agregado —misma version y mismas entregas— con otros elementos o equipamientos. */
    private Inventario con(List<ElementoInventario> nuevosElementos, List<EquipamientoHeroe> nuevosEquipamientos) {
        return new Inventario(id, propietarioId, nuevosElementos, nuevosEquipamientos, entregas, version);
    }
}

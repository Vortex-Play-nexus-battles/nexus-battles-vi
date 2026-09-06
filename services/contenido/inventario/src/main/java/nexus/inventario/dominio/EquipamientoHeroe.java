package nexus.inventario.dominio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Estado de las ranuras ocupadas por una instancia de heroe. */
public record EquipamientoHeroe(
        String heroeId,
        List<String> armas,
        Map<ParteArmadura, String> armaduras,
        List<String> items) {

    public static final int MAX_ARMAS = 2;
    public static final int MAX_ITEMS = 2;

    public EquipamientoHeroe {
        exigirTexto(heroeId, "heroeId");
        armas = List.copyOf(Objects.requireNonNull(armas, "armas no puede ser nulo"));
        armaduras = Map.copyOf(Objects.requireNonNull(armaduras, "armaduras no puede ser nulo"));
        items = List.copyOf(Objects.requireNonNull(items, "items no puede ser nulo"));
        if (armas.size() > MAX_ARMAS) {
            throw new LimiteEquipamientoException("Un heroe solo puede llevar dos armas");
        }
        if (armaduras.size() > ParteArmadura.values().length) {
            throw new LimiteEquipamientoException("Un heroe solo puede llevar seis armaduras");
        }
        if (items.size() > MAX_ITEMS) {
            throw new LimiteEquipamientoException("Un heroe solo puede llevar dos items");
        }
    }

    public static EquipamientoHeroe vacio(String heroeId) {
        return new EquipamientoHeroe(heroeId, List.of(), Map.of(), List.of());
    }

    public EquipamientoHeroe equipar(ElementoInventario elemento) {
        Objects.requireNonNull(elemento, "elemento no puede ser nulo");
        if (contiene(elemento.id())) {
            throw new ElementoYaEquipadoException();
        }

        return switch (elemento.tipo()) {
            case ARMA -> equiparArma(elemento.id());
            case ARMADURA -> equiparArmadura(elemento);
            case ITEM -> equiparItem(elemento.id());
            default -> throw new ElementoNoEquipableException(
                    "El tipo " + elemento.tipo() + " no se puede equipar");
        };
    }

    public EquipamientoHeroe desequipar(String elementoId) {
        exigirTexto(elementoId, "elementoId");
        List<String> armasActualizadas = armas.stream()
                .filter(id -> !id.equals(elementoId))
                .toList();
        EnumMap<ParteArmadura, String> armadurasActualizadas = new EnumMap<>(ParteArmadura.class);
        armaduras.forEach((parte, id) -> {
            if (!id.equals(elementoId)) {
                armadurasActualizadas.put(parte, id);
            }
        });
        List<String> itemsActualizados = items.stream()
                .filter(id -> !id.equals(elementoId))
                .toList();
        return new EquipamientoHeroe(heroeId, armasActualizadas, armadurasActualizadas, itemsActualizados);
    }

    public boolean contiene(String elementoId) {
        return armas.contains(elementoId)
                || armaduras.containsValue(elementoId)
                || items.contains(elementoId);
    }

    private EquipamientoHeroe equiparArma(String elementoId) {
        if (armas.size() >= MAX_ARMAS) {
            throw new LimiteEquipamientoException("Un heroe solo puede llevar dos armas");
        }
        List<String> actualizadas = new ArrayList<>(armas);
        actualizadas.add(elementoId);
        return new EquipamientoHeroe(heroeId, actualizadas, armaduras, items);
    }

    private EquipamientoHeroe equiparArmadura(ElementoInventario elemento) {
        ParteArmadura parte = elemento.parteArmadura();
        if (parte == null) {
            throw new ElementoNoEquipableException("La armadura no tiene una ranura definida");
        }
        if (armaduras.containsKey(parte)) {
            throw new LimiteEquipamientoException("La ranura " + parte + " ya esta ocupada");
        }
        EnumMap<ParteArmadura, String> actualizadas = new EnumMap<>(ParteArmadura.class);
        actualizadas.putAll(armaduras);
        actualizadas.put(parte, elemento.id());
        return new EquipamientoHeroe(heroeId, armas, actualizadas, items);
    }

    private EquipamientoHeroe equiparItem(String elementoId) {
        if (items.size() >= MAX_ITEMS) {
            throw new LimiteEquipamientoException("Un heroe solo puede llevar dos items");
        }
        List<String> actualizados = new ArrayList<>(items);
        actualizados.add(elementoId);
        return new EquipamientoHeroe(heroeId, armas, armaduras, actualizados);
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
    }
}

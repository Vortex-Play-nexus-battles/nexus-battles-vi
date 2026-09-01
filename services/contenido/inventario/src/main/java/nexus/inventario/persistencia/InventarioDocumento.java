package nexus.inventario.persistencia;

import java.util.List;
import java.util.Map;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "inventarios")
record InventarioDocumento(
        @Id String id,
        @Indexed(unique = true) String propietarioId,
        List<ElementoDocumento> elementos,
        List<EquipamientoDocumento> equipamientos) {

    @PersistenceCreator
    InventarioDocumento {
    }

    InventarioDocumento(String id, String propietarioId, List<ElementoDocumento> elementos) {
        this(id, propietarioId, elementos, List.of());
    }

    static InventarioDocumento de(Inventario inventario) {
        return new InventarioDocumento(
                inventario.id(),
                inventario.propietarioId(),
                inventario.elementos().stream().map(ElementoDocumento::de).toList(),
                inventario.equipamientos().stream().map(EquipamientoDocumento::de).toList());
    }

    Inventario aDominio() {
        return new Inventario(
                id,
                propietarioId,
                elementos.stream().map(ElementoDocumento::aDominio).toList(),
                equipamientos == null
                        ? List.of()
                        : equipamientos.stream().map(EquipamientoDocumento::aDominio).toList());
    }
}

record ElementoDocumento(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura) {

    static ElementoDocumento de(ElementoInventario elemento) {
        return new ElementoDocumento(
                elemento.id(), elemento.productoId(), elemento.tipo(),
                elemento.nombrePropio(), elemento.parteArmadura());
    }

    ElementoInventario aDominio() {
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura);
    }
}

record EquipamientoDocumento(
        String heroeId,
        List<String> armas,
        Map<ParteArmadura, String> armaduras,
        List<String> items) {

    static EquipamientoDocumento de(EquipamientoHeroe equipamiento) {
        return new EquipamientoDocumento(
                equipamiento.heroeId(), equipamiento.armas(),
                equipamiento.armaduras(), equipamiento.items());
    }

    EquipamientoHeroe aDominio() {
        return new EquipamientoHeroe(heroeId, armas, armaduras, items);
    }
}

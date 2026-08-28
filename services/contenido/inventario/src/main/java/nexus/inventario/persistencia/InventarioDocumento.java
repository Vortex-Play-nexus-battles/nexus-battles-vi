package nexus.inventario.persistencia;

import java.util.List;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "inventarios")
record InventarioDocumento(
        @Id String id,
        @Indexed(unique = true) String propietarioId,
        List<ElementoDocumento> elementos) {

    static InventarioDocumento de(Inventario inventario) {
        return new InventarioDocumento(
                inventario.id(),
                inventario.propietarioId(),
                inventario.elementos().stream().map(ElementoDocumento::de).toList());
    }

    Inventario aDominio() {
        return new Inventario(
                id,
                propietarioId,
                elementos.stream().map(ElementoDocumento::aDominio).toList());
    }
}

record ElementoDocumento(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio) {

    static ElementoDocumento de(ElementoInventario elemento) {
        return new ElementoDocumento(
                elemento.id(), elemento.productoId(), elemento.tipo(), elemento.nombrePropio());
    }

    ElementoInventario aDominio() {
        return new ElementoInventario(id, productoId, tipo, nombrePropio);
    }
}

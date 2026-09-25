package nexus.inventario.persistencia;

import java.time.Instant;
import java.util.List;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.EstadoEntrega;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una entrega en la coleccion {@code entregas} — B4. El indice unico de
 * {@code clave} es el que garantiza una entrega por {@code Idempotency-Key},
 * aunque dos peticiones con la misma clave lleguen a la vez.
 */
@Document(collection = "entregas")
record EntregaDocumento(
        @Id String id,
        @Indexed(unique = true) String clave,
        String huella,
        String uid,
        OrigenDeEntrega origen,
        String referencia,
        List<LineaDocumento> productos,
        List<ElementoEntregadoDocumento> elementos,
        EstadoEntrega estado,
        String solicitante,
        Instant creadaEn,
        Instant entregadaEn) {

    @PersistenceCreator
    EntregaDocumento {
        productos = productos == null ? List.of() : List.copyOf(productos);
        elementos = elementos == null ? List.of() : List.copyOf(elementos);
    }

    static EntregaDocumento de(Entrega entrega) {
        return new EntregaDocumento(
                entrega.id(), entrega.clave(), entrega.huella(), entrega.uid(), entrega.origen(),
                entrega.referencia(),
                entrega.productos().stream().map(l -> new LineaDocumento(l.productoId(), l.cantidad())).toList(),
                entrega.elementos().stream().map(ElementoEntregadoDocumento::de).toList(),
                entrega.estado(), entrega.solicitante(), entrega.creadaEn(), entrega.entregadaEn());
    }

    Entrega aDominio() {
        return new Entrega(
                id, clave, huella, uid, origen, referencia,
                productos.stream().map(l -> new LineaDeEntrega(l.productoId(), l.cantidad())).toList(),
                elementos.stream().map(ElementoEntregadoDocumento::aDominio).toList(),
                estado, solicitante, creadaEn, entregadaEn);
    }
}

record LineaDocumento(String productoId, int cantidad) {
}

/**
 * Un elemento planeado de la entrega. Sin los indices de texto de
 * {@link ElementoDocumento}: aqui no se busca, se recuerda.
 */
record ElementoEntregadoDocumento(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura,
        OrigenDeEntrega origen,
        String referencia,
        Integer nivel,
        Double experiencia) {

    static ElementoEntregadoDocumento de(ElementoInventario elemento) {
        return new ElementoEntregadoDocumento(
                elemento.id(), elemento.productoId(), elemento.tipo(), elemento.nombrePropio(),
                elemento.parteArmadura(), elemento.origen(), elemento.referencia(),
                elemento.nivel(), elemento.experiencia());
    }

    ElementoInventario aDominio() {
        return new ElementoInventario(
                id, productoId, tipo, nombrePropio, parteArmadura, null, origen, referencia, nivel, experiencia);
    }
}

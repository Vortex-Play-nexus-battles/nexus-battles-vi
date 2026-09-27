package nexus.inventario.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Una entrega de productos del catalogo al inventario de un jugador — B4
 * ({@code POST /api/v1/inventario/entregas}, contrato inventario 1.5.0).
 *
 * <p>Se registra PENDIENTE, con los elementos ya planeados —identificadores
 * incluidos— antes de tocar el inventario, y pasa a COMPLETADA cuando el
 * inventario los tiene. Es lo que hace segura una entrega sin transacciones
 * multi-documento: si algo falla entre medias, la misma clave la retoma con
 * los MISMOS elementos, y el inventario la reconoce (anota sus entregas en el
 * propio documento) y no la aplica dos veces.
 *
 * @param clave      {@code Idempotency-Key}; unica en la coleccion
 * @param huella     resumen del cuerpo: la misma clave con otro cuerpo es 409
 * @param uid        jugador que recibe (su identificador estable)
 * @param elementos  los elementos que recibe, planeados al registrarla
 * @param solicitante {@code azp} del servicio o {@code uid} del administrador
 *                   que la pidio, para la auditoria
 */
public record Entrega(
        String id,
        String clave,
        String huella,
        String uid,
        OrigenDeEntrega origen,
        String referencia,
        List<LineaDeEntrega> productos,
        List<ElementoInventario> elementos,
        EstadoEntrega estado,
        String solicitante,
        Instant creadaEn,
        Instant entregadaEn) {

    public Entrega {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clave, "clave");
        Objects.requireNonNull(huella, "huella");
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(origen, "origen");
        Objects.requireNonNull(estado, "estado");
        productos = List.copyOf(Objects.requireNonNull(productos, "productos"));
        elementos = List.copyOf(Objects.requireNonNull(elementos, "elementos"));
    }

    public static Entrega pendiente(
            String id,
            String clave,
            String huella,
            String uid,
            OrigenDeEntrega origen,
            String referencia,
            List<LineaDeEntrega> productos,
            List<ElementoInventario> elementos,
            String solicitante,
            Instant creadaEn) {
        return new Entrega(id, clave, huella, uid, origen, referencia, productos, elementos,
                EstadoEntrega.PENDIENTE, solicitante, creadaEn, null);
    }

    public boolean completada() {
        return estado == EstadoEntrega.COMPLETADA;
    }

    public Entrega completadaEn(Instant momento) {
        return new Entrega(id, clave, huella, uid, origen, referencia, productos, elementos,
                EstadoEntrega.COMPLETADA, solicitante, creadaEn, momento);
    }
}

package nexus.inventario.dominio;

/** Un producto de una entrega y cuantas unidades de el (1 a 20, lo valida la API). */
public record LineaDeEntrega(String productoId, int cantidad) {

    public LineaDeEntrega {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("productoId no puede estar vacio");
        }
        if (cantidad < 1) {
            throw new IllegalArgumentException("cantidad debe ser al menos 1");
        }
    }
}

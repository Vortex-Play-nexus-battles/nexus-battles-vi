package nexus.inventario.dominio;

/**
 * Por que canal llego un elemento al inventario — B4 (contrato inventario,
 * esquema {@code OrigenDeEntrega}). Queda guardado con cada elemento entregado:
 * la propiedad solo llega por canales validos, y este es el rastro de cual.
 */
public enum OrigenDeEntrega {
    PAQUETE_INICIAL,
    COMPRA,
    SUBASTA,
    COFRE,
    PREMIO_TORNEO,
    MISION,
    RECOMPENSA,
    ADMINISTRACION
}

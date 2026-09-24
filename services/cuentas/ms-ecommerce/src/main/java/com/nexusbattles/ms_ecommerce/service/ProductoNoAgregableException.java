package com.nexusbattles.ms_ecommerce.service;

/**
 * El producto pedido no puede entrar al carrito, y por que.
 *
 * <p>El motivo es de dominio; la traduccion a HTTP (422 o 409 y su
 * {@code type} de problem details) la hace {@code ManejadorDeErrores}.
 */
public class ProductoNoAgregableException extends RuntimeException {

    /** Por que no se puede agregar, en el orden en que se comprueba. */
    public enum Motivo {
        /** El catalogo maestro no tiene ese producto. */
        INEXISTENTE,
        /** El catalogo no lo ofrece: suspendido (RN-PRD-004) o en un estado que no es de venta. */
        NO_DISPONIBLE,
        /** No quedan unidades (RF-CAR-007, RF-PRD-003). */
        AGOTADO,
        /** No tiene precio en moneda real, y la tienda solo cobra en moneda real (RF-CAR-002, RN-PAG-001). */
        SIN_PRECIO_EN_MONEDA_REAL
    }

    private final Motivo motivo;

    public ProductoNoAgregableException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}

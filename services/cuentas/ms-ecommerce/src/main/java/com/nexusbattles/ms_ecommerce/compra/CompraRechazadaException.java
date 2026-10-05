package com.nexusbattles.ms_ecommerce.compra;

/**
 * La compra no se pudo hacer por un motivo que el jugador puede entender y,
 * a veces, arreglar. El motivo decide el codigo y el {@code type} de problem
 * details ({@code ManejadorDeErrores}); si ya habia una orden, viaja con ella
 * para que la respuesta diga cual y en que estado quedo.
 */
public class CompraRechazadaException extends RuntimeException {

    /** Por que, en el orden en que puede pasar. */
    public enum Motivo {
        /** 400 {@code clave-de-idempotencia-requerida}: falta o no tiene de 8 a 100 caracteres. */
        CLAVE_INVALIDA,
        /** 409 {@code clave-de-idempotencia-reutilizada}: la clave ya se uso con otra moneda. */
        CLAVE_REUTILIZADA,
        /** 503 {@code compra-no-disponible}: a la tienda le falta configuracion para completar una compra. */
        COMPRA_NO_DISPONIBLE,
        /** 400 {@code carrito-vacio}: no hay nada que se pueda comprar. */
        CARRITO_VACIO,
        /** 409 {@code compra-en-curso}: ese jugador ya tiene un pago en proceso. */
        COMPRA_EN_CURSO,
        /** 402 {@code pago-rechazado}: la pasarela se nego; orden RECHAZADA, carrito intacto. */
        PAGO_RECHAZADO,
        /** 503 {@code pasarela-no-disponible}: no se cobro; la orden sigue PENDIENTE y se reintenta con la misma clave. */
        PASARELA_NO_DISPONIBLE,
        /** 409 {@code compra-reembolsada}: se cobro, no se pudo entregar y se devolvio el dinero. */
        COMPRA_REEMBOLSADA,
        /** D-44 · 409 {@code producto-sin-precio-en-creditos}: algo del carrito no se vende en creditos; sin orden. */
        SIN_PRECIO_EN_CREDITOS,
        /** D-44 · 402 {@code saldo-insuficiente}: ms-finanzas no desconto; orden RECHAZADA, carrito intacto. */
        SALDO_INSUFICIENTE,
        /** D-44 · 503 {@code creditos-no-disponibles}: ms-finanzas no respondio; la orden sigue PENDIENTE. */
        CREDITOS_NO_DISPONIBLES
    }

    private final Motivo motivo;
    private final transient OrdenDto orden;

    public CompraRechazadaException(Motivo motivo, String mensaje) {
        this(motivo, mensaje, null);
    }

    public CompraRechazadaException(Motivo motivo, String mensaje, OrdenDto orden) {
        super(mensaje);
        this.motivo = motivo;
        this.orden = orden;
    }

    public Motivo motivo() {
        return motivo;
    }

    /** La orden que quedo, si llego a crearse; null si no. */
    public OrdenDto orden() {
        return orden;
    }
}

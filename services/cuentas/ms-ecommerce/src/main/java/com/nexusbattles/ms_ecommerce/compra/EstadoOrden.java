package com.nexusbattles.ms_ecommerce.compra;

import java.util.Set;

/**
 * Los estados de una orden de compra (contrato 1.3.0/1.4.0).
 *
 * <pre>
 * PENDIENTE ──cobro aprobado──▶ COBRADA ──entregada──▶ ENTREGADA ──asiento y correo──▶ COMPLETA
 *     │                            │
 *     ├──cobro rechazado──▶ RECHAZADA
 *     │                            └──reserva o entrega rechazadas──▶ COMPENSACION_PENDIENTE ──reembolso──▶ REEMBOLSADA
 *     └──caduca sin reintento──▶ RECHAZADA
 * </pre>
 *
 * <p>Cada transicion se persiste antes del paso siguiente: una orden que se
 * queda a medias (un servicio que no responde, un reinicio) sigue desde donde
 * quedo, con las mismas claves.
 */
public enum EstadoOrden {
    /** Creada, sin cobrar: la pasarela aun no respondio (o no respondio nunca). */
    PENDIENTE,
    /** Cobrada: falta reservar el tiraje y entregar. Lo comprado ya salio del carrito. */
    COBRADA,
    /** Los productos estan en el inventario: falta el asiento en ms-finanzas o el correo. */
    ENTREGADA,
    /** Todo hecho. */
    COMPLETA,
    /** La pasarela no cobro. Nada que deshacer; el carrito sigue igual. */
    RECHAZADA,
    /** Se cobro y no se pudo entregar: falta devolver el dinero. */
    COMPENSACION_PENDIENTE,
    /** Se cobro, no se pudo entregar y se devolvio el dinero. */
    REEMBOLSADA;

    /** Estados de los que la orden ya no se mueve. */
    public static final Set<EstadoOrden> FINALES = Set.of(COMPLETA, RECHAZADA, REEMBOLSADA);

    public boolean esFinal() {
        return FINALES.contains(this);
    }

    /** Estados en los que el dinero ya se cobro y la compra sigue en pie. */
    public boolean cobrada() {
        return this == COBRADA || this == ENTREGADA || this == COMPLETA;
    }
}

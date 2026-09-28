package com.nexusbattles.ms_subastas.reglas;

/**
 * Que pasa con un producto ganado que nadie recogio en los 7 dias de 7.7.9.
 *
 * <p>El documento fija el plazo («Tiempo limite para reclamar (7 dias)») pero
 * no la consecuencia. Es una decision del Product Owner, con parametro en
 * admin-parametros ({@code subastas.pendientes.al-vencer}) y respaldo por
 * variable de entorno ({@code SUBASTAS_PENDIENTES_AL_VENCER}).
 */
public enum PoliticaAlVencer {

    /**
     * PROVISIONAL mientras el PO no decida. El producto queda disponible en el
     * inventario del ganador como si lo hubiera recogido. Se eligio como
     * provisional porque es la unica opcion que no le quita a nadie algo que
     * ya pago: el ganador pago y el vendedor cobro al cerrar la subasta.
     */
    ENTREGAR,

    /**
     * El producto vuelve al inventario del vendedor, sin mover creditos (la
     * venta ya se cobro). Es la convencion de «correo no recogido vuelve al
     * remitente» de otros juegos; el ganador pierde el producto pagado. Solo
     * con decision expresa del PO.
     */
    DEVOLVER_AL_VENDEDOR
}

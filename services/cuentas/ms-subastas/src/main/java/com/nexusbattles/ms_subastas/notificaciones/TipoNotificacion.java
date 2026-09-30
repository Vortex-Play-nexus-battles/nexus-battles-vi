package com.nexusbattles.ms_subastas.notificaciones;

/**
 * Los avisos de subastas: los dos que exigia HU-SUB-004 mas los de 7.7.8 del
 * documento del curso (B8).
 *
 * <p>Cada tipo dice su titulo —lo que el jugador lee en la bandeja— y si
 * ademas va por correo ({@code POST /correos/subasta}). Por correo va lo que
 * 7.7.8 y el encargo de B8 piden que llegue aunque el jugador no este
 * conectado: nueva puja (al vendedor), puja superada, victoria, cierre por
 * vencimiento, cancelacion y recordatorio. El resto (confirmaciones de lo que
 * el propio jugador acaba de hacer, cambios en subastas seguidas, y la
 * confirmacion de creditos al vendedor, que coincide con el correo de la
 * venta) va solo a la bandeja, para no convertir cada puja en un correo.
 *
 * <p>Anadir uno exige anadirlo tambien a la restriccion
 * {@code chk_notificaciones_tipo} con una migracion.
 */
public enum TipoNotificacion {

    /** HU-SUB-004 criterio 2 y 7.7.6: la compra inmediata de otro cerro la subasta en la que pujabas. */
    SUBASTA_CERRADA_POR_COMPRA_INMEDIATA("La subasta se cerró por una compra inmediata", false),

    /** HU-SUB-004 criterio 4: la puja automatica «se detiene notificando al alcanzar el limite». */
    LIMITE_AUTOMATICO_ALCANZADO("Tu puja automática se detuvo", false),

    /** La puja automatica se detiene porque el saldo disponible no cubre la siguiente oferta. */
    AUTOMATICA_SIN_SALDO("Tu puja automática se detuvo por falta de saldo", false),

    /** 7.7.8 vendedor: «Confirmacion de publicacion exitosa de la subasta». */
    SUBASTA_PUBLICADA("Tu subasta quedó publicada", false),

    /** 7.7.8 vendedor: «Alerta de nueva puja recibida con monto y usuario». */
    NUEVA_PUJA("Nueva puja en tu subasta", true),

    /** 7.7.8 comprador: «Confirmacion de puja registrada». */
    PUJA_REGISTRADA("Puja registrada", false),

    /** 7.7.6 y 7.7.8 comprador: «Alerta cuando otra puja supera la oferta actual». */
    PUJA_SUPERADA("Te superaron en una subasta", true),

    /** 7.7.7 y 7.7.8 comprador: «Aviso de victoria en subasta». */
    SUBASTA_GANADA("¡Ganaste la subasta!", true),

    /**
     * 7.7.7 y 7.7.8 vendedor: finalizacion con su resultado. La confirmacion de
     * los creditos recibidos va aparte, en {@link #CREDITOS_RECIBIDOS}.
     */
    SUBASTA_VENDIDA("Vendiste tu subasta", true),

    /** 7.7.7 «Subasta sin ofertas: notificacion al vendedor del resultado». */
    SUBASTA_SIN_OFERTAS("Tu subasta terminó sin ofertas", true),

    /** 7.7.7 «Notificacion a los demas participantes informando del resultado». */
    SUBASTA_FINALIZADA("Terminó una subasta en la que participaste", false),

    /** 7.7.8 vendedor: «Notificacion de compra inmediata ejecutada». */
    COMPRA_INMEDIATA_EJECUTADA("Compraron tu subasta de forma inmediata", true),

    /** 7.7.8 comprador: «Notificacion de compra inmediata exitosa» y producto agregado al inventario. */
    COMPRA_INMEDIATA_EXITOSA("Compra inmediata realizada", false),

    /** 7.7.10: la cancelacion, con la penalizacion cobrada. */
    SUBASTA_CANCELADA("Subasta cancelada", true),

    /** 7.7.9 lista de seguimiento: «Notificaciones cuando hay cambios en estas subastas». */
    CAMBIO_EN_SUBASTA_SEGUIDA("Novedades en una subasta que sigues", false),

    /** 7.7.8: «Aviso 1 hora antes de finalizar» y «Recordatorio de subastas guardadas en lista de seguimiento». */
    RECORDATORIO_CIERRE("Una subasta cierra en menos de 1 hora", true),

    /** 7.7.8 comprador: «Confirmacion de producto agregado al inventario», al recogerlo. */
    PRODUCTO_RECOGIDO("Producto agregado a tu inventario", false),

    /** 7.7.9: vencieron los 7 dias para recoger; se aplico la politica del parametro. */
    PENDIENTE_VENCIDO("Venció el plazo para recoger tu producto", true),

    /** El producto que nadie recogio volvio al vendedor (politica DEVOLVER_AL_VENDEDOR). */
    PRODUCTO_DEVUELTO("Te devolvieron un producto que nadie recogió", false),

    /**
     * 7.7.8 vendedor: «Confirmacion de transferencia de creditos recibidos»
     * (RF-NOT-003). Sale al cerrar con ganador y en la compra inmediata,
     * despues de que ms-finanzas movio los creditos al vendedor. Solo a la
     * bandeja: el correo de la venta sale en el mismo instante y ya dice que
     * los creditos estan en su saldo.
     */
    CREDITOS_RECIBIDOS("Créditos recibidos", false);

    private final String titulo;
    private final boolean conCorreo;

    TipoNotificacion(String titulo, boolean conCorreo) {
        this.titulo = titulo;
        this.conCorreo = conCorreo;
    }

    /**
     * Titulo que vera el jugador en su bandeja. Vive aqui y no en el drenador
     * porque es parte de lo que significa el aviso: anadir un tipo obliga a
     * decidir como se llama, en vez de dejar que salga un enum en mayusculas.
     */
    public String getTitulo() {
        return titulo;
    }

    /** Si ademas de la bandeja va por correo. */
    public boolean conCorreo() {
        return conCorreo;
    }
}

package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

/** Una operacion sobre sanciones que las reglas no admiten; el motivo dice cual. */
public class SancionRechazada extends RuntimeException {

    /** Por que se rechaza, para que el cliente distinga sin leer el texto. */
    public enum Motivo {
        /** El rol del actor no alcanza (moderador baneando, jugador sancionando...). */
        PERMISO_INSUFICIENTE,
        /** Datos invalidos: motivo vacio, duracion fuera de rango, sin confirmacion. */
        SOLICITUD_INVALIDA,
        /** El usuario ya esta baneado: no se le emite nada mas. */
        USUARIO_BANEADO,
        /** No hay tal sancion o apelacion. */
        NO_ENCONTRADA,
        /** La apelacion no procede: fuera de plazo, ya abierta, sancion no vigente, no es suya. */
        APELACION_NO_PROCEDE,
        /** La apelacion ya esta resuelta. */
        APELACION_RESUELTA
    }

    private final Motivo motivo;

    public SancionRechazada(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}

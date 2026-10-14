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
        APELACION_RESUELTA,
        /** Levantar una sancion que ya no esta vigente (revertida o suspension vencida). */
        SANCION_NO_VIGENTE,
        /** La cuenta afectada tiene un rol igual o superior al de quien actua (7.3.1). */
        JERARQUIA_INSUFICIENTE,
        /** ms-identidad no dijo el rol de la cuenta: sin saberlo no se sanciona (fail-closed). */
        IDENTIDAD_NO_DISPONIBLE,
        /** SA-4 (§7.3.2): un baneo no se levanta; solo lo revierte una apelacion favorable. */
        BANEO_SOLO_POR_APELACION
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

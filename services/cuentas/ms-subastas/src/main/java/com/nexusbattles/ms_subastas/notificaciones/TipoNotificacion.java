package com.nexusbattles.ms_subastas.notificaciones;

/**
 * Solo los dos avisos que HU-SUB-004 exige explicitamente. No se anaden tipos
 * "por si acaso": cada uno obliga al microservicio de notificaciones a saber
 * como renderizarlo.
 */
public enum TipoNotificacion {

    /** Criterio 2: la compra inmediata cierra la subasta "notificando a quienes hubieran pujado". */
    SUBASTA_CERRADA_POR_COMPRA_INMEDIATA("La subasta se cerro"),

    /** Criterio 4: la puja automatica "se detiene notificando al alcanzar el limite". */
    LIMITE_AUTOMATICO_ALCANZADO("Tu puja automatica se detuvo");

    private final String titulo;

    TipoNotificacion(String titulo) {
        this.titulo = titulo;
    }

    /**
     * Titulo que vera el jugador en su bandeja. Vive aqui y no en el drenador
     * porque es parte de lo que significa el aviso: anadir un tipo obliga a
     * decidir como se llama, en vez de dejar que salga un enum en mayusculas.
     */
    public String getTitulo() {
        return titulo;
    }
}

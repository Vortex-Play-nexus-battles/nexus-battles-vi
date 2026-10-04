package com.nexusbattles.ms_finanzas.partidas;

/**
 * Puerto hacia el inventario del jugador: la única vía por la que un cofre da
 * propiedad de un producto — {@code POST /api/v1/inventario/entregas} de
 * inventario.yaml 1.4.0, origen {@code COFRE} (cofres.yaml 1.1.0, B7).
 */
public interface InventarioDeCofres {

    /**
     * Entrega el contenido del cofre al inventario de su dueño.
     *
     * <p>Idempotente por {@link CofreEntregado#claveDeEntrega()}: la misma clave
     * con el mismo cuerpo devuelve la entrega original, así que repetir la
     * llamada nunca duplica el premio.
     *
     * @return el identificador de la entrega en el inventario
     * @throws EntregaNoRealizada si inventario no la hizo, por el motivo que sea
     */
    String entregar(CofreEntregado cofre);

    /** La entrega no se hizo. El cofre sigue pendiente y se reintentará. */
    class EntregaNoRealizada extends RuntimeException {

        public EntregaNoRealizada(String motivo) {
            super(motivo);
        }

        public EntregaNoRealizada(String motivo, Throwable causa) {
            super(motivo, causa);
        }
    }
}

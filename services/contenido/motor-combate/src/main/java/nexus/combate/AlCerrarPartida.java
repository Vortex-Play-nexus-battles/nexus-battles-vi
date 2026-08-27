package nexus.combate;

/** Puerto para recompensas, historial y demás reacciones al cierre. */
@FunctionalInterface
public interface AlCerrarPartida {

    void procesar(ResultadoPartida resultado);
}

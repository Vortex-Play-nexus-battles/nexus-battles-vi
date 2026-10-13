package nexus.misiones.dominio.simulacion;

/**
 * El motor rechazo la accion (409 {@code accion-no-permitida}): esta en carga,
 * se aprende en otro nivel, no es de este heroe... No se aplico nada. No es un
 * fallo del servicio: la IA prueba la siguiente opcion de su rotacion y, al
 * final, el ataque basico.
 */
public class AccionNoPermitida extends RuntimeException {

    private final String motivo;

    /**
     * @param motivo  uno de {@code MotivoDeRechazo} del motor (EN_CARGA, BLOQUEADA_POR_NIVEL...); puede ser nulo
     * @param detalle el texto apto para el jugador
     */
    public AccionNoPermitida(String motivo, String detalle) {
        super(detalle == null || detalle.isBlank() ? "El motor no permite esa accion." : detalle);
        this.motivo = motivo == null || motivo.isBlank() ? "DESCONOCIDO" : motivo;
    }

    public String motivo() {
        return motivo;
    }
}

package nexus.misiones.ia;

/**
 * El modelo de IA no se puede usar (no esta, esta danado, o se hizo con otra definicion de caracteristicas). No
 * es un error del servicio: quien lo carga lo registra y sigue con la regla de siempre.
 */
public class ModeloNoUtilizable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModeloNoUtilizable(String mensaje) {
        super(mensaje);
    }

    public ModeloNoUtilizable(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

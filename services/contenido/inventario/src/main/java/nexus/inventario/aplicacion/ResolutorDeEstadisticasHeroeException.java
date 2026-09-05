package nexus.inventario.aplicacion;

public class ResolutorDeEstadisticasHeroeException extends RuntimeException {

    public ResolutorDeEstadisticasHeroeException(String mensaje) {
        super(mensaje);
    }

    public ResolutorDeEstadisticasHeroeException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

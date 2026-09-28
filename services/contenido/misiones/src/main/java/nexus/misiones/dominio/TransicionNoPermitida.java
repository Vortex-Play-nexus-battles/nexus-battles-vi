package nexus.misiones.dominio;

/**
 * Una transicion de estado que la seccion 7.8.7 no admite (cancelar una
 * mision que ya termino, por ejemplo). Se rechaza y el estado se conserva
 * (HU-MIS-010).
 */
public class TransicionNoPermitida extends ReglaDeMisionIncumplida {

    public TransicionNoPermitida(String detalle) {
        super(detalle);
    }
}

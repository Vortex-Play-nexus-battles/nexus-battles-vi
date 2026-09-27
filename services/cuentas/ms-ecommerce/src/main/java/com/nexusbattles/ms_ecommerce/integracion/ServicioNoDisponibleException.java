package com.nexusbattles.ms_ecommerce.integracion;

/**
 * Otro servicio no respondio como se esperaba y el fallo es de disponibilidad,
 * no de negocio: no respondio a tiempo, rechazo la conexion, contesto un 5xx
 * o un 429, o no acepto la credencial de la tienda (401/403: un despliegue a
 * medias, no una decision sobre la compra).
 *
 * <p>Quien la recibe no decide nada sobre la orden: la deja en su estado y la
 * vuelve a intentar mas tarde con las mismas claves de idempotencia. Lo que
 * SI es una decision del otro servicio (agotado, suspendido, inexistente) no
 * viaja con esta excepcion sino como resultado del cliente.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    private final String servicio;

    public ServicioNoDisponibleException(String servicio, String mensaje) {
        super(mensaje);
        this.servicio = servicio;
    }

    public ServicioNoDisponibleException(String servicio, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.servicio = servicio;
    }

    /** El servicio que fallo, para la bitacora y el ultimo error de la orden. */
    public String servicio() {
        return servicio;
    }
}

package com.nexusbattles.ms_ecommerce.seguridad;

/**
 * El despliegue no le dio a la tienda un {@code client_id}: la vitrina, el
 * carrito y la lista de deseos siguen funcionando, pero nada que exija hablar
 * con otro servicio en nombre de la tienda (comprar, marcar lo propio).
 *
 * <p>La compra lo comprueba ANTES de cobrar y responde 503
 * {@code compra-no-disponible}: descubrirlo despues de cobrar dejaria la orden
 * pagada y sin entregar hasta que alguien arreglara el despliegue.
 */
public final class SinCredencialDeServicio implements CredencialDeServicio {

    @Override
    public String portador() {
        throw new CredencialDeServicioNoDisponibleException(
                "La tienda no tiene credencial de servicio: falta DIRECTORIO_ACTIVO_CLIENT_ID (ADR-005).");
    }

    @Override
    public boolean configurada() {
        return false;
    }
}

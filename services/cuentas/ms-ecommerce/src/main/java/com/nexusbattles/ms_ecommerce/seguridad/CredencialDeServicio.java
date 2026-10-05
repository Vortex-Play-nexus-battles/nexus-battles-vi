package com.nexusbattles.ms_ecommerce.seguridad;

/**
 * La credencial con la que la tienda se identifica ante otro servicio
 * (ADR-001 via el emisor de ADR-005): la de ms-ecommerce, nunca la de un
 * jugador. El jugador afectado viaja en la peticion como dato de negocio
 * ({@code uid} de una entrega, {@code X-User-Name} de una consulta de
 * inventario), no dentro de esta credencial.
 *
 * <p>Es la version Maven de {@code TokenDeServicio} de
 * shared/libs/plataforma-seguridad, que este modulo no puede enlazar.
 */
public interface CredencialDeServicio {

    /**
     * Un token de acceso vigente, listo para {@code Authorization: Bearer}.
     *
     * @throws CredencialDeServicioNoDisponibleException si no hay credencial
     *         configurada o el emisor no la entrega
     */
    String portador();

    /** Si el despliegue le dio a la tienda un {@code client_id} con el que pedirla. */
    boolean configurada();
}

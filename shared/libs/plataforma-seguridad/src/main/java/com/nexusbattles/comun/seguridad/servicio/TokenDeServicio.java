package com.nexusbattles.comun.seguridad.servicio;

/**
 * Credencial con la que un microservicio se identifica ante otro — ADR-001.
 *
 * <p>Es la identidad del <b>servicio que llama</b> (el actor), nunca la de un
 * jugador. El jugador sobre cuyo recurso se opera viaja en la peticion como
 * dato de negocio ({@code propietarioUid}), no dentro de esta credencial.
 *
 * <p>No depende de ninguna peticion HTTP en curso ni de un
 * {@code SecurityContext}: se puede pedir desde un {@code @Scheduled}, una
 * compensacion o un reintento, exactamente igual que desde un controlador.
 *
 * <p>Es una interfaz y no una clase para que los dobles de prueba de los
 * servicios consumidores sean un lambda: {@code () -> "token-de-prueba"}.
 */
@FunctionalInterface
public interface TokenDeServicio {

    /**
     * Devuelve un token de acceso vigente. Lo pide al emisor la primera vez y lo
     * renueva solo cuando esta por caducar; entre medias devuelve el mismo.
     *
     * @return el valor del token, listo para {@code Authorization: Bearer}
     * @throws CredencialDeServicioNoDisponible si el emisor no responde o
     *         rechaza las credenciales del servicio
     */
    String portador();
}

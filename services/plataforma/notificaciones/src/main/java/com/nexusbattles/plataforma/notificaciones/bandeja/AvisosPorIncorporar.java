package com.nexusbattles.plataforma.notificaciones.bandeja;

/**
 * Avisos que llegan de otra fuente y se incorporan a la bandeja justo antes de
 * entregarle a una sesion lo pendiente — HU-NOT-001 (#532).
 *
 * <p>Hoy la unica fuente son los cambios del catalogo
 * ({@code catalogo.ImportadorDeAvisosDelCatalogo}). Vive aqui, del lado de la
 * bandeja, para que la dependencia vaya en un solo sentido: el catalogo
 * conoce la bandeja, la bandeja solo conoce este contrato.
 *
 * <p><b>Nunca lanza.</b> Lo que pase al traer los avisos (la otra fuente no
 * responde, la base falla) se anota y la entrega de pendientes sigue igual,
 * sin esos avisos: degradacion controlada (HU-DIS-003, riesgo #7 del Charter).
 */
@FunctionalInterface
public interface AvisosPorIncorporar {

    /**
     * Trae a la bandeja del jugador los avisos que le falten de esta fuente.
     *
     * @param usuarioId el {@code uid} del jugador dueno de la bandeja
     */
    void incorporar(String usuarioId);

    /** Ninguna fuente: la entrega de pendientes queda exactamente como antes de HU-NOT-001. */
    static AvisosPorIncorporar ninguno() {
        return usuarioId -> {
        };
    }
}

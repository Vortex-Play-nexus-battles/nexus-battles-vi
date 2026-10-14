package com.nexusbattles.plataforma.notificaciones.bandeja;

/**
 * Avisos que llegan de otra fuente y se incorporan a la bandeja justo antes de
 * entregarle a una sesion lo pendiente — HU-NOT-001 (#532).
 *
 * <p>Las fuentes son los cambios del catalogo
 * ({@code catalogo.ImportadorDeAvisosDelCatalogo}, HU-NOT-001) y los cambios
 * en las politicas de uso ({@code politicas.ImportadorDeCambiosDePolitica},
 * SA-11); {@link FuentesDeAvisos} las junta. Vive aqui, del lado de la
 * bandeja, para que la dependencia vaya en un solo sentido: cada fuente
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

    /**
     * Todas las fuentes, una detras de otra y en ese orden. Si una rompe su
     * contrato y lanza, las demas se intentan igual y la entrega no se entera.
     */
    static AvisosPorIncorporar todas(java.util.List<AvisosPorIncorporar> fuentes) {
        java.util.List<AvisosPorIncorporar> copia = java.util.List.copyOf(fuentes);
        return usuarioId -> {
            for (AvisosPorIncorporar fuente : copia) {
                try {
                    fuente.incorporar(usuarioId);
                } catch (RuntimeException rompioElContrato) {
                    org.slf4j.LoggerFactory.getLogger(AvisosPorIncorporar.class).atWarn()
                            .addKeyValue("evento", "fuente-de-avisos-fallida")
                            .addKeyValue("usuarioId", usuarioId)
                            .addKeyValue("causa", rompioElContrato.getClass().getSimpleName())
                            .log("Una fuente de avisos fallo; la entrega sigue con las demas");
                }
            }
        };
    }
}

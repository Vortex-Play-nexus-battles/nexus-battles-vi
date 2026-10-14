package nexus.auditoria;

/**
 * Lo que hizo un administrador sobre el catálogo — SA-18, §7.2.5 («registro de
 * auditoría de todas las operaciones realizadas») y §7.3.5.
 *
 * <p>Es el vocabulario del catálogo, no el de la bitácora central: la
 * traducción a sus tipos ({@code CREACION}, {@code SUSPENSION}…) la hace
 * ms-cumplimiento al importar, que es quien conoce los suyos. Un tipo nuevo
 * aquí no rompe la importación: allí cae en {@code OTRO} hasta que se
 * traduzca.
 */
public enum TipoOperacion {
        ALTA_PRODUCTO,
        MODIFICACION_PRODUCTO,
        CAMBIO_DE_BALANCE,
        IMPORTACION_DE_DISENO,
        SUSPENSION_PRODUCTO,
        REACTIVACION_PRODUCTO,
        REVERSION_PRODUCTO,
        ALTA_RECURSO,
        RETIRO_RECURSO,
        ALTA_BANNER,
        MODIFICACION_BANNER,
        RETIRO_BANNER
}

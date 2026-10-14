package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

/**
 * No se pudo leer el catálogo en esta ronda — SA-18: productos no responde,
 * respondió algo que no se entiende, o el emisor no entregó la credencial de
 * esta bitácora. No se inventa nada: la ronda siguiente lo vuelve a pedir
 * desde el mismo punto, y lo que ya se importó sigue en la bitácora.
 */
public class CatalogoNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CatalogoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

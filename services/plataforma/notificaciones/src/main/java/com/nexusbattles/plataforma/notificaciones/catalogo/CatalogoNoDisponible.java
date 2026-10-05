package com.nexusbattles.plataforma.notificaciones.catalogo;

/**
 * Productos no entrego un lote de cambios: no respondio a tiempo, rechazo la
 * credencial o respondio algo que no se entiende. Es de disponibilidad, no de
 * negocio: quien llama degrada (la entrega de pendientes sigue sin estos
 * avisos), nunca inventa un lote vacio.
 */
public class CatalogoNoDisponible extends RuntimeException {

    public CatalogoNoDisponible(String mensaje) {
        super(mensaje);
    }
}

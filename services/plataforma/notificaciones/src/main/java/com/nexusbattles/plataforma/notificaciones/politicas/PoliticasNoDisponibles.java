package com.nexusbattles.plataforma.notificaciones.politicas;

/**
 * Admin-parametros no entrego un lote de cambios en las politicas: no
 * respondio a tiempo o respondio algo que no se entiende. Es de
 * disponibilidad: quien llama degrada (la entrega de pendientes sigue sin
 * estos avisos), nunca inventa un lote vacio.
 */
public class PoliticasNoDisponibles extends RuntimeException {

    public PoliticasNoDisponibles(String mensaje) {
        super(mensaje);
    }
}

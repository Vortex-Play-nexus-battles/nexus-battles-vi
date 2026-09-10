package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;

/**
 * Puerto de comprobacion de salud de un servicio del bloque.
 *
 * <p>Se inyecta como interfaz para que el monitor se pueda probar sin red y
 * para que el dia que el equipo acuerde una herramienta externa de monitoreo
 * (subtarea SCRUM-1141) se cambie el adaptador y no la logica.
 */
@FunctionalInterface
public interface SondaDeSalud {

    /**
     * @param servicio nombre declarado en la configuracion
     * @param url endpoint de salud del servicio
     * @param instante momento en que se comprueba
     */
    Comprobacion comprobar(String servicio, String url, Instant instante);
}

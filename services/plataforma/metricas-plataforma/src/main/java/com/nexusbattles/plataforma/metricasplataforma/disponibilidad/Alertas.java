package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

/**
 * Puerto de alerta cuando la disponibilidad cae bajo el umbral (CP-03) o
 * cuando un servicio vital deja de responder (subtarea SCRUM-1143).
 *
 * <p>Es un puerto y no una llamada directa al modulo de notificaciones porque
 * el destino todavia no esta acordado: correo, el canal de notificaciones o
 * una herramienta externa. Hoy la implementacion escribe en la bitacora
 * estructurada, que es lo que la regla 6 de plataforma ya exige, y el dia que
 * se decida el destino se cambia el adaptador.
 */
public interface Alertas {

    /**
     * @param servicio servicio afectado
     * @param detalle motivo, tal como lo reporto la sonda
     */
    void servicioCaido(String servicio, String detalle);

    /**
     * @param porcentaje disponibilidad medida del bloque
     * @param umbral minimo acordado en DEC-01
     */
    void disponibilidadBajoUmbral(double porcentaje, double umbral);
}

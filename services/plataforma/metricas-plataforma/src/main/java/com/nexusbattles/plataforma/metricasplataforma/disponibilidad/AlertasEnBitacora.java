package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alertas por la bitacora estructurada a stdout (regla 6 de plataforma).
 *
 * <p>Es el destino provisional mientras el equipo acuerda el definitivo
 * (subtarea SCRUM-1143: «alertas criticas inmediatas a los desarrolladores»).
 * Se escribe a nivel ERROR para que el recolector de registros la separe del
 * ruido normal, y con campos fijos para poder filtrarla sin leer el texto.
 */
public class AlertasEnBitacora implements Alertas {

    private static final Logger BITACORA = LoggerFactory.getLogger(AlertasEnBitacora.class);

    @Override
    public void servicioCaido(String servicio, String detalle) {
        BITACORA.error(
                "alerta=servicio_caido servicio={} detalle={}", servicio, detalle);
    }

    @Override
    public void disponibilidadBajoUmbral(double porcentaje, double umbral) {
        BITACORA.error(
                "alerta=disponibilidad_bajo_umbral porcentaje={} umbral={}", porcentaje, umbral);
    }
}

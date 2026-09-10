package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servicios del bloque que se monitorean y umbral acordado (HU-DIS-001).
 *
 * <p>Regla 10 de plataforma: la configuracion llega por variable de entorno y
 * la plantilla vive en el repo. Los servicios NO estan escritos en el codigo:
 * el bloque crece (torneos, admin-parametros) y una lista compilada obligaria
 * a recompilar para medir un servicio nuevo.
 *
 * <p>Solo se declaran servicios del bloque. Los de los equipos socios se
 * quedan fuera a proposito: DEC-01 excluye sus caidas de la cifra.
 *
 * @param servicios nombre logico -> URL de su endpoint de salud
 * @param umbralPorcentaje minimo mensual acordado, 99.95 por DEC-01
 * @param intervaloMs cada cuanto se comprueba la salud
 */
@ConfigurationProperties(prefix = "disponibilidad")
public record ConfiguracionDeDisponibilidad(
        Map<String, String> servicios, double umbralPorcentaje, long intervaloMs) {

    public ConfiguracionDeDisponibilidad {
        servicios = servicios == null ? Map.of() : Map.copyOf(servicios);
        if (umbralPorcentaje <= 0 || umbralPorcentaje > 100) {
            throw new IllegalArgumentException(
                    "el umbral de disponibilidad debe estar entre 0 y 100, y llego " + umbralPorcentaje);
        }
    }

    public List<String> nombres() {
        return List.copyOf(servicios.keySet());
    }
}

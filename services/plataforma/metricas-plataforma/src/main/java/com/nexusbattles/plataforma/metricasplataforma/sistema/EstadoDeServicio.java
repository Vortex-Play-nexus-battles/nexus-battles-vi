package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.time.Instant;

/**
 * Como esta un servicio ahora mismo, en las palabras que usa la consola.
 *
 * @param servicio  nombre del catalogo de despliegue
 * @param estado    OPERATIVO | CAIDO | NO_DESPLEGADO
 * @param detalle   por que; en un fallo, el motivo recortado
 * @param instante  cuando se comprobo
 */
public record EstadoDeServicio(String servicio, String estado, String detalle, Instant instante) {

    public static final String OPERATIVO = "OPERATIVO";
    public static final String CAIDO = "CAIDO";
    public static final String NO_DESPLEGADO = "NO_DESPLEGADO";
}

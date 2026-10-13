package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.time.Instant;
import java.util.List;

/**
 * Lo que publica {@code GET /api/v1/admin/sistema/servicios}
 * (metricas-plataforma.yaml, esquema RespuestaDelSistema).
 *
 * @param servicios      uno por servicio, en el orden de la configuracion
 * @param total          cuantos hay en el catalogo; la consola lo ensena y
 *                       sin el tendria que sumarlo por su cuenta, que es
 *                       como acaban dos pantallas dando cifras distintas
 * @param operativos     cuantos responden UP
 * @param caidos         cuantos deberian responder y no lo hacen
 * @param lentos         cuantos conectaron y no contestaron a tiempo (1.9.0)
 * @param noDesplegados  cuantos estan fuera del host a proposito
 * @param noObservables  cuantos viven en otro host sin sonda configurada
 * @param instante       cuando se hizo la ronda de la que sale esta respuesta
 * @param desdeCache     true si reutiliza una ronda anterior aun vigente (1.9.0)
 */
public record RespuestaDelSistema(
        List<EstadoDeServicio> servicios,
        int total,
        int operativos,
        int caidos,
        int lentos,
        int noDesplegados,
        int noObservables,
        Instant instante,
        boolean desdeCache) {

    /** Una ronda recien hecha. */
    static RespuestaDelSistema de(List<EstadoDeServicio> estados, Instant instante) {
        return new RespuestaDelSistema(
                List.copyOf(estados),
                estados.size(),
                cuantos(estados, EstadoDeServicio.OPERATIVO),
                cuantos(estados, EstadoDeServicio.CAIDO),
                cuantos(estados, EstadoDeServicio.LENTO),
                cuantos(estados, EstadoDeServicio.NO_DESPLEGADO),
                cuantos(estados, EstadoDeServicio.NO_OBSERVABLE),
                instante,
                false);
    }

    /** La misma ronda, servida otra vez dentro de su vigencia: lo dice. */
    RespuestaDelSistema reutilizada() {
        return new RespuestaDelSistema(servicios, total, operativos, caidos, lentos, noDesplegados, noObservables,
                instante, true);
    }

    private static int cuantos(List<EstadoDeServicio> estados, String estado) {
        return (int) estados.stream().filter(e -> estado.equals(e.estado())).count();
    }
}

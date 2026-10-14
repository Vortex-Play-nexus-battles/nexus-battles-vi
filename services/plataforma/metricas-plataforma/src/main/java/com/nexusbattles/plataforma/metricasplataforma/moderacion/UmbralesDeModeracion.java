package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Los dos umbrales de «alta frecuencia» del tablero de moderacion (§7.3.8,
 * RF-MET-001, D-25), leidos del catalogo de admin-parametros con su variable
 * de entorno como respaldo.
 *
 * <p><b>Por que del catalogo.</b> Hasta SA-4 el de sanciones solo se leia de
 * {@code METRICAS_UMBRAL_SANCIONES_POR_DIA} al arrancar: el panel de
 * Parametros decia «sin valor» mientras la alerta se evaluaba con 3, y cambiar
 * la cifra desde el panel no hacia nada. Ahora manda el catalogo —lo que ve y
 * cambia la administracion, versionado y auditado— y la variable solo entra si
 * el catalogo no responde o no tiene valor ({@link LectorDeParametros}: nunca
 * lanza, nunca inventa).
 *
 * <p>Un umbral sin valor, que no es un entero o que no es positivo es
 * «sin umbral»: se publican los conteos y no se evalua ninguna alerta. Nunca se
 * pone una cifra que nadie dio.
 */
public class UmbralesDeModeracion {

    static final String SANCIONES = "metricas.umbral-sanciones-por-dia";
    static final String REPORTES = "metricas.umbral-reportes-por-dia";

    private static final Logger BITACORA = LoggerFactory.getLogger(UmbralesDeModeracion.class);

    private final LectorDeParametros catalogo;
    private final Integer respaldoSanciones;
    private final Integer respaldoReportes;

    /**
     * @param catalogo          lector del catalogo (o {@link LectorDeParametros#soloRespaldo()} sin catalogo)
     * @param respaldoSanciones {@code METRICAS_UMBRAL_SANCIONES_POR_DIA}, o {@code null}
     * @param respaldoReportes  {@code METRICAS_UMBRAL_REPORTES_POR_DIA}, o {@code null}
     */
    public UmbralesDeModeracion(LectorDeParametros catalogo, Integer respaldoSanciones, Integer respaldoReportes) {
        this.catalogo = Objects.requireNonNull(catalogo, "hace falta el lector del catalogo");
        this.respaldoSanciones = respaldoSanciones;
        this.respaldoReportes = respaldoReportes;
    }

    /** Los umbrales vigentes; {@code null} donde no hay ninguno. */
    public record Umbrales(Integer sancionesPorDia, Integer reportesPorDia) {

        public static final Umbrales NINGUNO = new Umbrales(null, null);

        public Umbrales {
            sancionesPorDia = positivo(sancionesPorDia);
            reportesPorDia = positivo(reportesPorDia);
        }

        private static Integer positivo(Integer umbral) {
            return umbral != null && umbral > 0 ? umbral : null;
        }
    }

    public Umbrales vigentes() {
        return new Umbrales(leer(SANCIONES, respaldoSanciones), leer(REPORTES, respaldoReportes));
    }

    private Integer leer(String clave, Integer respaldo) {
        Optional<String> delCatalogo = catalogo.texto(clave);
        if (delCatalogo.isEmpty()) {
            return respaldo;
        }
        try {
            return Integer.valueOf(delCatalogo.get().strip());
        } catch (NumberFormatException noEsUnEntero) {
            BITACORA.warn("El parametro {} vale '{}', que no es un entero; se usa el respaldo {}",
                    clave, delCatalogo.get(), respaldo);
            return respaldo;
        }
    }
}

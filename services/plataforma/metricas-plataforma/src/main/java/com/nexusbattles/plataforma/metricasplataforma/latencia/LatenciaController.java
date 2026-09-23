package com.nexusbattles.plataforma.metricasplataforma.latencia;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.plataforma.observabilidad.InformeDeLatencia;
import com.nexusbattles.plataforma.observabilidad.ObjetivoDeLatencia;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import com.nexusbattles.plataforma.observabilidad.RegistroDeLatencia;

/**
 * Informe de latencia extremo a extremo (HU-REN-001, CA-02).
 *
 * <p>Contrato: contracts/openapi/metricas-plataforma.yaml
 *
 * <ul>
 *   <li>{@code GET /api/v1/latencia/informe} — el informe en JSON, para el
 *       tablero y para cualquier consumidor automatico.
 *   <li>{@code GET /api/v1/latencia/informe/texto} — el mismo informe redactado,
 *       que es lo que se pega como evidencia en el informe de avance sin tener
 *       que maquetar nada.
 * </ul>
 *
 * <p>Son dos rutas y no una sola con negociacion por {@code Accept}: con las dos
 * variantes en la misma ruta, una peticion sin cabecera {@code Accept} —un
 * navegador, un {@code curl} suelto— deja a Spring eligiendo entre dos
 * representaciones igual de especificas.
 *
 * <p>El registro y las propiedades los inyecta la autoconfiguracion de
 * {@code shared/libs/plataforma-observabilidad}: este modulo no instancia el
 * filtro ni lo configura, igual que no lo hace ninguno de los otros diecinueve.
 */
@RestController
@RequestMapping("/api/v1/latencia")
public class LatenciaController {

    private final RegistroDeLatencia registro;
    private final PropiedadesDeLatencia propiedades;

    public LatenciaController(RegistroDeLatencia registro, PropiedadesDeLatencia propiedades) {
        this.registro = registro;
        this.propiedades = propiedades;
    }

    @GetMapping(value = "/informe", produces = MediaType.APPLICATION_JSON_VALUE)
    public InformeResponse informe() {
        ObjetivoDeLatencia objetivo = objetivoVigente();
        InformeDeLatencia informe = registro.informe(objetivo, propiedades.getOperacionesEnInforme());

        List<OperacionResponse> operaciones = informe.operacionesMasLentas().stream()
                .map(operacion -> new OperacionResponse(
                        operacion.metodo(),
                        operacion.ruta(),
                        operacion.muestras(),
                        operacion.percentilMs()))
                .toList();

        // HU-REN-002: lecturas y escrituras separadas. Con un percentil unico,
        // los listados —que son muchisimos mas— entierran a las pujas.
        List<TipoResponse> porTipo = registro.resumenPorTipo(objetivo).stream()
                .map(resumen -> new TipoResponse(
                        resumen.etiqueta(),
                        resumen.muestras(),
                        resumen.percentilMs(),
                        resumen.maximoMs()))
                .toList();

        return new InformeResponse(
                registro.servicio(),
                informe.muestras(),
                objetivo.nombre(),
                objetivo.objetivoMs(),
                informe.percentilMs(),
                informe.maximoMs(),
                informe.cumple(),
                informe.sinDatos(),
                porTipo,
                operaciones);
    }

    /** El mismo informe, ya redactado, para anexarlo como evidencia. */
    @GetMapping(value = "/informe/texto", produces = MediaType.TEXT_PLAIN_VALUE)
    public String informeLegible() {
        ObjetivoDeLatencia objetivo = objetivoVigente();
        InformeDeLatencia informe = registro.informe(objetivo, propiedades.getOperacionesEnInforme());

        StringBuilder texto = new StringBuilder();
        texto.append("Informe de latencia extremo a extremo — RNF-REN-001\n")
                .append("Servicio: ").append(registro.servicio()).append('\n')
                .append("Objetivo: ").append(objetivo.nombre())
                .append(" <= ").append(objetivo.objetivoMs()).append(" ms\n")
                .append("Peticiones medidas: ").append(informe.muestras()).append('\n');

        if (informe.sinDatos()) {
            // No medir no es lo mismo que cumplir. Decirlo aqui evita que el
            // informe se lea como un verde.
            texto.append("\nSIN DATOS: el servicio no ha atendido peticiones desde el ultimo arranque.\n")
                    .append("No se puede afirmar que cumpla; tampoco que incumpla.\n");
            return texto.toString();
        }

        texto.append(objetivo.nombre()).append(": ").append(informe.percentilMs()).append(" ms\n")
                .append("Maximo: ").append(informe.maximoMs()).append(" ms\n")
                .append("Resultado: ").append(informe.cumple() ? "CUMPLE" : "NO CUMPLE").append("\n\n");

        // HU-REN-002: el desglose va ANTES de las operaciones porque es lo
        // primero que hay que mirar. Un percentil global bueno con las
        // escrituras en rojo es un informe que enganna.
        texto.append("Por tipo de operacion (").append(objetivo.nombre()).append("):\n");
        for (InformeDeLatencia.ResumenPorTipo tipo : registro.resumenPorTipo(objetivo)) {
            texto.append("  ").append(tipo.etiqueta()).append(": ")
                    .append(tipo.percentilMs()).append(" ms")
                    .append("  (maximo ").append(tipo.maximoMs()).append(" ms, ")
                    .append(tipo.muestras()).append(" muestras)\n");
        }

        texto.append("\nOperaciones mas lentas (").append(objetivo.nombre()).append("):\n");

        for (InformeDeLatencia.Operacion operacion : informe.operacionesMasLentas()) {
            texto.append("  ").append(operacion.percentilMs()).append(" ms  ")
                    .append(operacion.metodo()).append(' ').append(operacion.ruta())
                    .append("  (").append(operacion.muestras()).append(" muestras)\n");
        }

        return texto.toString();
    }

    /**
     * El objetivo contra el que se evalua: 500 ms de RNF-REN-001 al percentil
     * de ADR-006 (p95), o lo que diga {@code LATENCIA_PERCENTIL}.
     *
     * <p>Hasta septiembre de 2026 este metodo lanzaba un 409 permanente
     * («el Product Owner no ha aprobado el percentil»). ADR-006 revisa esa
     * lectura de CA-03: ningun documento del proyecto fija el percentil y
     * elegirlo es una convencion de medicion, no una decision de producto.
     * Mantener la espera dejaba RNF-REN-001 sin poder evaluarse nunca.
     */
    private ObjetivoDeLatencia objetivoVigente() {
        return propiedades.objetivo();
    }

    public record InformeResponse(
            String servicio,
            long muestras,
            String percentil,
            long objetivoMs,
            long percentilMs,
            long maximoMs,
            boolean cumple,
            boolean sinDatos,
            List<TipoResponse> porTipo,
            List<OperacionResponse> operacionesMasLentas) {}

    /** HU-REN-002: lecturas y escrituras medidas por separado. */
    public record TipoResponse(String tipo, long muestras, long percentilMs, long maximoMs) {}

    public record OperacionResponse(String metodo, String ruta, long muestras, long percentilMs) {}
}

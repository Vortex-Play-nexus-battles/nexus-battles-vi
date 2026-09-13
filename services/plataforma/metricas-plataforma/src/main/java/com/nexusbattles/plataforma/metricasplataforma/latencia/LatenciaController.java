package com.nexusbattles.plataforma.metricasplataforma.latencia;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

        return new InformeResponse(
                registro.servicio(),
                informe.muestras(),
                objetivo.nombre(),
                objetivo.objetivoMs(),
                informe.percentilMs(),
                informe.maximoMs(),
                informe.cumple(),
                informe.sinDatos(),
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
                .append("Resultado: ").append(informe.cumple() ? "CUMPLE" : "NO CUMPLE").append("\n\n")
                .append("Operaciones mas lentas (").append(objetivo.nombre()).append("):\n");

        for (InformeDeLatencia.Operacion operacion : informe.operacionesMasLentas()) {
            texto.append("  ").append(operacion.percentilMs()).append(" ms  ")
                    .append(operacion.metodo()).append(' ').append(operacion.ruta())
                    .append("  (").append(operacion.muestras()).append(" muestras)\n");
        }

        return texto.toString();
    }

    /**
     * El objetivo vigente, o un fallo explicito si el Product Owner no aprobo el
     * percentil.
     *
     * <p>Aqui es donde vive la consecuencia de CA-03. La <b>medicion</b> corre
     * igualmente en los veinte modulos desde el Sprint 1 —no depende de ninguna
     * decision pendiente—, pero <b>evaluar</b> el requisito exige un percentil
     * acordado. Devolver un informe con un p95 elegido por quien programa seria
     * fabricar la decision y presentarla al cliente como si estuviera tomada.
     */
    private ObjetivoDeLatencia objetivoVigente() {
        return propiedades.objetivo().orElseThrow(PercentilNoAcordado::new);
    }

    /**
     * 409 y no 500: el servicio funciona y esta midiendo. Lo que falta es una
     * decision de negocio, y el mensaje dice cual y donde se configura.
     */
    @ExceptionHandler(PercentilNoAcordado.class)
    ProblemDetail percentilNoAcordado(PercentilNoAcordado e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Percentil de evaluacion no acordado");
        problema.setType(URI.create("https://nexusbattles.local/errores/percentil-no-acordado"));
        problema.setProperty("variable", "LATENCIA_PERCENTIL");
        problema.setProperty("criterio", "HU-REN-001 CA-03");
        problema.setProperty("muestrasAcumuladas", registro.cuantasMuestras());
        return problema;
    }

    /** Configuracion incompleta a proposito: falta la aprobacion del PO. */
    static class PercentilNoAcordado extends RuntimeException {
        PercentilNoAcordado() {
            super("El percentil de evaluacion no esta configurado. RNF-REN-001 se evalua en p95 o en p99 "
                    + "y CA-03 de HU-REN-001 exige que el Product Owner lo apruebe por escrito. "
                    + "Cuando lo apruebe, se configura en LATENCIA_PERCENTIL: no hace falta recompilar. "
                    + "La medicion sigue activa mientras tanto y las muestras se estan acumulando.");
        }
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
            List<OperacionResponse> operacionesMasLentas) {}

    public record OperacionResponse(String metodo, String ruta, long muestras, long percentilMs) {}
}

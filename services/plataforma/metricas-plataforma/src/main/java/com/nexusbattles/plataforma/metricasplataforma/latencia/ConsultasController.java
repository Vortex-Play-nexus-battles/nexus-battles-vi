package com.nexusbattles.plataforma.metricasplataforma.latencia;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.plataforma.observabilidad.InformeDeConsultas;
import com.nexusbattles.plataforma.observabilidad.MuestraDeConsulta;
import com.nexusbattles.plataforma.observabilidad.ObjetivoDeLatencia;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import com.nexusbattles.plataforma.observabilidad.RegistroDeConsultas;

/**
 * Informe de las consultas a la base de datos (HU-REN-003).
 *
 * <p>Contrato: contracts/openapi/metricas-plataforma.yaml
 *
 * <ul>
 *   <li>{@code GET /api/v1/consultas/informe} — CA-01: el tiempo de respuesta
 *       de las consultas frente al objetivo de latencia.
 *   <li>{@code GET /api/v1/consultas/lentas} — CA-03: las consultas que
 *       superaron el umbral, que son las que hay que optimizar.
 * </ul>
 *
 * <p>Las muestras las recoge el envoltorio de {@code DataSource} de
 * {@code shared/libs/plataforma-observabilidad}, que llega a los veinte modulos
 * desde las convenciones de Gradle. Ningun servicio instrumenta sus
 * repositorios a mano.
 */
@RestController
@RequestMapping("/api/v1/consultas")
public class ConsultasController {

    private final RegistroDeConsultas registro;
    private final PropiedadesDeLatencia propiedades;

    public ConsultasController(RegistroDeConsultas registro, PropiedadesDeLatencia propiedades) {
        this.registro = registro;
        this.propiedades = propiedades;
    }

    @GetMapping(value = "/informe", produces = MediaType.APPLICATION_JSON_VALUE)
    public InformeResponse informe() {
        ObjetivoDeLatencia objetivo = objetivoVigente();
        InformeDeConsultas informe =
                registro.informe(objetivo, propiedades.getConsultas().getSentenciasEnInforme());

        List<SentenciaResponse> sentencias = informe.sentenciasMasLentas().stream()
                .map(sentencia -> new SentenciaResponse(
                        sentencia.sentencia(),
                        sentencia.muestras(),
                        sentencia.percentilMs(),
                        sentencia.maximoMs()))
                .toList();

        return new InformeResponse(
                informe.servicio(),
                informe.muestras(),
                objetivo.nombre(),
                objetivo.objetivoMs(),
                informe.percentilMs(),
                informe.maximoMs(),
                informe.umbralLentaMs(),
                informe.cuantasLentas(),
                informe.cumple(),
                informe.sinDatos(),
                sentencias);
    }

    /**
     * CA-03: el registro de consultas lentas.
     *
     * <p>No necesita el percentil acordado por el Product Owner —marcar una
     * consulta lenta es comparar contra un umbral, no evaluar un percentil—,
     * asi que esta ruta responde aunque CA-03 de HU-REN-001 siga pendiente.
     */
    @GetMapping(value = "/lentas", produces = MediaType.APPLICATION_JSON_VALUE)
    public LentasResponse lentas() {
        List<ConsultaLentaResponse> lentas = registro.consultasLentas().stream()
                .map(muestra -> new ConsultaLentaResponse(
                        muestra.sentencia(), muestra.duracionMs(), muestra.instante(), muestra.fallo()))
                .toList();

        return new LentasResponse(
                registro.servicio(), registro.umbralLentaMs(), registro.totalLentas(), lentas);
    }

    private ObjetivoDeLatencia objetivoVigente() {
        return propiedades.objetivo().orElseThrow(PercentilNoAcordado::new);
    }

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

    /** Mismo motivo que en el informe de latencia: falta la decision del PO. */
    static class PercentilNoAcordado extends RuntimeException {
        PercentilNoAcordado() {
            super("El percentil de evaluacion no esta configurado. Se comparte con RNF-REN-001 y "
                    + "CA-03 de HU-REN-001 exige que el Product Owner lo apruebe por escrito. "
                    + "Cuando lo apruebe, se configura en LATENCIA_PERCENTIL: no hace falta recompilar. "
                    + "El registro de consultas lentas (/api/v1/consultas/lentas) si responde mientras tanto, "
                    + "porque marcar una consulta lenta no depende del percentil.");
        }
    }

    public record InformeResponse(
            String servicio,
            long muestras,
            String percentil,
            long objetivoMs,
            long percentilMs,
            long maximoMs,
            long umbralLentaMs,
            long cuantasLentas,
            boolean cumple,
            boolean sinDatos,
            List<SentenciaResponse> sentenciasMasLentas) {}

    public record SentenciaResponse(String sentencia, long muestras, long percentilMs, long maximoMs) {}

    public record LentasResponse(
            String servicio, long umbralLentaMs, long total, List<ConsultaLentaResponse> consultas) {}

    public record ConsultaLentaResponse(
            String sentencia, long duracionMs, Instant ejecutadaEn, boolean fallo) {}
}

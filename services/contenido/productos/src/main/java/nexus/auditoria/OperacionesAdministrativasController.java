package nexus.auditoria;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.nexusbattles.comun.seguridad.servicio.ActorDeServicio;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las operaciones administrativas del catálogo para la bitácora central —
 * SA-18, productos.yaml 1.12.0, §7.3.5.
 *
 * <p>ms-cumplimiento las importa con su credencial de servicio (ADR-005) y las
 * guarda en su registro inmutable, el que consulta y exporta el Super
 * Administrador. El sentido de la llamada es ese —la bitácora lee del
 * catálogo— porque productos vive en el host de contenido y el emisor de
 * credenciales en el de plataforma: una credencial de productos tendría que
 * compartirse entre los dos hosts (un secreto más en GitHub), mientras que la
 * de ms-cumplimiento nace en el mismo host que su emisor.
 *
 * <p>Solo un servicio (la regla del rol está en {@code SeguridadConfig}) y,
 * de los servicios, solo los de {@code productos.auditoria.lectores}: cada
 * operación lleva el {@code uid} de quien actuó y la IP desde donde lo hizo.
 */
@RestController
@RequestMapping("/api/v1/productos/auditoria")
public class OperacionesAdministrativasController {

        private final RegistroDeOperaciones registro;
        private final Set<String> lectores;

        public OperacionesAdministrativasController(
                        RegistroDeOperaciones registro,
                        @Value("${productos.auditoria.lectores:ms-cumplimiento}") String lectores) {
                this.registro = registro;
                this.lectores = Arrays.stream(lectores.split(","))
                                .map(String::strip)
                                .filter(lector -> !lector.isEmpty())
                                .collect(Collectors.toUnmodifiableSet());
        }

        /**
         * Las operaciones de {@code (desde, ahora]}, de la más antigua a la más
         * reciente; sin {@code desde}, desde la primera. Los límites de
         * {@code limite} los comprueba Bean Validation (400 con Problem
         * Details).
         */
        @GetMapping("/operaciones")
        public LoteResponse consultar(
                        Authentication autenticacion,
                        @RequestParam(name = "desde", required = false) Instant desde,
                        @RequestParam(name = "limite", defaultValue = "100")
                        @Min(value = 1, message = "limite debe estar entre 1 y 200")
                        @Max(value = 200, message = "limite debe estar entre 1 y 200")
                        int limite) {
                String servicio = ActorDeServicio.desde(autenticacion).orElse("");
                if (!lectores.contains(servicio)) {
                        throw new LectorNoAutorizadoException(
                                        "Las operaciones administrativas del catálogo solo las lee la bitácora central.");
                }
                LoteDeOperaciones lote = registro.consultarLote(desde, limite);
                return new LoteResponse(
                                lote.hasta(),
                                lote.completo(),
                                lote.operaciones().stream().map(OperacionResponse::desde).toList());
        }

        /** El esquema {@code LoteDeOperacionesAdministrativas} del contrato. */
        public record LoteResponse(Instant hasta, boolean completo, List<OperacionResponse> operaciones) {
        }

        /** El esquema {@code OperacionAdministrativa} del contrato. */
        public record OperacionResponse(
                        String id,
                        Instant ocurridaEn,
                        String autor,
                        TipoOperacion tipo,
                        ObjetoAuditado objeto,
                        String objetoId,
                        String nombre,
                        String valorAnterior,
                        String valorNuevo,
                        String motivo,
                        String descripcion,
                        String ipOrigen) {

                static OperacionResponse desde(OperacionAdministrativa operacion) {
                        return new OperacionResponse(
                                        operacion.id(),
                                        operacion.ocurridaEn(),
                                        operacion.autor(),
                                        operacion.tipo(),
                                        operacion.objeto(),
                                        operacion.objetoId(),
                                        operacion.nombre(),
                                        operacion.valorAnterior(),
                                        operacion.valorNuevo(),
                                        operacion.motivo(),
                                        operacion.descripcion(),
                                        operacion.ipOrigen());
                }
        }
}

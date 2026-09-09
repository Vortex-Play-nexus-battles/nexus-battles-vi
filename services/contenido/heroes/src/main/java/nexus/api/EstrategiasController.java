package nexus.api;

import java.util.List;
import java.util.Map;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.Decision;
import nexus.dominio.DecisorDeRotaciones;
import nexus.dominio.EstadoEnTurno;
import nexus.dominio.EstrategiaDeCombate;
import nexus.dominio.Heroe;
import nexus.dominio.Rotacion;
import nexus.dominio.RotacionInvalidaException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-SIM-001: validacion de la estrategia de combate (hasta tres rotaciones
 * de habilidades con prioridad, seccion 7.8.5) como servicio sin estado. El
 * configurador de estrategia (RF-MIS-38) la consulta antes de guardar y el
 * modulo de misiones, dueno de la persistencia (RNF-16), la reutiliza.
 * Contrato en contracts/openapi/heroes.yaml.
 */
@RestController
@RequestMapping("/api/v1/estrategias")
public class EstrategiasController {

    private final CatalogoDeHeroes catalogo;

    public EstrategiasController(CatalogoDeHeroes catalogo) {
        this.catalogo = catalogo;
    }

    @PostMapping("/validacion")
    public Veredicto validar(@RequestBody SolicitudDeEstrategia solicitud) {
        int nivel = solicitud.nivel() == null ? Heroe.NIVEL_MINIMO : solicitud.nivel();
        Heroe heroe = Heroe.deNivel(catalogo.fichaDe(solicitud.heroe()), nivel);
        List<List<String>> secuencias = solicitud.rotaciones() == null
                ? List.of()
                : solicitud.rotaciones().stream().map(RotacionSolicitada::pasos).toList();
        try {
            EstrategiaDeCombate estrategia = EstrategiaDeCombate.configurar(heroe, secuencias);
            return new Veredicto(
                    true, null, heroe.prototipo().nombre(), heroe.nivel(),
                    estrategia.rotaciones().stream().map(RotacionVista::de).toList(),
                    estrategia.habilidadesValidas(),
                    estrategia.esPorDefecto(), estrategia.comportamientoPorDefecto());
        } catch (RotacionInvalidaException e) {
            // Un rechazo de la regla no es un error de la peticion: responde el
            // veredicto con el motivo apto para el jugador y las habilidades validas
            // (ERS CU-61 E1), nunca un codigo pelado.
            return new Veredicto(
                    false, e.getMessage(), heroe.prototipo().nombre(), heroe.nivel(),
                    null, e.habilidadesValidas(), false, EstrategiaDeCombate.ATAQUE_BASICO);
        }
    }

    /**
     * HU-SIM-002: la decision de la IA para el turno que empieza (seccion 7.8.5,
     * RF-MIS-13 a 16). Sin estado: la simulacion manda la estrategia y el estado
     * del heroe y recibe la accion, las razones y los cursores del turno
     * siguiente. Una estrategia invalida aqui es un error de la peticion (400):
     * debio validarse antes en /validacion.
     */
    @PostMapping("/decision")
    public DecisionVista decidir(@RequestBody SolicitudDeDecision solicitud) {
        int nivel = solicitud.nivel() == null ? Heroe.NIVEL_MINIMO : solicitud.nivel();
        Heroe heroe = Heroe.deNivel(catalogo.fichaDe(solicitud.heroe()), nivel);
        List<List<String>> secuencias = solicitud.rotaciones() == null
                ? List.of()
                : solicitud.rotaciones().stream().map(RotacionSolicitada::pasos).toList();
        EstrategiaDeCombate estrategia = EstrategiaDeCombate.configurar(heroe, secuencias);
        if (solicitud.estado() == null) {
            throw new IllegalArgumentException("La decisión necesita el estado del héroe en el turno.");
        }
        EstadoEnTurno estado = new EstadoEnTurno(
                solicitud.estado().turno(), solicitud.estado().poder(), solicitud.estado().vida(),
                solicitud.estado().turnoDeUltimoUso(), solicitud.estado().cursores());
        Decision d = DecisorDeRotaciones.decidir(estrategia, estado);
        return new DecisionVista(
                heroe.prototipo().nombre(), heroe.nivel(), estado.turno(),
                d.accion(), d.rotacion(), d.costoDePoder(),
                d.evaluaciones().stream()
                        .map(e -> new EvaluacionVista(e.rotacion(), e.paso(), e.viable(), e.razon()))
                        .toList(),
                d.cursoresSiguientes());
    }

    /** nivel ausente = 1 (el heroe recien adquirido). rotaciones ausente = sin rotaciones. */
    public record SolicitudDeEstrategia(String heroe, Integer nivel, List<RotacionSolicitada> rotaciones) {
    }

    public record SolicitudDeDecision(
            String heroe, Integer nivel, List<RotacionSolicitada> rotaciones, EstadoSolicitado estado) {
    }

    /** turnoDeUltimoUso y cursores ausentes = sin usos previos y todas las rotaciones en su primer paso. */
    public record EstadoSolicitado(
            int turno, int poder, int vida, Map<String, Integer> turnoDeUltimoUso, List<Integer> cursores) {
    }

    /** rotacion va ausente cuando la accion es el ataque basico de respaldo; razon, cuando la rotacion fue viable. */
    public record DecisionVista(
            String heroe, int nivel, int turno,
            String accion, Integer rotacion, int costoDePoder,
            List<EvaluacionVista> evaluaciones, List<Integer> cursoresSiguientes) {
    }

    public record EvaluacionVista(int rotacion, String paso, boolean viable, String razon) {
    }

    public record RotacionSolicitada(List<String> pasos) {
    }

    public record RotacionVista(String prioridad, List<String> pasos) {
        static RotacionVista de(Rotacion r) {
            String p = r.prioridad().name();
            return new RotacionVista(p.charAt(0) + p.substring(1).toLowerCase(java.util.Locale.ROOT), r.pasos());
        }
    }

    /** motivo y rotaciones van ausentes segun el caso (jackson non-null). */
    public record Veredicto(
            boolean valida,
            String motivo,
            String heroe,
            int nivel,
            List<RotacionVista> rotaciones,
            List<String> habilidadesValidas,
            boolean porDefecto,
            String comportamientoPorDefecto) {
    }
}

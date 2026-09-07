package nexus.api;

import java.util.List;
import nexus.dominio.CatalogoDeHeroes;
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

    /** nivel ausente = 1 (el heroe recien adquirido). rotaciones ausente = sin rotaciones. */
    public record SolicitudDeEstrategia(String heroe, Integer nivel, List<RotacionSolicitada> rotaciones) {
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

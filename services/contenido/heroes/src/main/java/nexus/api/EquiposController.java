package nexus.api;

import java.util.List;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.ComposicionDeEquipo;
import nexus.dominio.Prototipo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-JUE-009: validacion de la composicion de un equipo como servicio, para
 * que Jugar online (sala de batalla) y Torneo la consuman en vez de
 * reimplementar la regla del sanador unico (RC-08). Contrato en
 * contracts/openapi/heroes.yaml. No persiste nada.
 */
@RestController
@RequestMapping("/api/v1/equipos")
public class EquiposController {

    private final CatalogoDeHeroes catalogo;
    private final boolean sanadorEnIndividualPermitido;

    public EquiposController(
            CatalogoDeHeroes catalogo,
            @Value("${reglas.sanador-en-individual-permitido}") boolean sanadorEnIndividualPermitido) {
        this.catalogo = catalogo;
        this.sanadorEnIndividualPermitido = sanadorEnIndividualPermitido;
    }

    @PostMapping("/validacion")
    public Veredicto validar(@RequestBody SolicitudDeComposicion solicitud) {
        // Sin el campo "heroes" (o null) la solicitud es un equipo vacio: la regla del
        // dominio responde con su propio mensaje (400), no con un error de protocolo.
        List<Prototipo> miembros = solicitud.heroes() == null
                ? List.of()
                : solicitud.heroes().stream().map(catalogo::fichaDe).toList();
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(miembros, sanadorEnIndividualPermitido);
        return new Veredicto(v.valida(), v.motivo(), v.sanadores(), v.individual());
    }

    /** Nombres de los prototipos que forman el equipo (busqueda tolerante a tildes y mayusculas). */
    public record SolicitudDeComposicion(List<String> heroes) {
    }

    /** motivo va ausente cuando la composicion es valida (jackson non-null). */
    public record Veredicto(boolean valida, String motivo, int sanadores, boolean individual) {
    }
}

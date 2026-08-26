package nexus.api;

import java.util.List;
import nexus.dominio.Accion;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.Estadisticas;
import nexus.dominio.Prototipo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-HER-001: consulta del catalogo de heroes y de la ficha de un prototipo.
 * Contrato en contracts/openapi/heroes.yaml (regla de plataforma: contrato primero,
 * rutas bajo /api/v1).
 */
@RestController
@RequestMapping("/api/v1/heroes")
public class HeroesController {

    private final CatalogoDeHeroes catalogo;

    public HeroesController(CatalogoDeHeroes catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    public List<ResumenHeroe> listar() {
        return catalogo.listar().stream().map(ResumenHeroe::de).toList();
    }

    @GetMapping("/{nombre}")
    public FichaHeroe ficha(@PathVariable String nombre) {
        return FichaHeroe.de(catalogo.fichaDe(nombre));
    }

    /** Vista de lista: lo minimo para la seleccion (criterio 1 de HU-HER-001). */
    public record ResumenHeroe(String nombre, String tipo, boolean esSanador) {
        static ResumenHeroe de(Prototipo p) {
            return new ResumenHeroe(p.nombre(), p.tipo(), p.esSanador());
        }
    }

    /** Ficha completa (criterios 2 y 3): formulas en el texto exacto de la Tabla 6. */
    public record FichaHeroe(
            String nombre,
            String tipo,
            String descripcion,
            boolean esSanador,
            EstadisticasVista estadisticasNivel1,
            List<AccionVista> acciones) {

        static FichaHeroe de(Prototipo p) {
            return new FichaHeroe(
                    p.nombre(), p.tipo(), p.descripcion(), p.esSanador(),
                    EstadisticasVista.de(p.estadisticasNivel1()),
                    p.acciones().stream().map(AccionVista::de).toList());
        }
    }

    public record EstadisticasVista(int poder, int vida, int defensa, String ataque, String dano, String sanar) {
        static EstadisticasVista de(Estadisticas e) {
            return new EstadisticasVista(
                    e.poder(), e.vida(), e.defensa(),
                    e.ataque() == null ? null : e.ataque().texto(),
                    e.dano() == null ? null : e.dano().texto(),
                    e.sanar() == null ? null : e.sanar().texto());
        }
    }

    public record AccionVista(String nombre, String costo, String efecto) {
        static AccionVista de(Accion a) {
            String costo = a.cuestaTodoElPoder()
                    ? "Todos los puntos de poder"
                    : a.costoPuntos() + " puntos de poder";
            return new AccionVista(a.nombre(), costo, a.efecto());
        }
    }
}

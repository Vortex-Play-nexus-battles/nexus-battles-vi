package nexus.api;

import java.util.List;
import nexus.dominio.Accion;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.ControlDeRecarga;
import nexus.dominio.Epica;
import nexus.dominio.EpicasIniciales;
import nexus.dominio.Estadisticas;
import nexus.dominio.Heroe;
import nexus.dominio.Prototipo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-HER-001: consulta del catalogo de heroes y de la ficha de un prototipo, y
 * la vista del prototipo en un nivel dado (reglas de progresion como servicio).
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

    /**
     * Funcion pura de (prototipo, nivel): lo que el motor, las misiones y el
     * inventario necesitan saber de un heroe en un nivel, sin reimplementar las
     * reglas. No persiste nada; el estado del heroe lo guarda el inventario.
     */
    @GetMapping("/{nombre}/niveles/{nivel}")
    public VistaPorNivel vistaPorNivel(@PathVariable String nombre, @PathVariable int nivel) {
        return VistaPorNivel.de(Heroe.deNivel(catalogo.fichaDe(nombre), nivel));
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

    /**
     * El prototipo en un nivel: estadisticas escaladas (HU-HER-008), acciones
     * desbloqueadas 1/4/8 (RC-01), multiplicador de efecto (HU-HER-007),
     * experiencia para subir (HU-HER-003) y la epica afin (HU-HER-009/010).
     */
    public record VistaPorNivel(
            String nombre,
            String tipo,
            boolean esSanador,
            int nivel,
            EstadisticasVista estadisticas,
            List<AccionVista> accionesDisponibles,
            int multiplicadorDeEfecto,
            Double experienciaParaSubir,
            EpicaVista epica) {

        static VistaPorNivel de(Heroe heroe) {
            Prototipo p = heroe.prototipo();
            return new VistaPorNivel(
                    p.nombre(), p.tipo(), p.esSanador(), heroe.nivel(),
                    EstadisticasVista.de(heroe.estadisticasActuales()),
                    heroe.accionesDisponibles().stream().map(AccionVista::de).toList(),
                    heroe.multiplicadorDeEfecto(),
                    Heroe.experienciaParaSubirDesde(heroe.nivel()),
                    EpicaVista.de(EpicasIniciales.afinA(p.nombre()), p));
        }
    }

    /** La epica afin con los efectos que recibe este prototipo (Tabla 20). */
    public record EpicaVista(String nombre, String efectoGeneral, String efectoPotenciado, int turnosDeRecarga) {
        static EpicaVista de(Epica epica, Prototipo p) {
            Epica.Efectos efectos = epica.efectosPara(p);
            return new EpicaVista(
                    epica.nombre(), efectos.general(), efectos.potenciado(),
                    ControlDeRecarga.TURNOS_DE_RECARGA_EPICA);
        }
    }

    public record EstadisticasVista(
            int poder, int vida, int defensa,
            String ataque, String dano, String sanar,
            FormulaVista ataqueDetalle, FormulaVista danoDetalle, FormulaVista sanarDetalle) {
        static EstadisticasVista de(Estadisticas e) {
            return new EstadisticasVista(
                    e.poder(), e.vida(), e.defensa(),
                    e.ataque() == null ? null : e.ataque().texto(),
                    e.dano() == null ? null : e.dano().texto(),
                    e.sanar() == null ? null : e.sanar().texto(),
                    FormulaVista.de(e.ataque()),
                    FormulaVista.de(e.dano()),
                    FormulaVista.de(e.sanar()));
        }
    }

    /**
     * La formula como datos, pedida por el motor de combate (HU-JUE-003) para
     * calcular sin parsear el texto. El texto sigue siendo la presentacion.
     */
    public record FormulaVista(int base, int cantidadDados, int caras) {
        static FormulaVista de(nexus.dominio.Formula f) {
            return f == null ? null : new FormulaVista(f.base(), f.cantidadDados(), f.carasDado());
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

package nexus.misiones.aplicacion;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.RepositorioDeEstrategias;

/**
 * Las estrategias guardadas (7.8.12, «configuraciones de rotaciones
 * guardadas»). Se guardan ya validadas por heroes con el prototipo y el nivel
 * que el INVENTARIO dice que tiene el heroe: la pantalla puede preparar la de
 * un nivel mas alto, pero lo que se guarda es lo que vale hoy.
 */
public class GestionarEstrategias {

    private final RepositorioDeEstrategias estrategias;
    private final InventarioDeHeroes inventario;
    private final CatalogoDeProductos productos;
    private final ServicioDeHeroes heroes;
    private final Clock reloj;

    public GestionarEstrategias(RepositorioDeEstrategias estrategias, InventarioDeHeroes inventario,
                                CatalogoDeProductos productos, ServicioDeHeroes heroes, Clock reloj) {
        this.estrategias = Objects.requireNonNull(estrategias);
        this.inventario = Objects.requireNonNull(inventario);
        this.productos = Objects.requireNonNull(productos);
        this.heroes = Objects.requireNonNull(heroes);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** @throws EstrategiaNoGuardada si no hay (tambien si el heroe no es suyo) */
    public EstrategiaGuardada consultar(String jugadorUid, String heroeId) {
        return estrategias.buscar(jugadorUid, heroeId).orElseThrow(EstrategiaNoGuardada::new);
    }

    /**
     * @throws HeroeNoEncontrado  si el heroe no existe o no es suyo
     * @throws EstrategiaInvalida si heroes la rechaza
     */
    public EstrategiaGuardada guardar(String jugadorUid, String heroeId, List<List<String>> rotaciones) {
        InventarioDeHeroes.HeroeDelInventario heroe = inventario.consultar(jugadorUid, heroeId);
        if (!jugadorUid.equals(heroe.propietarioUid()) || !heroe.esHeroe()) {
            throw new HeroeNoEncontrado();
        }
        String prototipo = productos.prototipoDe(heroe.productoId());
        if (prototipo == null || prototipo.isBlank()) {
            throw new HeroeNoEncontrado();
        }
        int nivel = heroe.nivel() == null ? HeroeEnMision.NIVEL_MINIMO : heroe.nivel();
        ServicioDeHeroes.VeredictoDeEstrategia veredicto = heroes.validarEstrategia(prototipo, nivel,
                rotaciones == null ? List.of() : rotaciones);
        if (!veredicto.valida()) {
            throw new EstrategiaInvalida(veredicto.motivo(), veredicto.habilidadesValidas());
        }
        return estrategias.guardar(new EstrategiaGuardada(jugadorUid, heroeId, prototipo, nivel,
                veredicto.rotaciones() == null ? List.of() : veredicto.rotaciones(), reloj.instant()));
    }
}

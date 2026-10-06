package nexus.misiones.aplicacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.PerfilDeCombate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lo que el heroe lleva a la mision cuando se simula (HU-SIM-003): sus
 * estadisticas con el equipamiento aplicado, lo que lleva puesto y las epicas
 * del jugador, que es lo que pide el motor de combate para resolver al heroe
 * REAL y no al prototipo pelado.
 *
 * <p><b>Por que se lee al simular y no al matricular.</b> Desde que el heroe
 * sale a la mision su equipamiento no puede cambiar (7.8.10, y el inventario
 * responde 409 «Heroe en mision»), asi que lo que hay al vencer el plazo es lo
 * que habia al salir; y leerlo aqui hace que una ejecucion ya en curso, y
 * guardada antes de existir esto, se simule igual de bien, sin migrar nada. Si
 * el inventario o productos no responden, el fallo sube y la simulacion se
 * reintenta en la vuelta siguiente: el heroe nunca pelea a medias.
 *
 * <p><b>Que se hace con lo que falta.</b> Si el inventario ya no conoce al
 * heroe ({@link HeroeNoEncontrado}: se borro, o ya no es del jugador) no se
 * deja la mision sin simular, con el heroe bloqueado para siempre: pelea con lo
 * que el motor sabe de su prototipo y su nivel, sin equipo (y se anota). Un
 * producto que el catalogo ya no tiene no se manda: sin nombre no hay efecto
 * que aplicar.
 */
public class PerfilDeCombateDelHeroe {

    private static final Logger BITACORA = LoggerFactory.getLogger(PerfilDeCombateDelHeroe.class);

    /**
     * Motor-combate.yaml (1.3.0): hasta diez piezas de equipamiento y nueve epicas por
     * combatiente: las ocho de la Tabla 20 y «Velo de Sombras» (7.8.14).
     */
    static final int EQUIPAMIENTO_MAXIMO = 10;
    static final int EPICAS_MAXIMAS = 9;

    private final InventarioDeHeroes inventario;
    private final CatalogoDeProductos productos;
    private final ServicioDeHeroes heroes;

    public PerfilDeCombateDelHeroe(InventarioDeHeroes inventario, CatalogoDeProductos productos,
                                   ServicioDeHeroes heroes) {
        this.inventario = Objects.requireNonNull(inventario);
        this.productos = Objects.requireNonNull(productos);
        this.heroes = Objects.requireNonNull(heroes);
    }

    public PerfilDeCombate de(String jugadorUid, HeroeEnMision heroe) {
        EstadisticasDeCombate estadisticas = estadisticas(jugadorUid, heroe);

        InventarioDeHeroes.EquipoDelHeroe equipo;
        try {
            equipo = inventario.equipo(jugadorUid, heroe.id());
        } catch (HeroeNoEncontrado desconocido) {
            BITACORA.warn("El inventario ya no conoce al heroe {}: pelea sin equipo ni epicas", heroe.id());
            equipo = new InventarioDeHeroes.EquipoDelHeroe(List.of(), List.of());
        }

        Map<String, String> nombres = new HashMap<>();
        List<String> equipamiento = nombresDe(equipo.productosEquipados(), nombres, EQUIPAMIENTO_MAXIMO, false);
        List<String> epicas = nombresDe(equipo.productosDeEpicas(), nombres, EPICAS_MAXIMAS, true);
        return new PerfilDeCombate(estadisticas, equipamiento, epicas);
    }

    /**
     * Las del inventario (nivel del heroe, equipo aplicado). Las formulas de
     * ataque y dano son las que el motor usa para golpear: si el inventario no
     * las publica se toman las del catalogo en el nivel del heroe, y si en
     * ninguna parte hay, no se mandan estadisticas (el motor usara las suyas):
     * unas estadisticas sin formula de ataque no podrian golpear.
     */
    private EstadisticasDeCombate estadisticas(String jugadorUid, HeroeEnMision heroe) {
        InventarioDeHeroes.EstadisticasDelHeroe publicadas;
        try {
            publicadas = inventario.estadisticas(jugadorUid, heroe.id());
        } catch (HeroeNoEncontrado desconocido) {
            BITACORA.warn("El inventario ya no conoce al heroe {}: pelea con las estadisticas del catalogo",
                    heroe.id());
            return null;
        }
        if (publicadas.vida() < 1) {
            BITACORA.warn("El inventario publica vida {} para el heroe {}: pelea con las del catalogo",
                    publicadas.vida(), heroe.id());
            return null;
        }
        Formula ataque = publicadas.ataque();
        Formula dano = publicadas.dano();
        Formula sanar = publicadas.sanar();
        if (ataque == null && sanar == null) {
            ServicioDeHeroes.EstadisticasDeNivel delCatalogo = heroes.enNivel(heroe.prototipo(), heroe.nivel());
            ataque = delCatalogo.ataque();
            dano = delCatalogo.dano();
            sanar = delCatalogo.sanar();
            if (ataque == null && sanar == null) {
                BITACORA.warn("Ni el inventario ni heroes publican formulas para {} en nivel {}: pelea con las del motor",
                        heroe.prototipo(), heroe.nivel());
                return null;
            }
        }
        return new EstadisticasDeCombate(publicadas.poder(), publicadas.vida(), publicadas.defensa(), ataque, dano,
                sanar);
    }

    /** Los nombres de los productos, sin repetir llamadas, saltando los que el catalogo ya no tiene. */
    private List<String> nombresDe(List<String> productoIds, Map<String, String> yaPedidos, int maximo,
                                   boolean sinRepetir) {
        List<String> nombres = new ArrayList<>();
        for (String productoId : productoIds) {
            if (nombres.size() >= maximo) {
                break;
            }
            String nombre = nombreDe(productoId, yaPedidos);
            if (nombre == null || nombre.isBlank() || (sinRepetir && nombres.contains(nombre))) {
                continue;
            }
            nombres.add(nombre);
        }
        return nombres;
    }

    private String nombreDe(String productoId, Map<String, String> yaPedidos) {
        if (!yaPedidos.containsKey(productoId)) {
            yaPedidos.put(productoId, productos.nombreDe(productoId));
        }
        return yaPedidos.get(productoId);
    }
}

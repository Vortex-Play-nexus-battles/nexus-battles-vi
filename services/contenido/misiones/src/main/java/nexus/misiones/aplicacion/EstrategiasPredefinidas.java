package nexus.misiones.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import nexus.misiones.dominio.CatalogoDeEstrategiasDeEnemigos;
import nexus.misiones.dominio.EstrategiaPredefinida;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La estrategia de un enemigo cuya mision no trae las rotaciones escritas (HU-SIM-004, 7.8.6: «la IA controla a los
 * enemigos con estrategias predefinidas»): la predefinida de su prototipo y nivel y, si no hay una que sirva, la
 * heuristica de siempre ({@link RotacionesPorDefectoDeEnemigos}). La precedencia completa, de mayor a menor:
 *
 * <ol>
 *   <li>la rotacion que la mision trae escrita para ese enemigo (la decide {@code SimularEjecucion}, antes de llegar
 *       aqui);</li>
 *   <li>la estrategia predefinida del prototipo en el tramo de su nivel (1 a 3, 4 a 7, 8 en adelante);</li>
 *   <li>la heuristica por defecto.</li>
 * </ol>
 *
 * <p>Una predefinida se valida con la regla de heroes —el dueno de las habilidades validas— la primera vez que se usa
 * (heroes puede no estar levantado al arrancar; el catalogo ya la valido contra la Tabla 7 local). Si heroes la
 * rechaza, se anota en la bitacora, se recuerda que no vale y ese prototipo y tramo juegan la heuristica: un contenido
 * malo no es un error para el jugador ni detiene la simulacion. Si heroes no responde, el fallo sube y la simulacion
 * entera se reintenta en la vuelta siguiente, como con cualquier otra consulta a heroes.
 *
 * <p>Una estrategia vale igual en todo su tramo (las habilidades se desbloquean solo en los niveles 1, 4 y 8), asi que
 * se valida una vez por estrategia, con el nivel en que empieza, y no una vez por enemigo.
 */
public class EstrategiasPredefinidas implements EstrategiaDeEnemigos {

    private static final Logger BITACORA = LoggerFactory.getLogger(EstrategiasPredefinidas.class);

    private final CatalogoDeEstrategiasDeEnemigos catalogo;
    private final ServicioDeHeroes heroes;
    private final EstrategiaDeEnemigos heuristica;
    /** El veredicto de heroes sobre cada estrategia (por id): sus rotaciones con nombre exacto, o vacio si no vale. */
    private final Map<String, Optional<List<List<String>>>> veredictos = new ConcurrentHashMap<>();

    public EstrategiasPredefinidas(CatalogoDeEstrategiasDeEnemigos catalogo, ServicioDeHeroes heroes,
                                   EstrategiaDeEnemigos heuristica) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.heroes = Objects.requireNonNull(heroes);
        this.heuristica = Objects.requireNonNull(heuristica);
    }

    @Override
    public List<List<String>> porDefecto(String prototipo, int nivel) {
        return elegir(prototipo, nivel).rotaciones();
    }

    @Override
    public Elegida elegir(String prototipo, int nivel) {
        Optional<EstrategiaPredefinida> escrita = catalogo.para(prototipo, nivel);
        if (escrita.isPresent()) {
            Optional<List<List<String>>> aceptada = validada(escrita.get());
            if (aceptada.isPresent()) {
                return new Elegida(OrigenDeEstrategia.PREDEFINIDA, escrita.get().id(), aceptada.get());
            }
        }
        return heuristica.elegir(prototipo, nivel);
    }

    /** Las rotaciones que heroes acepta de esa estrategia, o vacio si las rechaza (recordado). */
    private Optional<List<List<String>>> validada(EstrategiaPredefinida estrategia) {
        Optional<List<List<String>>> recordada = veredictos.get(estrategia.id());
        if (recordada != null) {
            return recordada;
        }
        ServicioDeHeroes.VeredictoDeEstrategia veredicto = heroes.validarEstrategia(estrategia.prototipo(),
                estrategia.desdeNivel(), estrategia.rotaciones());
        Optional<List<List<String>>> resultado;
        if (veredicto.valida()) {
            List<List<String>> exactas = veredicto.rotaciones() == null || veredicto.rotaciones().isEmpty()
                    ? estrategia.rotaciones() : veredicto.rotaciones();
            resultado = Optional.of(exactas);
        } else {
            BITACORA.warn("Heroes rechazo la estrategia predefinida «{}» ({} desde el nivel {}) y ese prototipo y"
                            + " tramo juegan la heuristica por defecto: {}", estrategia.id(), estrategia.prototipo(),
                    estrategia.desdeNivel(), veredicto.motivo());
            resultado = Optional.empty();
        }
        veredictos.put(estrategia.id(), resultado);
        return resultado;
    }
}

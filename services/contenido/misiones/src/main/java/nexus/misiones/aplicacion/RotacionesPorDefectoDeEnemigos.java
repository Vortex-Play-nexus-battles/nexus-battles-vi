package nexus.misiones.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;

/**
 * La estrategia por defecto de un enemigo (7.8.6, «la IA controla a los
 * enemigos con estrategias predefinidas»): una rotacion por cada habilidad que
 * su prototipo tiene desbloqueada en su nivel (RC-01: 1, 4 y 8), la mas
 * avanzada primero, y el ataque basico de respaldo que ya trae el decisor. Es
 * lo que un jugador sin ideas pondria con el prototipo, y usa lo mismo que el
 * heroe: las habilidades validas las dice heroes, que es su dueno.
 *
 * <p>Provisional: el documento no escribe la estrategia de cada enemigo. Cuando
 * HU-SIM-004 las declare en las misiones, estas ganan y esto solo cubre lo que
 * no se haya escrito.
 *
 * <p>Las habilidades de un prototipo en un nivel son datos fijos del catalogo:
 * se preguntan una sola vez por prototipo y nivel y se recuerdan, igual que
 * hace el cliente de heroes con las estadisticas por nivel.
 */
public class RotacionesPorDefectoDeEnemigos implements EstrategiaDeEnemigos {

    /** 7.8.5: una estrategia admite hasta tres rotaciones. */
    static final int ROTACIONES_MAXIMAS = 3;

    private final ServicioDeHeroes heroes;
    private final Map<String, List<List<String>>> conocidas = new ConcurrentHashMap<>();

    public RotacionesPorDefectoDeEnemigos(ServicioDeHeroes heroes) {
        this.heroes = Objects.requireNonNull(heroes);
    }

    @Override
    public List<List<String>> porDefecto(String prototipo, int nivel) {
        String clave = prototipo + "@" + nivel;
        List<List<String>> guardada = conocidas.get(clave);
        if (guardada != null) {
            return guardada;
        }
        List<List<String>> calculada = calcular(prototipo, nivel);
        conocidas.put(clave, calculada);
        return calculada;
    }

    private List<List<String>> calcular(String prototipo, int nivel) {
        ServicioDeHeroes.VeredictoDeEstrategia veredicto = heroes.validarEstrategia(prototipo, nivel, List.of());
        if (!veredicto.valida() || veredicto.habilidadesValidas() == null) {
            return List.of();
        }
        List<String> especiales = new ArrayList<>(veredicto.habilidadesValidas().stream()
                .filter(h -> !DecisionDeTurno.ATAQUE_BASICO.equals(h))
                .toList());
        java.util.Collections.reverse(especiales);
        return especiales.stream().limit(ROTACIONES_MAXIMAS).map(List::of).toList();
    }
}

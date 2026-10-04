package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nexus.misiones.aplicacion.ServicioDeHeroes;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link ServicioDeHeroes} contra el servicio de heroes (heroes.yaml 1.1.0): las
 * reglas del juego como servicio. Misiones no reimplementa ni la validacion de
 * la estrategia, ni la IA de cada turno, ni las tablas de progresion.
 *
 * <p>Las dos lecturas que son funciones puras del catalogo —la vista de un
 * prototipo en un nivel y la experiencia por enemigo segun el dado— se guardan
 * en memoria: son datos del producto, iguales para todos, y una mision de 19
 * combates las pediria cientos de veces.
 */
public class ClienteHeroes implements ServicioDeHeroes {

    static final String DEPENDENCIA = "heroes";

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;
    private final Map<String, EstadisticasDeNivel> vistas = new ConcurrentHashMap<>();
    private final Map<Integer, Double> experienciaPorDado = new ConcurrentHashMap<>();

    public ClienteHeroes(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public VeredictoDeEstrategia validarEstrategia(String prototipo, int nivel, List<List<String>> rotaciones) {
        SolicitudDeEstrategia solicitud = new SolicitudDeEstrategia(prototipo, nivel, comoRotaciones(rotaciones));
        Contestacion<Veredicto> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/api/v1/estrategias/validacion")
                .contentType(MediaType.APPLICATION_JSON)
                .body(solicitud)
                .retrieve()
                .body(Veredicto.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        Veredicto v = c.cuerpo();
        List<List<String>> validadas = v.rotaciones() == null ? List.of()
                : v.rotaciones().stream().map(r -> List.copyOf(r.pasos())).toList();
        return new VeredictoDeEstrategia(v.valida(), v.motivo(), validadas,
                v.habilidadesValidas() == null ? List.of() : v.habilidadesValidas());
    }

    @Override
    public VeredictoDeComposicion validarIndividual(String prototipo) {
        Contestacion<Composicion> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/api/v1/equipos/validacion")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("heroes", List.of(prototipo)))
                .retrieve()
                .body(Composicion.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        return new VeredictoDeComposicion(c.cuerpo().valida(), c.cuerpo().motivo());
    }

    @Override
    public EstadisticasDeNivel enNivel(String prototipo, int nivel) {
        String clave = prototipo + "@" + nivel;
        EstadisticasDeNivel guardada = vistas.get(clave);
        if (guardada != null) {
            return guardada;
        }
        Contestacion<Vista> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/heroes/{nombre}/niveles/{nivel}", prototipo, nivel)
                .retrieve()
                .body(Vista.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        Stats s = c.cuerpo().estadisticas();
        EstadisticasDeNivel vista = new EstadisticasDeNivel(s.poder(), s.vida(), s.defensa(),
                s.ataqueDetalle() == null ? null : s.ataqueDetalle().aDominio(),
                s.danoDetalle() == null ? null : s.danoDetalle().aDominio(),
                s.sanarDetalle() == null ? null : s.sanarDetalle().aDominio());
        vistas.put(clave, vista);
        return vista;
    }

    /**
     * {@code POST /api/v1/estrategias/decision}. Sin rotaciones no se pregunta:
     * el contrato fija que una estrategia ausente o vacia es «ataque basico
     * siempre» (heroes.yaml, SolicitudDeDecision), y un heroe sin estrategia
     * pediria lo mismo en cada uno de sus turnos.
     */
    @Override
    public DecisionDeTurno decidir(TurnoParaDecidir turno) {
        if (turno.rotaciones().isEmpty()) {
            return new DecisionDeTurno(DecisionDeTurno.ATAQUE_BASICO, 0, List.of());
        }
        // Heroes no admite mas poder que el maximo de su catalogo en ese nivel
        // (400 «poder fuera de rango»), y el heroe real puede traer mas por su
        // equipo: se acota. El motor de combate es quien lleva el poder de verdad.
        int poder = Math.min(turno.poder(), enNivel(turno.prototipo(), turno.nivel()).poder());
        SolicitudDeDecision solicitud = new SolicitudDeDecision(turno.prototipo(), turno.nivel(),
                comoRotaciones(turno.rotaciones()),
                new EstadoEnTurno(turno.turno(), poder, turno.vida(), turno.turnoDeUltimoUso(),
                        turno.cursores().isEmpty() ? null : turno.cursores()));
        Contestacion<Decision> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/api/v1/estrategias/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .body(solicitud)
                .retrieve()
                .body(Decision.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        Decision d = c.cuerpo();
        return new DecisionDeTurno(d.accion(), d.costoDePoder(), d.cursoresSiguientes());
    }

    @Override
    public double porEnemigoDerrotado(int dado) {
        Double guardada = experienciaPorDado.get(dado);
        if (guardada != null) {
            return guardada;
        }
        Contestacion<Experiencia> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/progresion/experiencia-por-enemigo/{dado}", dado)
                .retrieve()
                .body(Experiencia.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        experienciaPorDado.put(dado, c.cuerpo().puntos());
        return c.cuerpo().puntos();
    }

    private static List<RotacionSolicitada> comoRotaciones(List<List<String>> rotaciones) {
        return rotaciones == null ? List.of() : rotaciones.stream().map(RotacionSolicitada::new).toList();
    }

    record SolicitudDeEstrategia(String heroe, int nivel, List<RotacionSolicitada> rotaciones) {
    }

    record RotacionSolicitada(List<String> pasos) {
    }

    record SolicitudDeDecision(String heroe, int nivel, List<RotacionSolicitada> rotaciones, EstadoEnTurno estado) {
    }

    record EstadoEnTurno(int turno, int poder, int vida, Map<String, Integer> turnoDeUltimoUso,
                         List<Integer> cursores) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Veredicto(boolean valida, String motivo, List<Rotacion> rotaciones, List<String> habilidadesValidas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rotacion(String prioridad, List<String> pasos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Composicion(boolean valida, String motivo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Vista(String nombre, int nivel, Stats estadisticas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stats(int poder, int vida, int defensa, FormulaVista ataqueDetalle, FormulaVista danoDetalle,
                 FormulaVista sanarDetalle) {
    }

    /** {@code FormulaDetalle} de heroes.yaml: base + cantidadDados dados de N caras. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FormulaVista(int base, int cantidadDados, int caras) {

        Formula aDominio() {
            return new Formula(base, cantidadDados, caras);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Decision(String accion, int costoDePoder, List<Integer> cursoresSiguientes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Experiencia(int dado, double puntos) {
    }
}

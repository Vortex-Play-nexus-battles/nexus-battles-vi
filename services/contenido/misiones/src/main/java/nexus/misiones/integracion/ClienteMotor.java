package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nexus.misiones.aplicacion.RechazoDelServicio;
import nexus.misiones.dominio.simulacion.AccionNoPermitida;
import nexus.misiones.dominio.simulacion.Combatiente;
import nexus.misiones.dominio.simulacion.DetalleDeAtaque;
import nexus.misiones.dominio.simulacion.EfectoActivo;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.dominio.simulacion.ResultadoDeAccion;
import nexus.misiones.dominio.simulacion.ResultadoDeTurno;
import nexus.misiones.dominio.simulacion.Suceso;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link MotorDeCombate} contra el motor de combate
 * ({@code POST /api/v1/combate/turnos} y {@code POST /api/v1/combate/acciones},
 * motor-combate.yaml 1.2.0). «Motor de combate identico al de batallas en
 * linea» (7.8.12): el mismo contrato, el mismo servicio y la misma credencial
 * de servicio que usa salas-partidas; la mision ya no resuelve golpes sueltos
 * con la formula del prototipo (el {@code /ataques} de 1.1.0, que queda solo
 * por compatibilidad).
 *
 * <p>El motor no guarda nada entre llamadas: cada peticion lleva el estado de
 * todos los combatientes y la respuesta trae el de todos. Lo que la respuesta
 * no repite del combatiente (su prototipo, su nivel, sus estadisticas, su
 * equipo) se conserva de lo que se mando.
 *
 * <p><b>Que es una caida y que un «no».</b> Si el motor no responde (conexion,
 * tiempo, 5xx) sale como {@code DependenciaDegradada} por el corta circuitos y
 * la simulacion se reintenta. Un 409 {@code accion-no-permitida} es el motor
 * diciendo que no a esa accion: {@link AccionNoPermitida}, y la IA prueba otra.
 * Cualquier otro 4xx es un rechazo definitivo ({@link RechazoDelServicio}).
 */
public class ClienteMotor implements MotorDeCombate {

    static final String DEPENDENCIA = "motor-combate";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteMotor(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public ResultadoDeTurno iniciarTurno(String combatiente, List<Combatiente> combatientes, Long semilla) {
        PeticionDeTurno peticion = new PeticionDeTurno(combatiente, false,
                combatientes.stream().map(CombatienteJson::de).toList(), semilla);
        RespuestaDeTurno respuesta = pedir("/api/v1/combate/turnos", peticion, RespuestaDeTurno.class);
        return new ResultadoDeTurno(respuesta.combatiente(), sucesos(respuesta.eventos()),
                combatientes(respuesta.combatientes(), combatientes));
    }

    @Override
    public ResultadoDeAccion resolverAccion(String accion, String ejecutor, String objetivo,
                                            List<Combatiente> combatientes, Long semilla) {
        PeticionDeAccion peticion = new PeticionDeAccion(accion, ejecutor, objetivo, false,
                combatientes.stream().map(CombatienteJson::de).toList(), semilla);
        RespuestaDeAccion respuesta = pedir("/api/v1/combate/acciones", peticion, RespuestaDeAccion.class);
        if (respuesta.accionEjecutada() == null || respuesta.ejecutor() == null) {
            throw ilegible("no dice que accion se jugo");
        }
        GolpeJson golpe = respuesta.ataque();
        DetalleDeAtaque detalle = golpe == null ? null
                : new DetalleDeAtaque(cero(golpe.ataqueResuelto()), cero(golpe.defensaObjetivo()),
                Boolean.TRUE.equals(golpe.acierta()), golpe.categoria(), golpe.indiceTabla(),
                golpe.porcentajeDano(), cero(golpe.danoBase()), cero(golpe.danoAplicado()));
        return new ResultadoDeAccion(respuesta.accion(), respuesta.accionEjecutada(),
                Boolean.TRUE.equals(respuesta.enValorBase()), respuesta.ejecutor(), respuesta.objetivo(), detalle,
                sucesos(respuesta.eventos()), combatientes(respuesta.combatientes(), combatientes));
    }

    private <T> T pedir(String ruta, Object peticion, Class<T> tipo) {
        Contestacion<T> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .body(peticion)
                .retrieve()
                .body(tipo));
        if (c.rechazada()) {
            if (c.estado() == 409) {
                throw new AccionNoPermitida(motivoDe(c), c.detalle());
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
        if (c.cuerpo() == null) {
            throw ilegible("respondio sin cuerpo");
        }
        return c.cuerpo();
    }

    /** El {@code motivo} del problem detail del 409 (EN_CARGA, BLOQUEADA_POR_NIVEL...), si trae uno legible. */
    private static String motivoDe(Contestacion<?> c) {
        try {
            Map<?, ?> problema = JSON.readValue(c.rechazo().getResponseBodyAsString(), Map.class);
            return problema.get("motivo") instanceof String motivo ? motivo : null;
        } catch (RuntimeException sinCuerpoLegible) {
            return null;
        }
    }

    private static RechazoDelServicio ilegible(String que) {
        return new RechazoDelServicio(DEPENDENCIA, 200, "la respuesta del motor no sirve: " + que);
    }

    private static int cero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private static List<Suceso> sucesos(List<EventoJson> eventos) {
        return eventos == null ? List.of() : eventos.stream()
                .map(e -> new Suceso(e.tipo(), e.combatiente(), e.origen(), e.efecto(), e.cantidad())).toList();
    }

    /**
     * El estado que devolvio el motor. Lo que la respuesta no repite de un
     * combatiente (prototipo, nivel, estadisticas, equipo, epicas) se conserva
     * de lo que se le mando; lo que es estado (vida, poder, cargas, efectos) lo
     * dice siempre el motor.
     */
    private static List<Combatiente> combatientes(List<CombatienteRespuesta> recibidos, List<Combatiente> enviados) {
        if (recibidos == null || recibidos.isEmpty()) {
            throw ilegible("no trae el estado de los combatientes");
        }
        Map<String, Combatiente> porId = new LinkedHashMap<>();
        enviados.forEach(c -> porId.put(c.id(), c));
        return recibidos.stream().map(r -> aDominio(r, porId.get(r.id()))).toList();
    }

    private static Combatiente aDominio(CombatienteRespuesta r, Combatiente enviado) {
        String prototipo = r.prototipo() != null ? r.prototipo() : enviado == null ? null : enviado.prototipo();
        Integer nivel = r.nivel() != null ? r.nivel() : enviado == null ? null : enviado.nivel();
        if (r.id() == null || prototipo == null || nivel == null || r.vidaActual() == null) {
            throw ilegible("un combatiente llega sin identificador, prototipo, nivel o vida");
        }
        EstadisticasDeCombate estadisticas = r.estadisticas() != null && r.estadisticas().completas()
                ? r.estadisticas().aDominio() : enviado == null ? null : enviado.estadisticas();
        List<EfectoActivo> efectos = r.efectos() == null ? List.of() : r.efectos().stream()
                .map(e -> new EfectoActivo(e.codigo(), e.nombre(), e.tipo(), cero(e.valor()), cero(e.turnos()),
                        Boolean.TRUE.equals(e.hastaSuTurno()), e.origen())).toList();
        return new Combatiente(r.id(), prototipo, nivel, estadisticas, r.vidaActual(), r.poderActual(),
                r.turnosJugados() != null ? r.turnosJugados() : enviado == null ? 0 : enviado.turnosJugados(),
                r.cargas(), efectos,
                r.equipamiento() != null ? r.equipamiento() : enviado == null ? List.of() : enviado.equipamiento(),
                r.epicas() != null ? r.epicas() : enviado == null ? List.of() : enviado.epicas(),
                r.ultimoDanoRecibido() == null ? null
                        : new Combatiente.GolpeRecibido(r.ultimoDanoRecibido().de(),
                        cero(r.ultimoDanoRecibido().cantidad())),
                r.recargas(),
                r.acciones() == null ? List.of() : r.acciones().stream().map(AccionJson::codigo).toList());
    }

    // ------------------------------------------------------------------- JSON

    /**
     * Las formas exactas del contrato. En Jackson 3 un primitivo ausente es un
     * error, no un cero: lo que el contrato no garantiza va en tipos con nulo.
     */
    record PeticionDeTurno(String combatiente, boolean porEquipos, List<CombatienteJson> combatientes, Long semilla) {
    }

    record PeticionDeAccion(String accion, String ejecutor, String objetivo, boolean porEquipos,
                            List<CombatienteJson> combatientes, Long semilla) {
    }

    /** {@code Combatiente} de ida: nulo en estadisticas y poder significa «las del catalogo» y «el maximo». */
    record CombatienteJson(String id, Integer equipo, String prototipo, int nivel, EstadisticasJson estadisticas,
                           int vidaActual, Integer poderActual, int turnosJugados, Map<String, Integer> cargas,
                           List<EfectoJson> efectos, List<String> equipamiento, List<String> epicas,
                           GolpeRecibidoJson ultimoDanoRecibido) {

        static CombatienteJson de(Combatiente c) {
            return new CombatienteJson(c.id(), null, c.prototipo(), c.nivel(),
                    c.estadisticas() == null ? null : EstadisticasJson.de(c.estadisticas()), c.vidaActual(),
                    c.poderActual(), c.turnosJugados(), c.cargas(),
                    c.efectos().stream().map(EfectoJson::de).toList(), c.equipamiento(), c.epicas(),
                    c.ultimoDanoRecibido() == null ? null
                            : new GolpeRecibidoJson(c.ultimoDanoRecibido().de(), c.ultimoDanoRecibido().cantidad()));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EstadisticasJson(Integer poder, Integer vida, Integer defensa, FormulaJson ataque, FormulaJson dano,
                            FormulaJson sanar) {

        static EstadisticasJson de(EstadisticasDeCombate e) {
            return new EstadisticasJson(e.poder(), e.vida(), e.defensa(), FormulaJson.de(e.ataque()),
                    FormulaJson.de(e.dano()), FormulaJson.de(e.sanar()));
        }

        boolean completas() {
            return poder != null && vida != null && defensa != null;
        }

        EstadisticasDeCombate aDominio() {
            return new EstadisticasDeCombate(poder, vida, defensa, ataque == null ? null : ataque.aDominio(),
                    dano == null ? null : dano.aDominio(), sanar == null ? null : sanar.aDominio());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FormulaJson(Integer base, Integer cantidadDados, Integer caras) {

        static FormulaJson de(Formula f) {
            return f == null ? null : new FormulaJson(f.base(), f.cantidadDados(), f.caras());
        }

        Formula aDominio() {
            return new Formula(cero(base), cero(cantidadDados), cero(caras));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EfectoJson(String codigo, String nombre, String tipo, Integer valor, Integer turnos, Boolean hastaSuTurno,
                      String origen) {

        static EfectoJson de(EfectoActivo e) {
            return new EfectoJson(e.codigo(), e.nombre(), e.tipo(), e.valor(), e.turnos(), e.hastaSuTurno(),
                    e.origen());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GolpeRecibidoJson(String de, Integer cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccionJson(String codigo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CombatienteRespuesta(String id, String prototipo, Integer nivel, EstadisticasJson estadisticas,
                                Integer vidaActual, Integer poderActual, Integer turnosJugados,
                                Map<String, Integer> cargas, List<EfectoJson> efectos, List<String> equipamiento,
                                List<String> epicas, GolpeRecibidoJson ultimoDanoRecibido,
                                Map<String, Integer> recargas, List<AccionJson> acciones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GolpeJson(Integer ataqueResuelto, Integer defensaObjetivo, Boolean acierta, String categoria,
                     Integer indiceTabla, Integer porcentajeDano, Integer danoBase, Integer danoAplicado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventoJson(String tipo, String combatiente, String origen, String efecto, Integer cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeTurno(String combatiente, List<EventoJson> eventos, List<CombatienteRespuesta> combatientes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeAccion(String accion, String accionEjecutada, Boolean enValorBase, String ejecutor,
                             String objetivo, GolpeJson ataque, List<EventoJson> eventos,
                             List<CombatienteRespuesta> combatientes) {
    }
}

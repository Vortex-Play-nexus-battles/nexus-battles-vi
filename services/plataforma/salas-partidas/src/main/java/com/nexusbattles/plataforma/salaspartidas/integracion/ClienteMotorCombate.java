package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CombatienteResuelto;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadisticasDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EventoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InicioDeTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDeAccion;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador hacia el motor de combate — {@code contracts/openapi/motor-combate.yaml}
 * 1.2.0 (B7).
 *
 * <p>Habla con {@code POST /api/v1/combate/acciones} y
 * {@code POST /api/v1/combate/turnos}. El motor no guarda nada entre llamadas:
 * cada peticion lleva el estado de TODOS los combatientes de la partida —los
 * participantes con heroe y prototipo conocidos— y la respuesta trae el de
 * todos, que la partida guarda tal cual para la llamada siguiente.
 *
 * <p><b>Que manda.</b> El identificador de cada combatiente es su
 * {@code idJugador}; su prototipo, su nivel, sus estadisticas con el equipo
 * (si se conocen; si no, el motor usa las del catalogo en ese nivel), su vida,
 * su poder (nulo = maximo), sus cargas, sus efectos, su equipamiento, sus
 * epicas y el ultimo golpe que recibio.
 *
 * <p><b>Ningun fallo se traduce a un resultado inventado.</b> Si el motor
 * <i>no responde</i> (conexion, tiempo, 5xx) la llamada sale por el corta
 * circuitos de HU-DIS-003 como {@link DependenciaDegradada}, seccion «Motor de
 * combate». Si <i>rechaza la accion</i> (409 {@code accion-no-permitida}) es
 * {@link AccionNoPermitida}, con el {@code motivo} del motor. Cualquier otra
 * respuesta que no sirve (un 400, un 404 de prototipo, un cuerpo ilegible) es
 * {@link MotorNoDisponible}, y el circuito no se abre porque el motor esta vivo.
 *
 * <p><b>Por eso el cuerpo se lee FUERA del corta circuitos.</b> Dentro solo va
 * el transporte: la respuesta llega como texto y se interpreta despues. Si la
 * conversion la hiciera el propio {@code RestClient}, un cuerpo al que le falta
 * un campo (un booleano ausente es un error para Jackson 3) saldria del corta
 * circuitos como un fallo mas y dos respuestas raras seguidas abririan el
 * circuito de un motor que esta contestando.
 */
public class ClienteMotorCombate implements MotorDeCombate {

    /**
     * Lectura tolerante: un campo que el motor no mande se toma por su valor
     * por omision (falso, cero) y un campo nuevo no rompe nada. Lo esencial
     * —que accion se jugo, quien, el estado de todos— se comprueba aparte.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteMotorCombate(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
        this.corta = corta;
    }

    @Override
    public ResolucionDeAccion resolverAccion(String accion, UUID ejecutor, UUID objetivo, Partida partida) {
        PeticionDeAccion peticion = new PeticionDeAccion(accion, ejecutor.toString(),
                objetivo == null ? null : objetivo.toString(), partida.conEquipos(),
                combatientesDe(partida, false));
        RespuestaDeAccion respuesta = pedir("/api/v1/combate/acciones", peticion, RespuestaDeAccion.class);
        if (respuesta.accionEjecutada() == null || respuesta.ejecutor() == null || respuesta.combatientes() == null) {
            throw new MotorNoDisponible("el motor respondio una accion que no se entiende");
        }
        return new ResolucionDeAccion(respuesta.accion(), respuesta.accionEjecutada(), respuesta.enValorBase(),
                uuid(respuesta.ejecutor()), uuid(respuesta.objetivo()), respuesta.tipo(), respuesta.esEpica(),
                respuesta.potenciada(), golpeDe(respuesta.ataque()), afectadosDe(respuesta.afectados()),
                eventosDe(respuesta.eventos()), resueltosDe(respuesta.combatientes()));
    }

    @Override
    public InicioDeTurno iniciarTurno(UUID combatiente, Partida partida, boolean aVidaCompleta) {
        PeticionDeTurno peticion = new PeticionDeTurno(combatiente.toString(), partida.conEquipos(),
                combatientesDe(partida, aVidaCompleta));
        RespuestaDeTurno respuesta = pedir("/api/v1/combate/turnos", peticion, RespuestaDeTurno.class);
        if (respuesta.combatiente() == null || respuesta.combatientes() == null) {
            throw new MotorNoDisponible("el motor respondio un turno que no se entiende");
        }
        return new InicioDeTurno(uuid(respuesta.combatiente()), afectadosDe(respuesta.afectados()),
                eventosDe(respuesta.eventos()), resueltosDe(respuesta.combatientes()));
    }

    private <T> T pedir(String ruta, Object peticion, Class<T> tipo) {
        Contestacion<String> contestacion = Contestacion.protegida(corta, () -> http.post()
                .uri(base + ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .body(peticion)
                .retrieve()
                .body(String.class));
        if (contestacion.rechazada()) {
            if (contestacion.estado() == 409) {
                JsonNode problema = problemaDe(contestacion.rechazo().getResponseBodyAsString());
                throw new AccionNoPermitida(texto(problema, "motivo"), texto(problema, "detail"));
            }
            throw new MotorNoDisponible("el motor rechazo la peticion con " + contestacion.estado());
        }
        String cuerpo = contestacion.cuerpo();
        if (cuerpo == null || cuerpo.isBlank()) {
            throw new MotorNoDisponible("el motor respondio sin cuerpo");
        }
        try {
            return JSON.readValue(cuerpo, tipo);
        } catch (JacksonException ilegible) {
            throw new MotorNoDisponible("el motor respondio un cuerpo que no se entiende");
        }
    }

    // ------------------------------------------------------------ hacia el motor

    /**
     * Los combatientes de la partida: los participantes con heroe y prototipo
     * conocidos. Los demas (fichas anteriores a V8) no se pueden resolver.
     */
    static List<CombatienteJson> combatientesDe(Partida partida, boolean aVidaCompleta) {
        return partida.participantes().stream()
                .filter(p -> p.heroe() != null && p.heroe().prototipo() != null && !p.heroe().prototipo().isBlank())
                .map(p -> combatienteDe(p, aVidaCompleta))
                .toList();
    }

    private static CombatienteJson combatienteDe(ParticipanteDePartida p, boolean aVidaCompleta) {
        HeroeDeCombate heroe = p.heroe();
        PerfilDeCombate perfil = heroe.perfil();
        EstadoDeCombate combate = p.combate();
        EstadisticasDeCombate estadisticas = combate != null && combate.estadisticas() != null
                ? combate.estadisticas()
                : perfil == null ? null : perfil.estadisticas();
        return new CombatienteJson(
                p.idJugador().toString(),
                p.equipo(),
                heroe.prototipo(),
                heroe.nivelDeCombate(),
                estadisticas == null ? null : EstadisticasJson.de(estadisticas),
                // Al empezar, todos a su vida maxima en su nivel: la calcula el
                // motor, que acota cualquier vida por encima de ella.
                aVidaCompleta ? Integer.MAX_VALUE : heroe.vidaActual(),
                aVidaCompleta || combate == null ? null : combate.poderActual(),
                combate == null ? 0 : combate.turnosJugados(),
                combate == null ? Map.of() : combate.cargas(),
                combate == null ? List.of()
                        : combate.efectos().stream().map(EfectoJson::de).toList(),
                perfil == null ? List.of() : perfil.equipamiento(),
                perfil == null ? List.of() : perfil.epicas(),
                combate == null || combate.ultimoDanoRecibido() == null ? null
                        : new GolpeRecibidoJson(combate.ultimoDanoRecibido().de(),
                        combate.ultimoDanoRecibido().cantidad()));
    }

    // ------------------------------------------------------------ desde el motor

    private static List<CombatienteResuelto> resueltosDe(List<CombatienteRespuesta> combatientes) {
        return combatientes.stream().map(ClienteMotorCombate::resueltoDe).toList();
    }

    private static CombatienteResuelto resueltoDe(CombatienteRespuesta c) {
        EstadisticasDeCombate estadisticas = c.estadisticas() == null ? null : c.estadisticas().aDominio();
        int vidaMaxima = estadisticas == null ? Math.max(1, c.vidaActual()) : estadisticas.vida();
        EstadoDeCombate estado = new EstadoDeCombate(
                c.poderActual() == null ? 0 : c.poderActual(),
                estadisticas == null ? 0 : estadisticas.poder(),
                c.turnosJugados(),
                c.cargas(),
                c.efectos() == null ? List.of() : c.efectos().stream().map(EfectoJson::aDominio).toList(),
                c.ultimoDanoRecibido() == null ? null
                        : new EstadoDeCombate.GolpeRecibido(c.ultimoDanoRecibido().de(),
                        c.ultimoDanoRecibido().cantidad()),
                c.recargas(),
                c.acciones() == null ? List.of() : c.acciones().stream().map(AccionJson::aDominio).toList(),
                estadisticas);
        return new CombatienteResuelto(uuid(c.id()), c.vidaActual(), vidaMaxima, estado);
    }

    private static ResolucionDeAccion.Golpe golpeDe(GolpeJson g) {
        return g == null ? null : new ResolucionDeAccion.Golpe(g.ataqueResuelto(), g.defensaObjetivo(),
                g.acierta(), g.categoria(), g.indiceTabla(), g.porcentajeDano(), g.danoBase(), g.danoAplicado());
    }

    private static List<ResolucionDeAccion.Afectado> afectadosDe(List<AfectadoJson> afectados) {
        return afectados == null ? List.of() : afectados.stream()
                .map(a -> new ResolucionDeAccion.Afectado(uuid(a.id()), a.vidaAntes(), a.vidaDespues(),
                        a.diferencia()))
                .toList();
    }

    private static List<EventoDeCombate> eventosDe(List<EventoJson> eventos) {
        return eventos == null ? List.of() : eventos.stream()
                .map(e -> new EventoDeCombate(e.tipo(), uuid(e.combatiente()), uuid(e.origen()), e.efecto(),
                        e.cantidad()))
                .toList();
    }

    /** Los identificadores de los combatientes son los {@code idJugador} que se le mandaron. */
    private static UUID uuid(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException noEsDeEstaPartida) {
            throw new MotorNoDisponible("el motor devolvio un combatiente que no se le mando");
        }
    }

    private static JsonNode problemaDe(String cuerpo) {
        try {
            return cuerpo == null || cuerpo.isBlank() ? null : JSON.readTree(cuerpo);
        } catch (JacksonException ilegible) {
            return null;
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        if (nodo == null || !nodo.hasNonNull(campo)) {
            return null;
        }
        return nodo.get(campo).asString();
    }

    // ------------------------------------------------------------------- JSON

    /** Espejo de {@code PeticionDeAccion}. */
    record PeticionDeAccion(String accion, String ejecutor, String objetivo, boolean porEquipos,
                            List<CombatienteJson> combatientes) {
    }

    /** Espejo de {@code PeticionDeTurno}. */
    record PeticionDeTurno(String combatiente, boolean porEquipos, List<CombatienteJson> combatientes) {
    }

    /** Espejo de {@code Combatiente}, de ida. */
    record CombatienteJson(String id, Integer equipo, String prototipo, int nivel, EstadisticasJson estadisticas,
                           int vidaActual, Integer poderActual, int turnosJugados, Map<String, Integer> cargas,
                           List<EfectoJson> efectos, List<String> equipamiento, List<String> epicas,
                           GolpeRecibidoJson ultimoDanoRecibido) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EstadisticasJson(int poder, int vida, int defensa, FormulaJson ataque, FormulaJson dano,
                            FormulaJson sanar) {

        static EstadisticasJson de(EstadisticasDeCombate e) {
            return new EstadisticasJson(e.poder(), e.vida(), e.defensa(), FormulaJson.de(e.ataque()),
                    FormulaJson.de(e.dano()), FormulaJson.de(e.sanar()));
        }

        EstadisticasDeCombate aDominio() {
            return new EstadisticasDeCombate(poder, Math.max(1, vida), defensa, FormulaJson.aDominio(ataque),
                    FormulaJson.aDominio(dano), FormulaJson.aDominio(sanar));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FormulaJson(int base, int cantidadDados, int caras) {

        static FormulaJson de(EstadisticasDeCombate.Formula f) {
            return f == null ? null : new FormulaJson(f.base(), f.cantidadDados(), f.caras());
        }

        static EstadisticasDeCombate.Formula aDominio(FormulaJson f) {
            return f == null ? null : new EstadisticasDeCombate.Formula(f.base(), f.cantidadDados(), f.caras());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EfectoJson(String codigo, String nombre, String tipo, int valor, int turnos, boolean hastaSuTurno,
                      String origen) {

        static EfectoJson de(EstadoDeCombate.Efecto e) {
            return new EfectoJson(e.codigo(), e.nombre(), e.tipo(), e.valor(), e.turnos(), e.hastaSuTurno(),
                    e.origen());
        }

        EstadoDeCombate.Efecto aDominio() {
            return new EstadoDeCombate.Efecto(codigo, nombre, tipo, valor, turnos, hastaSuTurno, origen);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GolpeRecibidoJson(String de, int cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccionJson(String codigo, String nombre, String tipo, boolean esEpica, Integer costoPoder,
                      boolean todoElPoder, int turnosDeCarga, int nivelRequerido, boolean disponible,
                      String motivo) {

        EstadoDeCombate.AccionDisponible aDominio() {
            return new EstadoDeCombate.AccionDisponible(codigo, Objects.requireNonNullElse(nombre, codigo), tipo,
                    esEpica, costoPoder, todoElPoder, turnosDeCarga, nivelRequerido, disponible, motivo);
        }
    }

    /** Espejo de {@code Combatiente}, de vuelta. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CombatienteRespuesta(String id, EstadisticasJson estadisticas, int vidaActual, Integer poderActual,
                                int turnosJugados, Map<String, Integer> cargas, List<EfectoJson> efectos,
                                GolpeRecibidoJson ultimoDanoRecibido, Map<String, Integer> recargas,
                                List<AccionJson> acciones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GolpeJson(int ataqueResuelto, int defensaObjetivo, boolean acierta, String categoria,
                     Integer indiceTabla, Integer porcentajeDano, int danoBase, int danoAplicado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AfectadoJson(String id, int vidaAntes, int vidaDespues, int diferencia) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventoJson(String tipo, String combatiente, String origen, String efecto, Integer cantidad) {
    }

    /** Espejo de {@code ResultadoDeAccion}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeAccion(String accion, String accionEjecutada, boolean enValorBase, String ejecutor,
                             String objetivo, String tipo, boolean esEpica, boolean potenciada, GolpeJson ataque,
                             List<AfectadoJson> afectados, List<EventoJson> eventos,
                             List<CombatienteRespuesta> combatientes) {
    }

    /** Espejo de {@code ResultadoDeTurno}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeTurno(String combatiente, List<AfectadoJson> afectados, List<EventoJson> eventos,
                            List<CombatienteRespuesta> combatientes) {
    }
}

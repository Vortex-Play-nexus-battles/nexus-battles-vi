package nexus.misiones.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.ReglaDeMisionIncumplida;
import nexus.misiones.dominio.ReglasDeMatricula;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.RepositorioDeEstrategias;
import nexus.misiones.dominio.SituacionDelJugador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enviar un heroe a una mision (seccion 7.8.6, «matriculacion de la mision»;
 * HU-MIS-008 y HU-MIS-009).
 *
 * <p>El orden importa, porque la unica escritura en otro servicio —el bloqueo
 * del heroe— es la penultima: todo lo que se puede rechazar se rechaza antes
 * de tocar el inventario. Si despues de bloquear no se puede guardar la
 * ejecucion, se libera el heroe y el error sube: la mision no queda en progreso
 * ni el heroe reservado (HU-MIS-009).
 */
public class MatricularHeroe {

    private static final Logger BITACORA = LoggerFactory.getLogger(MatricularHeroe.class);

    static final String SIN_EQUIPO =
            "Debe completar su mazo: equípale al menos un arma, una armadura o un ítem antes de enviarlo.";

    private final CatalogoDeMisiones catalogo;
    private final RepositorioDeEjecuciones ejecuciones;
    private final RepositorioDeEstrategias estrategias;
    private final InventarioDeHeroes inventario;
    private final CatalogoDeProductos productos;
    private final ServicioDeHeroes heroes;
    private final ParametrosDeMisiones parametros;
    private final Clock reloj;
    private final LongSupplier semillas;

    public MatricularHeroe(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                           RepositorioDeEstrategias estrategias, InventarioDeHeroes inventario,
                           CatalogoDeProductos productos, ServicioDeHeroes heroes, ParametrosDeMisiones parametros,
                           Clock reloj, LongSupplier semillas) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.estrategias = Objects.requireNonNull(estrategias);
        this.inventario = Objects.requireNonNull(inventario);
        this.productos = Objects.requireNonNull(productos);
        this.heroes = Objects.requireNonNull(heroes);
        this.parametros = Objects.requireNonNull(parametros);
        this.reloj = Objects.requireNonNull(reloj);
        this.semillas = Objects.requireNonNull(semillas);
    }

    public Matricula matricular(String jugadorUid, SolicitudDeMatricula solicitud) {
        String clave = solicitud.claveIdempotencia();
        if (clave != null && !clave.isBlank()) {
            Optional<Ejecucion> previa = ejecuciones.buscarPorClave(jugadorUid, clave);
            if (previa.isPresent()) {
                return new Matricula(previa.get(), true);
            }
        }
        Instant ahora = reloj.instant();
        Escalon escalon = solicitud.escalon() == null ? Escalon.NORMAL : solicitud.escalon();

        // 1. La mision lo admite.
        Mision mision = catalogo.buscar(solicitud.misionId())
                .orElseThrow(() -> new MisionNoEncontrada(solicitud.misionId()));
        exigirMisionDisponible(jugadorUid, mision, escalon, ahora);

        // 2. El heroe es suyo, es un heroe y esta libre.
        InventarioDeHeroes.HeroeDelInventario heroe = inventario.consultar(solicitud.heroeId());
        if (!jugadorUid.equals(heroe.propietarioUid())) {
            throw new HeroeNoEncontrado();
        }
        if (!heroe.esHeroe()) {
            throw new HeroeNoApto(List.of("Solo un héroe puede salir de misión."));
        }
        if (!heroe.disponible()) {
            throw heroe.ejecucionMisionId() != null ? HeroeOcupado.enMision() : HeroeOcupado.enSubasta();
        }

        // 3. Es apto: lleva equipo y no es un sanador en solitario. Todos los
        //    motivos a la vez (HU-MIS-008).
        String prototipo = productos.prototipoDe(heroe.productoId());
        if (prototipo == null || prototipo.isBlank()) {
            throw new HeroeNoApto(List.of("No pudimos saber qué tipo de héroe es: su producto no declara prototipo."));
        }
        List<String> motivos = new ArrayList<>();
        if (!inventario.equipado(jugadorUid, heroe.id())) {
            motivos.add(SIN_EQUIPO);
        }
        ServicioDeHeroes.VeredictoDeComposicion individual = heroes.validarIndividual(prototipo);
        if (!individual.valida()) {
            motivos.add(individual.motivo());
        }
        if (!motivos.isEmpty()) {
            throw new HeroeNoApto(motivos);
        }

        // 4. La estrategia vale para su nivel REAL (el del inventario, no el
        //    que eligiera la pantalla).
        int nivel = heroe.nivel() == null ? HeroeEnMision.NIVEL_MINIMO : heroe.nivel();
        List<List<String>> pedida = solicitud.rotaciones() != null
                ? solicitud.rotaciones()
                : estrategias.buscar(jugadorUid, heroe.id()).map(EstrategiaGuardada::rotaciones).orElse(List.of());
        ServicioDeHeroes.VeredictoDeEstrategia veredicto = heroes.validarEstrategia(prototipo, nivel, pedida);
        if (!veredicto.valida()) {
            throw new EstrategiaInvalida(veredicto.motivo(), veredicto.habilidadesValidas());
        }
        List<List<String>> estrategia = veredicto.rotaciones() == null ? List.of() : veredicto.rotaciones();

        // 5. La foto del heroe con que sale (7.8.10: su equipo no cambia hasta que vuelva).
        InventarioDeHeroes.EstadisticasDelHeroe estadisticas = inventario.estadisticas(jugadorUid, heroe.id());
        HeroeEnMision enMision = new HeroeEnMision(heroe.id(), nombreDe(heroe, prototipo), prototipo,
                heroe.productoId(), nivel, heroe.experiencia() == null ? 0 : heroe.experiencia(),
                estadisticas.poder(), estadisticas.vida(), estadisticas.defensa());

        Duration duracion = Duration.ofMillis(mision.duracionEnMilisegundos(parametros.duracionDeUnaHora().toMillis()));
        long semilla = parametros.semillaDePruebas() != null ? parametros.semillaDePruebas() : semillas.getAsLong();
        Ejecucion ejecucion = Ejecucion.nueva(UUID.randomUUID(), mision.id(), jugadorUid, enMision, estrategia,
                escalon, ahora, duracion, semilla, clave == null || clave.isBlank() ? null : clave);

        // 6. Bloquear y guardar, o liberar y avisar.
        inventario.bloquear(heroe.id(), jugadorUid, ejecucion.id());
        Ejecucion guardada;
        try {
            guardada = ejecuciones.guardar(ejecucion);
        } catch (RuntimeException fallo) {
            liberarTrasFallo(heroe.id(), jugadorUid, ejecucion.id());
            if (fallo instanceof EjecucionModificadaConcurrentemente) {
                throw new ReglaDeMisionIncumplida("Ahora mismo ya tienes esta misión en curso: espera a que "
                        + "termine o cancélala antes de volver a empezarla.");
            }
            throw fallo;
        }
        guardarEstrategia(jugadorUid, enMision, estrategia, ahora);
        BITACORA.info("Heroe {} enviado a la mision {} (ejecucion {}), termina {}",
                heroe.id(), mision.id(), guardada.id(), guardada.terminaEn());
        return new Matricula(guardada, false);
    }

    private void exigirMisionDisponible(String jugadorUid, Mision mision, Escalon escalon, Instant ahora) {
        List<Ejecucion> suyas = ejecuciones.delJugador(jugadorUid);
        Set<String> completadas = suyas.stream()
                .filter(e -> e.estado() == EstadoEjecucion.COMPLETADA)
                .map(Ejecucion::misionId)
                .collect(Collectors.toSet());
        Map<String, String> nombres = catalogo.todas().stream()
                .collect(Collectors.toMap(Mision::id, Mision::nombre, (a, b) -> a));
        SituacionDelJugador situacion = SituacionDelJugador.calcular(mision,
                suyas.stream().filter(e -> e.misionId().equals(mision.id())).toList(), completadas,
                nombres::get, ahora);
        long intentos = mision.intentos() == null
                ? 0
                : ejecuciones.iniciadasDesde(jugadorUid, mision.id(), mision.intentos().periodo().inicio(ahora));
        ReglasDeMatricula.exigir(mision, situacion, escalon, parametros.multiplicadorMitico(), intentos, ahora);
    }

    private void liberarTrasFallo(String heroeId, String jugadorUid, UUID ejecucionId) {
        try {
            inventario.liberar(heroeId, jugadorUid, ejecucionId, 0);
        } catch (RuntimeException sinLiberar) {
            // No se oculta el fallo original por este: queda anotado para que
            // alguien libere al heroe a mano si el inventario tampoco contesta.
            BITACORA.error("No se pudo guardar la ejecucion {} ni liberar al heroe {}: {}",
                    ejecucionId, heroeId, sinLiberar.getMessage());
        }
    }

    private void guardarEstrategia(String jugadorUid, HeroeEnMision heroe, List<List<String>> estrategia,
                                   Instant ahora) {
        try {
            estrategias.guardar(new EstrategiaGuardada(jugadorUid, heroe.id(), heroe.prototipo(), heroe.nivel(),
                    estrategia, ahora));
        } catch (RuntimeException fallo) {
            // La mision ya empezo con esta estrategia; no poder recordarla para
            // la proxima vez no es motivo para deshacerla.
            BITACORA.warn("La ejecucion se guardo pero no la estrategia del heroe {}: {}", heroe.id(),
                    fallo.getMessage());
        }
    }

    private static String nombreDe(InventarioDeHeroes.HeroeDelInventario heroe, String prototipo) {
        return heroe.nombrePropio() == null || heroe.nombrePropio().isBlank() ? prototipo : heroe.nombrePropio();
    }
}

package nexus.misiones.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lleva a los otros servicios lo que la ejecucion gano (7.8.6, «el heroe es
 * liberado y regresa al inventario», «las recompensas obtenidas se agregan al
 * inventario»; 7.8.10, «los creditos se suman al balance disponible») y se lo
 * cuenta al jugador (7.8.9, notificaciones de misiones; RF-NOT-004).
 *
 * <p>Paso a paso, en el orden de {@link PasoDeLiquidacion}, cada uno
 * idempotente del lado de quien lo recibe:
 * <ul>
 *   <li>un paso confirmado queda HECHO y no se repite;</li>
 *   <li>un rechazo definitivo (un 4xx) queda FALLIDO con su motivo, y se
 *       sigue con el siguiente: que el correo no tenga destinatario no le
 *       quita al jugador sus creditos;</li>
 *   <li>un servicio que no responde corta la vuelta y la ejecucion espera
 *       su reintento, cada vez mas tarde.</li>
 * </ul>
 */
public class LiquidarEjecucion {

    private static final Logger BITACORA = LoggerFactory.getLogger(LiquidarEjecucion.class);
    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    /** Largo maximo del titulo de un aviso (notificaciones.yaml 1.2.0). */
    static final int TITULO_MAXIMO = 200;

    private final RepositorioDeEjecuciones ejecuciones;
    private final CatalogoDeMisiones catalogo;
    private final InventarioDeHeroes inventario;
    private final LibroDeCreditos libro;
    private final DirectorioDeJugadores directorio;
    private final CorreoDeMisiones correo;
    private final AvisosDeMisiones avisos;
    private final ParametrosDeMisiones parametros;
    private final Clock reloj;

    public LiquidarEjecucion(RepositorioDeEjecuciones ejecuciones, CatalogoDeMisiones catalogo,
                             InventarioDeHeroes inventario, LibroDeCreditos libro, DirectorioDeJugadores directorio,
                             CorreoDeMisiones correo, AvisosDeMisiones avisos, ParametrosDeMisiones parametros,
                             Clock reloj) {
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.catalogo = Objects.requireNonNull(catalogo);
        this.inventario = Objects.requireNonNull(inventario);
        this.libro = Objects.requireNonNull(libro);
        this.directorio = Objects.requireNonNull(directorio);
        this.correo = Objects.requireNonNull(correo);
        this.avisos = Objects.requireNonNull(avisos);
        this.parametros = Objects.requireNonNull(parametros);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public Ejecucion liquidar(Ejecucion ejecucion) {
        Instant ahora = reloj.instant();
        for (PasoDeLiquidacion paso : ejecucion.pasosPendientes()) {
            try {
                hacer(paso, ejecucion);
                ejecucion.pasoHecho(paso);
            } catch (RechazoDelServicio rechazo) {
                BITACORA.warn("Ejecucion {}: el paso {} fue rechazado y no se reintenta: {}",
                        ejecucion.id(), paso, rechazo.getMessage());
                ejecucion.pasoFallido(paso, rechazo.getMessage());
            } catch (RuntimeException sinRespuesta) {
                ejecucion.reintentarMasTarde(ahora, parametros.reintentoBase(), sinRespuesta.getMessage());
                BITACORA.warn("Ejecucion {}: el paso {} se reintentara a las {}: {}",
                        ejecucion.id(), paso, ejecucion.proximoIntento(), sinRespuesta.getMessage());
                break;
            }
        }
        try {
            Ejecucion guardada = ejecuciones.guardar(ejecucion);
            if (!guardada.liquidacionPendiente() && guardada.intentosDeLiquidacion() > 0) {
                BITACORA.info("Ejecucion {} liquidada tras {} reintentos: se recupero del fallo anterior ({})",
                        guardada.id(), guardada.intentosDeLiquidacion(), guardada.ultimoError());
            }
            return guardada;
        } catch (EjecucionModificadaConcurrentemente otraVuelta) {
            // Otra vuelta del trabajo la liquido a la vez. Lo que se hizo aqui
            // fue idempotente; la siguiente lectura trae el estado bueno.
            BITACORA.info("Ejecucion {} liquidada a la vez por otra vuelta; se relee la proxima", ejecucion.id());
            return ejecuciones.buscar(ejecucion.id()).orElse(ejecucion);
        }
    }

    private void hacer(PasoDeLiquidacion paso, Ejecucion ejecucion) {
        RecompensasDeEjecucion recompensas = ejecucion.recompensas();
        String referencia = "mision-" + ejecucion.id();
        switch (paso) {
            case LIBERACION -> {
                double experiencia = recompensas == null ? 0 : recompensas.experiencia();
                InventarioDeHeroes.ProgresionDelHeroe progresion = inventario.liberar(
                        ejecucion.heroe().id(), ejecucion.jugadorUid(), ejecucion.id(), experiencia);
                ejecucion.registrarProgresion(progresion.nivel(), progresion.experiencia());
            }
            case CREDITOS -> libro.acreditar(ejecucion.jugadorUid(), recompensas.creditos(), referencia,
                    "recompensa-mision");
            case BOTIN -> inventario.entregar(ejecucion.jugadorUid(), ejecucion.id(),
                    recompensas.productos().stream()
                            .map(p -> new InventarioDeHeroes.ProductoAEntregar(p.productoId(), p.cantidad()))
                            .toList(),
                    referencia + "-botin");
            case EPICA -> inventario.entregar(ejecucion.jugadorUid(), ejecucion.id(),
                    recompensas.epicas().stream()
                            .filter(RecompensasDeEjecucion.EpicaGanada::entregable)
                            .map(e -> new InventarioDeHeroes.ProductoAEntregar(e.productoId(), 1))
                            .toList(),
                    referencia + "-epica");
            case CORREO -> escribir(ejecucion, asuntoDeFin(ejecucion), mensajeDeFin(ejecucion),
                    referencia + "-correo");
            case CORREO_EPICA -> escribir(ejecucion, asuntoDeEpica(recompensas), mensajeDeEpica(ejecucion),
                    referencia + "-correo-epica");
            case AVISO -> avisar(ejecucion, referencia + "-aviso", asuntoDeFin(ejecucion), mensajeDeFin(ejecucion));
            case AVISO_EPICA -> avisar(ejecucion, referencia + "-aviso-epica", asuntoDeEpica(recompensas),
                    mensajeDeEpica(ejecucion));
            case AVISO_DESBLOQUEO -> {
                // Un aviso por mision desbloqueada, cada uno con su id: si la
                // vuelta se corta a mitad, el reintento repite los ya dados y
                // la bandeja los reconoce (409) sin duplicarlos.
                for (Mision nueva : desbloqueadas(ejecucion)) {
                    avisar(ejecucion, referencia + "-aviso-desbloqueo-" + nueva.id(),
                            "Nueva misión disponible: «" + nueva.nombre() + "»",
                            "Completaste «" + nombreDeMision(ejecucion) + "»: ya puedes enviar un héroe a «"
                                    + nueva.nombre() + "».");
                }
            }
        }
    }

    private void escribir(Ejecucion ejecucion, String asunto, String mensaje, String clave) {
        DirectorioDeJugadores.Contacto contacto = directorio.contacto(ejecucion.jugadorUid())
                .orElseThrow(() -> new RechazoDelServicio("ms-identidad", 404,
                        "no hay contacto para el jugador: no hay a quien escribir"));
        correo.enviar(contacto, asunto, mensaje, clave);
    }

    /** El aviso lleva la hora del hecho, no la del intento: es la misma en cada reintento. */
    private void avisar(Ejecucion ejecucion, String id, String titulo, String cuerpo) {
        avisos.avisar(ejecucion.jugadorUid(), id, recortar(titulo, TITULO_MAXIMO), cuerpo, ejecucion.terminadaEn());
    }

    /**
     * Las misiones que esta ejecucion desbloqueo (7.8.2, la historia «se
     * desbloquea secuencialmente»): las vigentes que la piden como previa,
     * cuyos demas requisitos el jugador ya cumplia, y que no ha empezado nunca.
     * Es la misma regla con la que el tablon las deja de pintar Bloqueadas
     * ({@code SituacionDelJugador}).
     */
    private List<Mision> desbloqueadas(Ejecucion ejecucion) {
        List<Ejecucion> suyas = ejecuciones.delJugador(ejecucion.jugadorUid());
        Set<String> completadas = suyas.stream()
                .filter(e -> e.estado() == EstadoEjecucion.COMPLETADA)
                .map(Ejecucion::misionId)
                .collect(Collectors.toCollection(HashSet::new));
        completadas.add(ejecucion.misionId());
        Set<String> empezadas = suyas.stream().map(Ejecucion::misionId).collect(Collectors.toSet());
        Instant ahora = reloj.instant();
        return catalogo.todas().stream()
                .filter(m -> m.requisitosPrevios().contains(ejecucion.misionId()))
                .filter(m -> completadas.containsAll(m.requisitosPrevios()))
                .filter(m -> !empezadas.contains(m.id()))
                .filter(m -> m.vigente(ahora))
                .toList();
    }

    // ------------------------------------------------------------ textos

    private String nombreDeMision(Ejecucion ejecucion) {
        return catalogo.buscar(ejecucion.misionId()).map(Mision::nombre).orElse(ejecucion.misionId());
    }

    private String asuntoDeFin(Ejecucion ejecucion) {
        String mision = nombreDeMision(ejecucion);
        return ejecucion.estado() == EstadoEjecucion.COMPLETADA
                ? "Tu misión «" + mision + "» terminó con éxito"
                : "Tu misión «" + mision + "» terminó: " + ejecucion.heroe().nombre() + " fue derrotado";
    }

    /**
     * El detalle de CADA recompensa (7.8.10, «notificacion detallada de cada
     * recompensa recibida»): creditos, experiencia, nivel, cada objeto con su
     * cantidad y la epica. Solo cuenta lo que ya esta en el reporte; no anade
     * nada que el documento o la semilla no den.
     */
    private String mensajeDeFin(Ejecucion ejecucion) {
        RecompensasDeEjecucion r = ejecucion.recompensas();
        StringBuilder texto = new StringBuilder();
        texto.append(ejecucion.heroe().nombre()).append(" volvió de «").append(nombreDeMision(ejecucion)).append("»");
        if (ejecucion.estado() == EstadoEjecucion.COMPLETADA) {
            texto.append(" con la misión cumplida. Ganó ").append(r.creditos()).append(" créditos y ");
        } else {
            texto.append(" sin completarla: fue derrotado. Derrotó a ")
                    .append(ejecucion.resultado().encuentrosCompletados()).append(" enemigos y ganó ");
        }
        texto.append(String.format(ESPANOL, "%.1f", r.experiencia())).append(" puntos de experiencia.");
        if (ejecucion.nivelAlcanzado() != null && ejecucion.nivelAlcanzado() > ejecucion.heroe().nivel()) {
            texto.append(" ¡Subió al nivel ").append(ejecucion.nivelAlcanzado()).append("!");
        }
        if (!r.productos().isEmpty()) {
            texto.append(" Objetos: ").append(r.productos().stream()
                    .map(p -> "«" + p.nombre() + "» ×" + p.cantidad())
                    .collect(Collectors.joining(", "))).append(".");
        }
        if (!r.epicas().isEmpty()) {
            texto.append(" Aprendió la épica «").append(r.epicas().getFirst().nombre()).append("».");
        }
        List<RecompensasDeEjecucion.SinEntregar> sinEntregar = r.sinEntregar();
        if (!sinEntregar.isEmpty()) {
            texto.append(" Algunas recompensas no se pudieron entregar; el reporte explica por qué.");
        }
        texto.append(" El reporte completo está en Misiones, en tu historial.");
        return texto.toString();
    }

    private static String asuntoDeEpica(RecompensasDeEjecucion recompensas) {
        return "Obtuviste la épica «" + recompensas.epicas().getFirst().nombre() + "»";
    }

    private String mensajeDeEpica(Ejecucion ejecucion) {
        RecompensasDeEjecucion.EpicaGanada epica = ejecucion.recompensas().epicas().getFirst();
        return ejecucion.heroe().nombre() + " derrotó a " + epica.master() + " en «" + nombreDeMision(ejecucion)
                + "» y aprendió su épica «" + epica.nombre() + "». "
                + (epica.entregable()
                        ? "Ya está en tu inventario."
                        : "Queda en tu colección de épicas de Máster.");
    }

    static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}

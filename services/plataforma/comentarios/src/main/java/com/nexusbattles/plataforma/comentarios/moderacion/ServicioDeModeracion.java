package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ResumenDeComentario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El flujo de moderacion de comentarios, de punta a punta — R10.1, ampliado en
 * B3 con EDITAR, MARCAR y DESMARCAR (7.3.3).
 *
 * <h2>El defecto de producto que cierra</h2>
 *
 * El estado EN_REVISION existia desde V1 y el filtro automatico de RF-COM-007
 * metia comentarios ahi. Lo que no existia era la salida: ni cola donde
 * aparecieran, ni accion que los resolviera. Un comentario retenido se quedaba
 * invisible para siempre y su autor no sabia por que. No era un fallo de
 * codigo —nada reventaba— sino un camino que nadie habia terminado de dibujar.
 *
 * <h2>Las cinco historias son un flujo, no cinco islas</h2>
 *
 * <pre>
 *   jugador reporta (RF-COM-006)
 *     -> se agrupa con los demas reportes del comentario
 *     -> aparece en la cola (RF-COM-005), SIGUIENDO PUBLICADO
 *     -> el moderador lo abre con su contexto
 *     -> decide (RF-COM-008): aprobar, ocultar, eliminar, editar...
 *     -> queda el asiento
 *     -> se avisa al autor
 * </pre>
 *
 * <p>Por eso viven en un solo servicio: implementarlas por separado habria
 * dejado otra vez una cola sin salida o una accion sin cola.
 *
 * <h2>Encolar no es ocultar (contrato 1.8.0, auditoria de DEV del 30-sep)</h2>
 *
 * Hasta la 1.7.0 el PRIMER reporte pasaba el comentario a EN_REVISION y lo
 * sacaba del hilo para todos: en DEV un solo reporte bajo el contador de
 * «Espada de una mano» de 28 a 27 opiniones. La ficha (RF-COM-006, CA-01 y
 * CA-04) pide otra cosa: registrar el reporte, agruparlo e incorporar el
 * comentario a la cola, «sujeto a revision». Ocultar es una decision del
 * moderador (RF-COM-008), no el efecto de una sola persona pulsando un boton.
 *
 * <p>Ahora el comentario reportado sigue a la vista y entra en la cola por
 * sus reportes PENDIENTES (los posteriores a la ultima decision que los
 * atiende). Solo si el Product Owner fija un umbral de ocultamiento
 * ({@code comentarios.reportes.umbral-ocultamiento}, D-36; 0 = nunca) y los
 * reportes pendientes lo alcanzan, el comentario se retira de la vista
 * mientras se revisa. Lo que el filtro automatico retiene al publicar
 * (RF-COM-007) sigue entrando en EN_REVISION, como siempre.
 */
@Service
public class ServicioDeModeracion {

    /** Contrato: {@code motivo} de 3 a 500 caracteres (la columna es de 500). */
    static final int MOTIVO_MINIMO = 3;
    static final int MOTIVO_MAXIMO = 500;

    /** Contrato: {@code descripcion} del reporte de 0 a 500 caracteres (la columna es de 500). */
    static final int DESCRIPCION_MAXIMA = 500;

    /** Contrato 1.4.0: {@code textoNuevo} de 1 a 2000 caracteres. */
    static final int TEXTO_NUEVO_MAXIMO = 2000;

    /** Contrato 1.7.0: el historial del autor admite paginas de hasta 100, el mismo tope que la cola. */
    static final int TAMANO_MAXIMO_DEL_HISTORIAL = 100;

    /**
     * Del mas reciente al mas antiguo y, a igual fecha, por id: sin el segundo
     * criterio dos comentarios del mismo instante podrian salir en dos paginas
     * o en ninguna.
     */
    private static final Sort ORDEN_DEL_HISTORIAL =
            Sort.by(Sort.Order.desc("fechaPublicacion"), Sort.Order.desc("id"));

    /** Los estados que se pueden seguir mirando: un ELIMINADO ya no tiene seguimiento. */
    private static final Set<Comentario.Estado> ESTADOS_CON_SEGUIMIENTO = EnumSet.of(
            Comentario.Estado.PUBLICADO, Comentario.Estado.EN_REVISION, Comentario.Estado.OCULTO);

    private final ComentarioRepository comentarios;
    private final ReporteRepository reportes;
    private final AsientoRepository asientos;
    private final AvisoAlAutor aviso;
    private final RegistroDeAuditoria auditoria;
    private final Clock reloj;
    private final int limiteDiarioDeReportes;
    private final int umbralDePrioridad;
    private final int umbralDeOcultamiento;

    public ServicioDeModeracion(
            ComentarioRepository comentarios,
            ReporteRepository reportes,
            AsientoRepository asientos,
            AvisoAlAutor aviso,
            RegistroDeAuditoria auditoria,
            Clock reloj,
            @Value("${comentarios.reportes.maximo-por-usuario-por-dia:20}") int limiteDiarioDeReportes,
            @Value("${comentarios.reportes.umbral-prioridad-elevada:0}") int umbralDePrioridad,
            @Value("${comentarios.reportes.umbral-ocultamiento:0}") int umbralDeOcultamiento) {
        this.comentarios = comentarios;
        this.reportes = reportes;
        this.asientos = asientos;
        this.aviso = aviso;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.limiteDiarioDeReportes = limiteDiarioDeReportes;
        this.umbralDePrioridad = umbralDePrioridad;
        this.umbralDeOcultamiento = umbralDeOcultamiento;
    }

    /**
     * Contrato 1.8.0: cuantos reportes PENDIENTES retiran el comentario de la
     * vista mientras se revisa. La ficha no fija ninguno, asi que nace en 0 =
     * nunca: el comentario reportado sigue publicado y es el moderador quien
     * decide (D-36, pendiente del Product Owner). Mismo criterio que
     * {@link #elevaLaPrioridad}: 0 o menos no oculta nunca.
     */
    private boolean seOcultaPorReportes(long reportesPendientes) {
        return umbralDeOcultamiento > 0 && reportesPendientes >= umbralDeOcultamiento;
    }

    /**
     * CA-02: cuantos reportes elevan la prioridad en la cola. El PO aun no fija
     * el valor, asi que nace en 0 = sin umbral, y 0 (o menos) no eleva nunca.
     * Se deriva del conteo y no se guarda: no hay estado que pueda quedar a
     * medias y, si el umbral cambia, la cola lo refleja sin migrar nada.
     */
    private boolean elevaLaPrioridad(long reportesDelComentario) {
        return umbralDePrioridad > 0 && reportesDelComentario >= umbralDePrioridad;
    }

    // ------------------------------------------------------------ RF-COM-006

    /**
     * Un jugador marca un comentario. El reportante sale del token.
     *
     * <p>El limite por usuario lo exige la ficha sin fijar su valor
     * ("[LIMITE DE REPORTES POR USUARIO POR DEFINIR]"), asi que aqui es un
     * parametro y no una constante: cuando el Product Owner decida, se cambia
     * la configuracion, no el codigo.
     *
     * <p>El duplicado se comprueba ANTES por cortesia —para dar un 409 claro—
     * y se vuelve a atrapar DESPUES por seguridad: dos peticiones simultaneas
     * del mismo usuario cargan cada una un estado que no ve a la otra, y el
     * indice unico de V4 es el que de verdad lo impide. Se guarda con
     * {@code saveAndFlush} para que el INSERT salga aqui: con el id asignado a
     * mano {@code save} hace un merge y difiere el INSERT al commit, cuando el
     * {@code catch} ya no esta en pila y el cliente recibiria el 409 generico.
     *
     * <p>La entrada se valida primero, antes de tocar la base y de gastar cupo:
     * sin categoria o con la descripcion de mas de 500 caracteres es
     * {@link ReporteInvalido}. Una descripcion en blanco es {@code null}.
     */
    @Transactional
    public Reportado reportar(String productoId, String comentarioId, String reportanteId,
            CategoriaDeReporte categoria, String descripcion) {

        exigirCategoria(categoria);
        String descripcionLimpia = limpiarDescripcion(descripcion);

        RegistroDeComentario registro = comentarios.findById(comentarioId)
                .filter(c -> c.getProductoId().equals(productoId))
                .orElseThrow(() -> new ComentarioNoEncontrado(comentarioId));

        Comentario comentario = registro.aDominio();
        if (comentario.estaEliminado()) {
            // Reportar algo que ya no existe no es un error del usuario, pero
            // tampoco tiene efecto: no se encola lo que nadie puede ver.
            throw new ComentarioNoEncontrado(comentarioId);
        }

        if (reportes.existsByComentarioIdAndReportanteId(comentarioId, reportanteId)) {
            throw new ReporteDuplicado(comentarioId);
        }

        Instant ahora = Instant.now(reloj);
        long hechosHoy = reportes.countByReportanteIdAndFechaAfter(
                reportanteId, ahora.minus(Duration.ofDays(1)));
        if (hechosHoy >= limiteDiarioDeReportes) {
            throw new LimiteDeReportesAgotado(limiteDiarioDeReportes);
        }

        RegistroDeReporte reporte = new RegistroDeReporte(
                UUID.randomUUID().toString(), comentarioId, reportanteId,
                categoria, descripcionLimpia, ahora);
        try {
            reportes.saveAndFlush(reporte);
        } catch (DataIntegrityViolationException carrera) {
            // El indice unico de V4 gano la carrera. Es el mismo 409.
            throw new ReporteDuplicado(comentarioId);
        }

        // Contrato 1.8.0: el reporte ya lo pone en la cola por si solo (tiene un
        // reporte pendiente) y el comentario SIGUE PUBLICADO. Solo un umbral de
        // ocultamiento fijado por el PO lo retira de la vista mientras se revisa.
        Comentario resultante = comentario;
        if (comentario.estaPublicado() && umbralDeOcultamiento > 0
                && seOcultaPorReportes(reportes.contarPendientes(comentarioId, AccionDeModeracion.RESUELVEN_REPORTES))) {
            resultante = comentario.con(Comentario.Estado.EN_REVISION);
            comentarios.save(RegistroDeComentario.desde(resultante));
        }

        long totales = reportes.countByComentarioId(comentarioId);
        return new Reportado(reporte, resultante, totales, elevaLaPrioridad(totales));
    }

    private static void exigirCategoria(CategoriaDeReporte categoria) {
        if (categoria == null) {
            throw new ReporteInvalido("Falta la categoria del reporte");
        }
    }

    private static String limpiarDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String limpia = descripcion.strip();
        if (limpia.isEmpty()) {
            return null;
        }
        if (limpia.length() > DESCRIPCION_MAXIMA) {
            throw new ReporteInvalido(
                    "La descripcion admite hasta " + DESCRIPCION_MAXIMA + " caracteres");
        }
        return limpia;
    }

    // ------------------------------------------------------------ RF-COM-005

    /**
     * La cola priorizada. Vacia es {@code 200} con lista vacia, no un 404: no
     * tener trabajo pendiente es una respuesta correcta (CA-03 de la ficha).
     *
     * <p>{@code marcado} (B3, 7.3.3) elige que se mira:
     * <ul>
     *   <li>sin filtro: la cola de siempre, los EN_REVISION y, desde la 1.8.0,
     *       los PUBLICADOS con reportes pendientes;</li>
     *   <li>{@code true}: la lista de seguimiento especial, los marcados en
     *       cualquier estado salvo ELIMINADO —un comentario aprobado pero
     *       marcado sigue necesitando que alguien lo mire—;</li>
     *   <li>{@code false}: lo mismo que sin filtro, pero sin los marcados.</li>
     * </ul>
     *
     * <p>Contrato 1.10.0: {@code categoria} y {@code prioridadElevada} filtran
     * ademas, y se combinan por Y con los anteriores:
     * <ul>
     *   <li>{@code categoria}: solo los comentarios con al menos un reporte de
     *       esa categoria (la cuenta de {@code porCategoria}; un EN_REVISION sin
     *       reportes no sale);</li>
     *   <li>{@code prioridadElevada}: {@code true} solo los de prioridad
     *       elevada, {@code false} los demas.</li>
     * </ul>
     * Se aplican en memoria sobre los candidatos que la cola ya carga, antes de
     * ordenar y paginar, para que {@code total} sea el de la cola ya filtrada.
     * La prioridad no se guarda, se deriva del conteo, asi que filtrar sobre la
     * entrada usa el mismo valor que ve el cliente. Si la cola crece mucho, habra
     * que mover los filtros a la consulta.
     *
     * <p>Los reportes de todos los comentarios de la cola se leen en una sola
     * consulta, no uno por comentario.
     */
    @Transactional(readOnly = true)
    public Cola cola(String productoId, Boolean marcado, CategoriaDeReporte categoria,
            Boolean prioridadElevada, int pagina, int tamano) {
        List<Comentario> candidatos = candidatosDeLaCola(productoId, marcado).stream()
                .map(RegistroDeComentario::aDominio)
                .toList();

        Map<String, List<RegistroDeReporte>> reportesPorComentario = new LinkedHashMap<>();
        if (!candidatos.isEmpty()) {
            for (RegistroDeReporte reporte : reportes.findByComentarioIdInOrderByFechaAsc(
                    candidatos.stream().map(Comentario::id).toList())) {
                reportesPorComentario.computeIfAbsent(reporte.comentarioId(), id -> new ArrayList<>()).add(reporte);
            }
        }

        List<Entrada> entradas = new ArrayList<>();
        for (Comentario c : candidatos) {
            List<RegistroDeReporte> suyos = reportesPorComentario.getOrDefault(c.id(), List.of());
            Map<CategoriaDeReporte, Long> porCategoria = new EnumMap<>(CategoriaDeReporte.class);
            for (RegistroDeReporte r : suyos) {
                porCategoria.merge(r.categoria(), 1L, Long::sum);
            }
            Instant primero = suyos.isEmpty() ? c.fechaPublicacion() : suyos.get(0).fecha();
            boolean elevada = elevaLaPrioridad(suyos.size());
            if (categoria != null && !porCategoria.containsKey(categoria)) {
                continue;
            }
            if (prioridadElevada != null && prioridadElevada != elevada) {
                continue;
            }
            entradas.add(new Entrada(c, suyos.size(), porCategoria, primero, elevada));
        }

        // Mas reportado primero; a igualdad, el que lleva mas tiempo esperando.
        // Es lo que pide "cola priorizada" y tambien lo justo: lo que mas gente
        // marco y lleva mas rato sin atenderse va antes.
        entradas.sort(Comparator
                .comparingInt(Entrada::reportes).reversed()
                .thenComparing(Entrada::primerReporte));

        int total = entradas.size();
        int desde = Math.min(pagina * tamano, total);
        int hasta = Math.min(desde + tamano, total);
        return new Cola(entradas.subList(desde, hasta), total, pagina, tamano);
    }

    private List<RegistroDeComentario> candidatosDeLaCola(String productoId, Boolean marcado) {
        if (Boolean.TRUE.equals(marcado)) {
            return productoId == null
                    ? comentarios.findByMarcadoTrueAndEstadoInOrderByFechaPublicacionAsc(ESTADOS_CON_SEGUIMIENTO)
                    : comentarios.findByMarcadoTrueAndEstadoInAndProductoIdOrderByFechaPublicacionAsc(
                            ESTADOS_CON_SEGUIMIENTO, productoId);
        }
        List<RegistroDeComentario> enRevision;
        if (Boolean.FALSE.equals(marcado)) {
            enRevision = productoId == null
                    ? comentarios.findByEstadoAndMarcadoOrderByFechaPublicacionAsc(
                            Comentario.Estado.EN_REVISION, false)
                    : comentarios.findByEstadoAndMarcadoAndProductoIdOrderByFechaPublicacionAsc(
                            Comentario.Estado.EN_REVISION, false, productoId);
        } else {
            enRevision = productoId == null
                    ? comentarios.findByEstadoOrderByFechaPublicacionAsc(Comentario.Estado.EN_REVISION)
                    : comentarios.findByEstadoAndProductoIdOrderByFechaPublicacionAsc(
                            Comentario.Estado.EN_REVISION, productoId);
        }

        List<RegistroDeComentario> reportadosALaVista = publicadosConReportesPendientes(productoId, marcado);
        if (reportadosALaVista.isEmpty()) {
            return enRevision;
        }
        List<RegistroDeComentario> todos = new ArrayList<>(enRevision);
        todos.addAll(reportadosALaVista);
        return todos;
    }

    /**
     * Contrato 1.8.0: los PUBLICADOS con algun reporte posterior a la ultima
     * decision que atiende reportes. Siguen a la vista del jugador y esperan
     * igual la decision del moderador (RF-COM-006 CA-01: «incorpora el
     * comentario a la cola de moderacion»).
     */
    private List<RegistroDeComentario> publicadosConReportesPendientes(String productoId, Boolean marcado) {
        List<String> pendientes = reportes.comentariosConReportesPendientes(AccionDeModeracion.RESUELVEN_REPORTES);
        if (pendientes.isEmpty()) {
            return List.of();
        }
        return comentarios.findByIdInAndEstadoOrderByFechaPublicacionAsc(pendientes, Comentario.Estado.PUBLICADO)
                .stream()
                .filter(r -> productoId == null || productoId.equals(r.getProductoId()))
                .filter(r -> !Boolean.FALSE.equals(marcado) || !r.aDominio().marcado())
                .toList();
    }

    /** Un comentario en revision con todo lo que el moderador necesita para decidir. */
    @Transactional(readOnly = true)
    public Detalle detalle(String comentarioId) {
        Comentario comentario = comentarios.findById(comentarioId)
                .map(RegistroDeComentario::aDominio)
                .orElseThrow(() -> new ComentarioNoEncontrado(comentarioId));
        return new Detalle(
                comentario,
                reportes.findByComentarioIdOrderByFechaAsc(comentarioId),
                asientos.findByComentarioIdOrderByFechaAsc(comentarioId));
    }

    /**
     * El historial de comentarios de un autor, del mas reciente al mas antiguo —
     * RF-COM-005, contrato 1.7.0. Solo lectura y con datos de este servicio.
     *
     * <p>Incluye los comentarios en cualquier estado, tambien OCULTO y ELIMINADO
     * (los retirados por el propio autor incluidos), cada uno con su estado. La
     * pagina y el orden los resuelve la base, no una lista en memoria. Un autor
     * sin comentarios es un historial vacio con total 0, no un error.
     *
     * <p>Los parametros fuera de rango se corrigen en vez de rechazarse: la
     * pagina negativa pasa a 0, el tamano menor que 1 a 1 y el que pasa de
     * {@link #TAMANO_MAXIMO_DEL_HISTORIAL} se recorta: el cliente no decide
     * cuanto carga el servidor.
     */
    @Transactional(readOnly = true)
    public Historial historialDelAutor(String autorId, int pagina, int tamano) {
        int paginaEfectiva = Math.max(pagina, 0);
        int tamanoEfectivo = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_DEL_HISTORIAL);

        Page<ResumenDeComentario> leida = comentarios.findByAutorId(
                autorId, PageRequest.of(paginaEfectiva, tamanoEfectivo, ORDEN_DEL_HISTORIAL));

        String apodo = leida.isEmpty() ? null : leida.getContent().get(0).apodoAutor();
        return new Historial(autorId, apodo, leida.getContent(), leida.getTotalElements(),
                paginaEfectiva, tamanoEfectivo);
    }

    // ------------------------------------------------------------ RF-COM-008

    /**
     * La decision, con su asiento.
     *
     * <p>El orden importa y es deliberado: primero se comprueba que la
     * transicion valga, luego se guarda el estado nuevo y el asiento en la
     * MISMA transaccion, y solo despues se avisa y se audita. Auditar o
     * notificar antes de confirmar dejaria constancia de algo que pudo no
     * ocurrir.
     *
     * <p>El aviso y la auditoria son fail-open a proposito (HU-DIS-003): un
     * servicio de avisos caido no puede impedir que se retire un comentario
     * ofensivo. Lo que si queda es el rastro de que no salio.
     *
     * @param textoNuevo solo con EDITAR (obligatorio ahi); en las demas se ignora
     * @param ipOrigen   IP de la peticion, para el asiento (puede ser nula)
     */
    @Transactional
    public Resuelto resolver(String comentarioId, String moderadorId, String apodoModerador,
            AccionDeModeracion accion, String motivo, String textoNuevo, String ipOrigen) {

        if (accion == null) {
            throw new DecisionIncompleta("Falta la accion de moderacion");
        }
        exigirMotivo(motivo);
        if (accion == AccionDeModeracion.EDITAR) {
            exigirTextoNuevo(textoNuevo);
        }

        Comentario comentario = comentarios.findById(comentarioId)
                .map(RegistroDeComentario::aDominio)
                .orElseThrow(() -> new ComentarioNoEncontrado(comentarioId));

        if (!admite(comentario, accion)) {
            // Es el caso que CA-03 nombra: otro moderador lo resolvio mientras
            // este miraba la pantalla. Nada cambia.
            throw new TransicionInvalida(accion, comentario);
        }

        Decision decision = decidir(comentario, accion, moderadorId, apodoModerador, motivo,
                textoNuevo, ipOrigen);
        comentarios.save(RegistroDeComentario.desde(decision.resultante()));
        asientos.save(decision.asiento());

        // Aprobar uno que siguio a la vista no cambia nada para su autor: avisarle
        // solo le diria que alguien lo reporto. Queda el asiento y la auditoria.
        boolean avisado = accion.seAvisaAlAutor() && !decision.cierraReportesALaVista()
                && aviso.notificar(decision.resultante(), decision.asiento());
        auditoria.registrar(decision.asiento());

        return new Resuelto(decision.resultante(), decision.asiento(), avisado);
    }

    /**
     * Si la accion vale sobre el comentario tal como esta ahora: la transicion
     * del enum y, contrato 1.8.0, que aprobar uno que sigue publicado solo sirve
     * para cerrar sus reportes pendientes. Si ya no tiene ninguno, otro
     * moderador se adelanto (CA-03): el mismo 409, sin asiento.
     */
    private boolean admite(Comentario comentario, AccionDeModeracion accion) {
        if (!accion.aplicableA(comentario)) {
            return false;
        }
        return !cierraReportesALaVista(comentario, accion)
                || reportes.contarPendientes(comentario.id(), AccionDeModeracion.RESUELVEN_REPORTES) != 0;
    }

    private static boolean cierraReportesALaVista(Comentario comentario, AccionDeModeracion accion) {
        return accion == AccionDeModeracion.APROBAR && comentario.estado() == Comentario.Estado.PUBLICADO;
    }

    /**
     * El comentario despues de la accion y su asiento, sin guardar nada. Quien
     * llama ya comprobo {@link #admite}.
     */
    private Decision decidir(Comentario comentario, AccionDeModeracion accion, String moderadorId,
            String apodoModerador, String motivo, String textoNuevo, String ipOrigen) {
        Comentario resultante = accion.aplicarA(comentario, textoNuevo);

        boolean edita = accion == AccionDeModeracion.EDITAR;
        AsientoDeModeracion asiento = new AsientoDeModeracion(
                UUID.randomUUID().toString(), comentario.id(), moderadorId, apodoModerador,
                accion, motivo, comentario.estado(), resultante.estado(), Instant.now(reloj),
                edita ? comentario.texto() : null,
                edita ? resultante.texto() : null,
                ipOrigen);
        return new Decision(resultante, asiento, cierraReportesALaVista(comentario, accion));
    }

    private static void exigirMotivo(String motivo) {
        if (motivo == null || motivo.strip().length() < MOTIVO_MINIMO || motivo.length() > MOTIVO_MAXIMO) {
            throw new MotivoRequerido();
        }
    }

    private static void exigirTextoNuevo(String textoNuevo) {
        if (textoNuevo == null || textoNuevo.isBlank() || textoNuevo.length() > TEXTO_NUEVO_MAXIMO) {
            throw new DecisionIncompleta(
                    "EDITAR necesita textoNuevo, de 1 a " + TEXTO_NUEVO_MAXIMO + " caracteres: el texto que queda visible");
        }
    }

    // ------------------------------------------------------------- resultados

    public record Reportado(RegistroDeReporte reporte, Comentario comentario, long totales,
            boolean prioridadElevada) {
    }

    public record Entrada(Comentario comentario, int reportes,
            Map<CategoriaDeReporte, Long> porCategoria, Instant primerReporte,
            boolean prioridadElevada) {
    }

    public record Cola(List<Entrada> entradas, int total, int pagina, int tamano) {
    }

    public record Detalle(Comentario comentario, List<RegistroDeReporte> reportes,
            List<AsientoDeModeracion> historial) {
    }

    /** {@code apodoAutor} es el del comentario mas reciente de la pagina; nulo si no hay ninguno. */
    public record Historial(String autorId, String apodoAutor, List<ResumenDeComentario> comentarios,
            long total, int pagina, int tamano) {
    }

    public record Resuelto(Comentario comentario, AsientoDeModeracion asiento, boolean autorNotificado) {
    }

    /** Lo que una accion decide para un comentario, antes de guardarlo, avisar o auditar. */
    private record Decision(Comentario resultante, AsientoDeModeracion asiento,
            boolean cierraReportesALaVista) {
    }

    // ------------------------------------------------------------- excepciones

    public static class ComentarioNoEncontrado extends RuntimeException {
        public ComentarioNoEncontrado(String id) {
            super("No existe el comentario " + id);
        }
    }

    public static class ReporteDuplicado extends RuntimeException {
        public ReporteDuplicado(String id) {
            super("Ya reportaste el comentario " + id);
        }
    }

    /** El reporte no cumple el contrato: categoria ausente o descripcion demasiado larga (400). */
    public static class ReporteInvalido extends RuntimeException {
        public ReporteInvalido(String explicacion) {
            super(explicacion);
        }
    }

    public static class LimiteDeReportesAgotado extends RuntimeException {
        public LimiteDeReportesAgotado(int limite) {
            super("Alcanzaste el limite de " + limite + " reportes en un dia");
        }
    }

    /** A la decision le falta algo que el contrato exige (400). */
    public static class DecisionIncompleta extends RuntimeException {
        public DecisionIncompleta(String explicacion) {
            super(explicacion);
        }
    }

    public static class MotivoRequerido extends DecisionIncompleta {
        public MotivoRequerido() {
            super("Toda decision de moderacion necesita un motivo, de " + MOTIVO_MINIMO + " a "
                    + MOTIVO_MAXIMO + " caracteres");
        }
    }

    public static class TransicionInvalida extends RuntimeException {
        public TransicionInvalida(AccionDeModeracion accion, Comentario actual) {
            super(explicar(accion, actual));
        }

        private static String explicar(AccionDeModeracion accion, Comentario actual) {
            if (accion == AccionDeModeracion.MARCAR && actual.marcado()) {
                return "El comentario ya esta marcado";
            }
            if (accion == AccionDeModeracion.DESMARCAR && !actual.marcado()) {
                return "El comentario no esta marcado";
            }
            if (accion == AccionDeModeracion.APROBAR && actual.estaPublicado()) {
                return "El comentario sigue publicado y ya no tiene reportes pendientes";
            }
            return "No se puede " + accion + " un comentario que esta en " + actual.estado();
        }
    }
}

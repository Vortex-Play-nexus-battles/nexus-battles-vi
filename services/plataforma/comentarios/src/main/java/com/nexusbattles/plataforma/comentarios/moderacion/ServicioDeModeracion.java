package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El flujo de moderacion de comentarios, de punta a punta — R10.1.
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
 *     -> comentario a EN_REVISION
 *     -> aparece en la cola (RF-COM-005)
 *     -> el moderador lo abre con su contexto
 *     -> decide (RF-COM-008)
 *     -> queda el asiento
 *     -> se avisa al autor
 * </pre>
 *
 * <p>Por eso viven en un solo servicio: implementarlas por separado habria
 * dejado otra vez una cola sin salida o una accion sin cola.
 */
@Service
public class ServicioDeModeracion {

    private final ComentarioRepository comentarios;
    private final ReporteRepository reportes;
    private final AsientoRepository asientos;
    private final AvisoAlAutor aviso;
    private final RegistroDeAuditoria auditoria;
    private final Clock reloj;
    private final int limiteDiarioDeReportes;

    public ServicioDeModeracion(
            ComentarioRepository comentarios,
            ReporteRepository reportes,
            AsientoRepository asientos,
            AvisoAlAutor aviso,
            RegistroDeAuditoria auditoria,
            Clock reloj,
            @Value("${comentarios.reportes.maximo-por-usuario-por-dia:20}") int limiteDiarioDeReportes) {
        this.comentarios = comentarios;
        this.reportes = reportes;
        this.asientos = asientos;
        this.aviso = aviso;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.limiteDiarioDeReportes = limiteDiarioDeReportes;
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
     * indice unico de V4 es el que de verdad lo impide.
     */
    @Transactional
    public Reportado reportar(String productoId, String comentarioId, String reportanteId,
            CategoriaDeReporte categoria, String descripcion) {

        RegistroDeComentario registro = comentarios.findById(comentarioId)
                .filter(c -> c.aDominio().productoId().equals(productoId))
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
                categoria, descripcion, ahora);
        try {
            reportes.save(reporte);
        } catch (DataIntegrityViolationException carrera) {
            // El indice unico de V4 gano la carrera. Es el mismo 409.
            throw new ReporteDuplicado(comentarioId);
        }

        // El primer reporte encola; los siguientes solo suben la prioridad.
        Comentario resultante = comentario;
        if (comentario.estaPublicado()) {
            resultante = comentario.con(Comentario.Estado.EN_REVISION);
            comentarios.save(RegistroDeComentario.desde(resultante));
        }

        return new Reportado(reporte, resultante, reportes.countByComentarioId(comentarioId));
    }

    // ------------------------------------------------------------ RF-COM-005

    /**
     * La cola priorizada. Vacia es {@code 200} con lista vacia, no un 404: no
     * tener trabajo pendiente es una respuesta correcta (CA-03 de la ficha).
     */
    @Transactional(readOnly = true)
    public Cola cola(String productoId, int pagina, int tamano) {
        List<RegistroDeComentario> enRevision = productoId == null
                ? comentarios.findByEstadoOrderByFechaPublicacionAsc(Comentario.Estado.EN_REVISION)
                : comentarios.findByEstadoAndProductoIdOrderByFechaPublicacionAsc(
                        Comentario.Estado.EN_REVISION, productoId);

        List<Entrada> entradas = new ArrayList<>();
        for (RegistroDeComentario registro : enRevision) {
            Comentario c = registro.aDominio();
            List<RegistroDeReporte> suyos = reportes.findByComentarioIdOrderByFechaAsc(c.id());
            Map<CategoriaDeReporte, Long> porCategoria = new EnumMap<>(CategoriaDeReporte.class);
            for (RegistroDeReporte r : suyos) {
                porCategoria.merge(r.categoria(), 1L, Long::sum);
            }
            Instant primero = suyos.isEmpty() ? c.fechaPublicacion() : suyos.get(0).fecha();
            entradas.add(new Entrada(c, suyos.size(), porCategoria, primero));
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
     */
    @Transactional
    public Resuelto resolver(String comentarioId, String moderadorId, String apodoModerador,
            AccionDeModeracion accion, String motivo) {

        if (motivo == null || motivo.isBlank()) {
            throw new MotivoRequerido();
        }

        Comentario comentario = comentarios.findById(comentarioId)
                .map(RegistroDeComentario::aDominio)
                .orElseThrow(() -> new ComentarioNoEncontrado(comentarioId));

        Comentario.Estado anterior = comentario.estado();
        if (!accion.aplicableDesde(anterior)) {
            // Es el caso que CA-03 nombra: otro moderador lo resolvio mientras
            // este miraba la pantalla. Nada cambia.
            throw new TransicionInvalida(accion, anterior);
        }

        Comentario resultante = comentario.con(accion.destino());
        comentarios.save(RegistroDeComentario.desde(resultante));

        AsientoDeModeracion asiento = new AsientoDeModeracion(
                UUID.randomUUID().toString(), comentarioId, moderadorId, apodoModerador,
                accion, motivo, anterior, accion.destino(), Instant.now(reloj));
        asientos.save(asiento);

        boolean avisado = aviso.notificar(resultante, asiento);
        auditoria.registrar(asiento);

        return new Resuelto(resultante, asiento, avisado);
    }

    // ------------------------------------------------------------- resultados

    public record Reportado(RegistroDeReporte reporte, Comentario comentario, long totales) {
    }

    public record Entrada(Comentario comentario, int reportes,
            Map<CategoriaDeReporte, Long> porCategoria, Instant primerReporte) {
    }

    public record Cola(List<Entrada> entradas, int total, int pagina, int tamano) {
    }

    public record Detalle(Comentario comentario, List<RegistroDeReporte> reportes,
            List<AsientoDeModeracion> historial) {
    }

    public record Resuelto(Comentario comentario, AsientoDeModeracion asiento, boolean autorNotificado) {
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

    public static class LimiteDeReportesAgotado extends RuntimeException {
        public LimiteDeReportesAgotado(int limite) {
            super("Alcanzaste el limite de " + limite + " reportes en un dia");
        }
    }

    public static class MotivoRequerido extends RuntimeException {
        public MotivoRequerido() {
            super("Toda decision de moderacion necesita un motivo");
        }
    }

    public static class TransicionInvalida extends RuntimeException {
        public TransicionInvalida(AccionDeModeracion accion, Comentario.Estado actual) {
            super("No se puede " + accion + " un comentario que esta en " + actual);
        }
    }
}

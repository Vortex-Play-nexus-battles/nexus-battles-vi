package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import com.nexusbattles.ms_cumplimiento.auditoria.exception.ExportacionExcedeMaximoException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogSpecifications.*;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final int TAMANO_PAGINA_EXPORTACION = 500;

    private static final float MARGEN = 40f;
    private static final float ALTO_LINEA = 14f;
    private static final float TAMANO_LETRA_TITULO = 14f;
    private static final float TAMANO_LETRA_CELDA = 8f;
    private static final String FORMATO_FILA = "%-16s %-14s %-16s %-22s %-45s %-15s";
    private static final DateTimeFormatter FORMATO_FECHA_PDF =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /** Los tamanos de las columnas de V1 y V3: lo que no cabe se rechaza con 400, no se recorta en silencio. */
    static final int MAXIMO_ADMINISTRADOR = 64;
    static final int MAXIMO_AFECTADO = 255;
    static final int MAXIMO_MOTIVO = 500;
    static final int MAXIMO_IP = 45;
    static final int MAXIMO_REFERENCIA = 120;

    /** Desfase de reloj admitido para {@code ocurridoEn}: una accion no ocurre en el futuro. */
    static final Duration DESFASE_ADMITIDO = Duration.ofMinutes(5);

    /** Columnas del CSV (1.3.0), en este orden. */
    static final List<String> COLUMNAS_CSV = List.of("id", "fecha_hora", "ocurrido_en", "administrador",
        "tipo_accion", "afectado", "valor_anterior", "valor_nuevo", "motivo", "ip_origen");

    /** Formatos de exportacion (1.3.0): PDF para leer, CSV para analizar. */
    public enum Formato {
        PDF("application/pdf", "pdf"),
        CSV("text/csv;charset=UTF-8", "csv");

        private final String tipoDeContenido;
        private final String extension;

        Formato(String tipoDeContenido, String extension) {
            this.tipoDeContenido = tipoDeContenido;
            this.extension = extension;
        }

        public String tipoDeContenido() {
            return tipoDeContenido;
        }

        public String extension() {
            return extension;
        }

        /** {@code pdf} o {@code csv}, sin distinguir mayusculas; ausente es PDF; otro valor, 400. */
        public static Formato de(String valor) {
            if (valor == null || valor.isBlank()) {
                return PDF;
            }
            try {
                return valueOf(valor.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("formato invalido: " + valor + ". Valores permitidos: pdf, csv");
            }
        }
    }

    /**
     * Los filtros de la consulta y de la exportacion (1.3.0): {@code administradores} y
     * {@code afectados} admiten varios valores y coinciden con cualquiera.
     */
    public record FiltroDeAuditoria(List<String> administradores, List<String> afectados,
                                    AuditActionType tipoAccion, Instant desde, Instant hasta) {
        public FiltroDeAuditoria {
            administradores = administradores == null ? List.of() : List.copyOf(administradores);
            afectados = afectados == null ? List.of() : List.copyOf(afectados);
        }
    }

    /** Una exportacion lista para entregar. */
    public record Exportacion(byte[] contenido, Formato formato) { }

    /** Lo que deja un evento: la entrada y si es nueva o la que ya traia su referencia. */
    public record ResultadoDeRegistro(AuditLog entrada, boolean nueva) { }

    private final AuditLogRepository repository;

    @Value("${cumplimiento.auditoria.exportacion.maximo-registros:10000}")
    private int maximoRegistrosPorExportacion;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public AuditLog registrar(AuditActionType tipoAccion,
                              String administradorId,
                              String afectado,
                              String valorAnterior,
                              String valorNuevo,
                              String motivo,
                              String ipOrigen) {
        return registrar(tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen,
            null, null);
    }

    /** Como el anterior, con el momento de la accion en su origen y la referencia del evento (1.3.0). */
    public AuditLog registrar(AuditActionType tipoAccion,
                              String administradorId,
                              String afectado,
                              String valorAnterior,
                              String valorNuevo,
                              String motivo,
                              String ipOrigen,
                              Instant ocurridoEn,
                              String referencia) {
        try {
            AuditLog entrada = AuditLog.builder()
                .tipoAccion(tipoAccion)
                .administradorId(administradorId)
                .afectado(afectado)
                .valorAnterior(valorAnterior)
                .valorNuevo(valorNuevo)
                .motivo(motivo)
                .ipOrigen(ipOrigen)
                .ocurridoEn(ocurridoEn)
                .referencia(referencia)
                .build();
            return repository.saveAndFlush(entrada);
        } catch (Exception e) {
            log.error("Fallo al escribir registro de auditoría, se cancela la acción administrativa", e);
            throw new AuditWriteException("No se pudo registrar la auditoría; acción cancelada", e);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<AuditLog> consultar(String administradorId,
                                    AuditActionType tipoAccion,
                                    Instant desde,
                                    Instant hasta,
                                    Pageable pageable) {
        return consultar(administradorId, List.of(), tipoAccion, desde, hasta, pageable);
    }

    /**
     * Con {@code afectados} (contrato 1.2.0): solo los registros sobre esa
     * cuenta o ese comentario, para el historial de actividad de la ficha
     * administrativa (§7.3.4). Vacio no filtra.
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<AuditLog> consultar(String administradorId,
                                    Collection<String> afectados,
                                    AuditActionType tipoAccion,
                                    Instant desde,
                                    Instant hasta,
                                    Pageable pageable) {
        return buscar(filtro(administradorId, afectados, tipoAccion, desde, hasta), pageable);
    }

    /**
     * 1.3.0: la consulta con varios administradores a la vez. La misma persona
     * figura con su apodo en las entradas de ms-identidad y con su {@code uid}
     * en las de los servicios de plataforma: la pantalla pide los dos.
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<AuditLog> buscar(FiltroDeAuditoria filtro, Pageable pageable) {
        return repository.findAll(construirEspecificacion(filtro), pageable);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public byte[] exportarPdf(String administradorId,
                              AuditActionType tipoAccion,
                              Instant desde,
                              Instant hasta,
                              String administradorQueExporta,
                              String ipOrigenDeLaExportacion) {
        return exportarPdf(administradorId, List.of(), tipoAccion, desde, hasta,
            administradorQueExporta, ipOrigenDeLaExportacion);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public byte[] exportarPdf(String administradorId,
                              Collection<String> afectados,
                              AuditActionType tipoAccion,
                              Instant desde,
                              Instant hasta,
                              String administradorQueExporta,
                              String ipOrigenDeLaExportacion) {
        return exportar(filtro(administradorId, afectados, tipoAccion, desde, hasta), Formato.PDF,
            administradorQueExporta, ipOrigenDeLaExportacion).contenido();
    }

    /**
     * Exporta lo que cumpla el filtro en PDF o en CSV (1.3.0, §7.3.5 «exportable
     * para analisis y reportes»). Mismo maximo por operacion en los dos
     * formatos; cada exportacion, y cada rechazo, queda registrada.
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Exportacion exportar(FiltroDeAuditoria filtro,
                                Formato formato,
                                String administradorQueExporta,
                                String ipOrigenDeLaExportacion) {
        Specification<AuditLog> spec = construirEspecificacion(filtro);

        long total = repository.count(spec);
        if (total > maximoRegistrosPorExportacion) {
            registrarAccesoDeExportacion(administradorQueExporta, ipOrigenDeLaExportacion, formato,
                "RECHAZADA: " + total + " registros supera el máximo de " + maximoRegistrosPorExportacion);
            throw new ExportacionExcedeMaximoException(total, maximoRegistrosPorExportacion);
        }

        List<AuditLog> todos = new ArrayList<>();
        Pageable pagina = PageRequest.of(0, TAMANO_PAGINA_EXPORTACION, Sort.by("fechaHora").ascending());
        Page<AuditLog> resultado;
        do {
            resultado = repository.findAll(spec, pagina);
            todos.addAll(resultado.getContent());
            pagina = pagina.next();
        } while (resultado.hasNext());

        byte[] contenido = formato == Formato.CSV ? generarCsv(todos) : generarPdf(todos);

        registrarAccesoDeExportacion(administradorQueExporta, ipOrigenDeLaExportacion, formato,
            todos.size() == 1 ? "Exportado 1 registro" : "Exportados " + todos.size() + " registros");

        return new Exportacion(contenido, formato);
    }

    private void registrarAccesoDeExportacion(String administradorQueExporta, String ipOrigen, Formato formato,
                                              String detalle) {
        registrar(
            AuditActionType.EXPORTACION,
            administradorQueExporta,
            "registro-de-auditoria",
            null,
            detalle,
            "Exportación del registro de auditoría en " + formato.name(),
            ipOrigen
        );
    }

    /**
     * CSV de RFC 4180 en UTF-8 con BOM (para que una hoja de calculo lea bien
     * las tildes), todas las celdas entre comillas y fin de linea CRLF.
     */
    static byte[] generarCsv(List<AuditLog> registros) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", COLUMNAS_CSV)).append("\r\n");
        for (AuditLog r : registros) {
            csv.append(String.join(",",
                celda(r.getId()),
                celda(r.getFechaHora() == null ? null : r.getFechaHora().toString()),
                celda(r.getOcurridoEn() == null ? null : r.getOcurridoEn().toString()),
                celda(r.getAdministradorId()),
                celda(r.getTipoAccion() == null ? null : r.getTipoAccion().name()),
                celda(r.getAfectado()),
                celda(r.getValorAnterior()),
                celda(r.getValorNuevo()),
                celda(r.getMotivo()),
                celda(r.getIpOrigen())))
                .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Una celda entre comillas. Lo que empieza por {@code = + - @}, tabulador o
     * retorno se antepone con un apostrofo: un motivo escrito como formula no
     * se ejecuta al abrir el archivo en una hoja de calculo (inyeccion de CSV).
     */
    static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor;
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        return '"' + texto.replace("\"", "\"\"") + '"';
    }

    private byte[] generarPdf(List<AuditLog> registros) {
        try (PDDocument documento = new PDDocument()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteSubtitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            // Monoespaciada: las columnas son de ancho fijo (FORMATO_FILA) y con
            // Helvetica no quedaban alineadas.
            PDType1Font fuenteCelda = new PDType1Font(Standard14Fonts.FontName.COURIER);
            PDType1Font fuenteCabecera = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

            PDPage pagina = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            documento.addPage(pagina);
            float altoPagina = pagina.getMediaBox().getHeight();
            float y = altoPagina - MARGEN;

            PDPageContentStream contenido = new PDPageContentStream(documento, pagina);
            try {
                y = escribirLinea(contenido, fuenteTitulo, TAMANO_LETRA_TITULO, MARGEN, y,
                    "Registro de auditoría — The Nexus Battles VI");
                y = escribirLinea(contenido, fuenteSubtitulo, TAMANO_LETRA_CELDA, MARGEN, y,
                    "Generado el " + FORMATO_FECHA_PDF.format(Instant.now()) + " UTC · "
                        + registros.size() + (registros.size() == 1 ? " registro" : " registros"));
                y -= ALTO_LINEA;
                y = escribirLinea(contenido, fuenteCabecera, TAMANO_LETRA_CELDA, MARGEN, y, encabezados());

                for (AuditLog registro : registros) {
                    if (y < MARGEN + ALTO_LINEA) {
                        contenido.close();
                        pagina = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                        documento.addPage(pagina);
                        contenido = new PDPageContentStream(documento, pagina);
                        y = altoPagina - MARGEN;
                        y = escribirLinea(contenido, fuenteCabecera, TAMANO_LETRA_CELDA, MARGEN, y, encabezados());
                    }
                    y = escribirLinea(contenido, fuenteCelda, TAMANO_LETRA_CELDA, MARGEN, y, filaDe(registro));
                }
            } finally {
                contenido.close();
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de auditoría", e);
        }
    }

    private String encabezados() {
        return String.format(FORMATO_FILA, "Fecha", "Admin", "Tipo", "Afectado", "Motivo", "IP");
    }

    private String filaDe(AuditLog registro) {
        // La hora de la accion; sin ella, la de su registro aqui.
        Instant momento = registro.getOcurridoEn() != null ? registro.getOcurridoEn() : registro.getFechaHora();
        String fecha = momento != null ? FORMATO_FECHA_PDF.format(momento) : "-";
        String admin = abreviar(registro.getAdministradorId(), 14);
        String tipo = registro.getTipoAccion() != null ? registro.getTipoAccion().name() : "-";
        String afectado = abreviar(registro.getAfectado(), 21);
        String motivo = abreviar(registro.getMotivo(), 44);
        String ip = abreviar(registro.getIpOrigen(), 15);
        return String.format(FORMATO_FILA, fecha, admin, tipo, afectado, motivo, ip);
    }

    private float escribirLinea(PDPageContentStream contenido, PDType1Font fuente, float tamanoLetra,
                                float x, float y, String texto) throws IOException {
        contenido.beginText();
        contenido.setFont(fuente, tamanoLetra);
        contenido.newLineAtOffset(x, y);
        contenido.showText(texto);
        contenido.endText();
        return y - ALTO_LINEA;
    }

    private String abreviar(String valor, int maximo) {
        if (valor == null) {
            return "-";
        }
        return valor.length() > maximo ? valor.substring(0, maximo - 1) + "…" : valor;
    }

    private static FiltroDeAuditoria filtro(String administradorId, Collection<String> afectados,
                                            AuditActionType tipoAccion, Instant desde, Instant hasta) {
        List<String> administradores = administradorId == null || administradorId.isBlank()
            ? List.of() : List.of(administradorId);
        return new FiltroDeAuditoria(administradores, afectados == null ? List.of() : List.copyOf(afectados),
            tipoAccion, desde, hasta);
    }

    private Specification<AuditLog> construirEspecificacion(FiltroDeAuditoria filtro) {
        Specification<AuditLog> spec = Specification.where((root, query, cb) -> cb.conjunction());

        Specification<AuditLog> porAdministradores = conAdministradores(filtro.administradores());
        if (porAdministradores != null) {
            spec = spec.and(porAdministradores);
        }

        Specification<AuditLog> sobreAfectados = conAfectado(filtro.afectados());
        if (sobreAfectados != null) {
            spec = spec.and(sobreAfectados);
        }

        if (filtro.tipoAccion() != null) {
            spec = spec.and(conTipoAccion(filtro.tipoAccion()));
        }

        if (filtro.desde() != null || filtro.hasta() != null) {
            spec = spec.and(entreFechas(filtro.desde(), filtro.hasta()));
        }

        return spec;
    }

    public AuditLog registrarDesdeSolicitud(RegistrarAuditoriaRequest solicitud) {
        return registrarEvento(solicitud).entrada();
    }

    /**
     * Un evento de otro servicio (1.3.0).
     *
     * <ul>
     *   <li>Un campo obligatorio vacio o mas largo que su columna es 400: antes
     *       caia dentro del {@code try} de {@link #registrar} y salia como 503,
     *       y quien reintenta los 503 lo reintentaba para siempre.</li>
     *   <li>{@code ocurridoEn} mas alla del desfase admitido es 400: una accion
     *       no ocurre en el futuro.</li>
     *   <li>Con {@code referencia}: si ya hay una entrada con ella, se devuelve
     *       esa y no se crea otra; dos entregas simultaneas chocan con el
     *       indice unico de V3 y la segunda devuelve la de la primera.</li>
     * </ul>
     */
    public ResultadoDeRegistro registrarEvento(RegistrarAuditoriaRequest solicitud) {
        AuditActionType tipoAccion = tipoDe(solicitud.tipoAccion());
        exigirCampos(solicitud);
        String referencia = vacioANulo(solicitud.referencia());
        if (referencia != null) {
            Optional<AuditLog> previa = repository.findByReferencia(referencia);
            if (previa.isPresent()) {
                return new ResultadoDeRegistro(previa.get(), false);
            }
        }
        try {
            return new ResultadoDeRegistro(registrar(tipoAccion, solicitud.administradorId().strip(),
                solicitud.afectado().strip(), solicitud.valorAnterior(), solicitud.valorNuevo(),
                solicitud.motivo().strip(), solicitud.ipOrigen().strip(), solicitud.ocurridoEn(), referencia), true);
        } catch (AuditWriteException fallo) {
            if (referencia != null && fallo.getCause() instanceof DataIntegrityViolationException) {
                Optional<AuditLog> ganadora = repository.findByReferencia(referencia);
                if (ganadora.isPresent()) {
                    return new ResultadoDeRegistro(ganadora.get(), false);
                }
            }
            throw fallo;
        }
    }

    private static AuditActionType tipoDe(String tipoAccion) {
        try {
            return AuditActionType.valueOf(tipoAccion);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "tipoAccion inválido: " + tipoAccion
                    + ". Valores permitidos: " + java.util.Arrays.toString(AuditActionType.values()));
        }
    }

    private static void exigirCampos(RegistrarAuditoriaRequest solicitud) {
        List<String> faltan = new ArrayList<>();
        if (vacioANulo(solicitud.administradorId()) == null) {
            faltan.add("administradorId");
        }
        if (vacioANulo(solicitud.afectado()) == null) {
            faltan.add("afectado");
        }
        if (vacioANulo(solicitud.motivo()) == null) {
            faltan.add("motivo");
        }
        if (vacioANulo(solicitud.ipOrigen()) == null) {
            faltan.add("ipOrigen");
        }
        if (!faltan.isEmpty()) {
            throw new IllegalArgumentException("Faltan campos obligatorios: " + String.join(", ", faltan));
        }
        exigirLargo("administradorId", solicitud.administradorId(), MAXIMO_ADMINISTRADOR);
        exigirLargo("afectado", solicitud.afectado(), MAXIMO_AFECTADO);
        exigirLargo("motivo", solicitud.motivo(), MAXIMO_MOTIVO);
        exigirLargo("ipOrigen", solicitud.ipOrigen(), MAXIMO_IP);
        exigirLargo("referencia", solicitud.referencia(), MAXIMO_REFERENCIA);
        if (solicitud.ocurridoEn() != null && solicitud.ocurridoEn().isAfter(Instant.now().plus(DESFASE_ADMITIDO))) {
            throw new IllegalArgumentException("ocurridoEn no puede estar en el futuro: " + solicitud.ocurridoEn());
        }
    }

    private static void exigirLargo(String campo, String valor, int maximo) {
        if (valor != null && valor.strip().length() > maximo) {
            throw new IllegalArgumentException(campo + " supera los " + maximo + " caracteres");
        }
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}

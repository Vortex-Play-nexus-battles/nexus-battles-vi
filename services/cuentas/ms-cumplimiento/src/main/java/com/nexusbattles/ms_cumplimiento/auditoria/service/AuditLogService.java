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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
        try {
            AuditLog entrada = AuditLog.builder()
                .tipoAccion(tipoAccion)
                .administradorId(administradorId)
                .afectado(afectado)
                .valorAnterior(valorAnterior)
                .valorNuevo(valorNuevo)
                .motivo(motivo)
                .ipOrigen(ipOrigen)
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
        Specification<AuditLog> spec = construirEspecificacion(administradorId, tipoAccion, desde, hasta);
        return repository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public byte[] exportarPdf(String administradorId,
                              AuditActionType tipoAccion,
                              Instant desde,
                              Instant hasta,
                              String administradorQueExporta,
                              String ipOrigenDeLaExportacion) {
        Specification<AuditLog> spec = construirEspecificacion(administradorId, tipoAccion, desde, hasta);

        long total = repository.count(spec);
        if (total > maximoRegistrosPorExportacion) {
            registrarAccesoDeExportacion(administradorQueExporta, ipOrigenDeLaExportacion,
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

        byte[] pdf = generarPdf(todos);

        registrarAccesoDeExportacion(administradorQueExporta, ipOrigenDeLaExportacion,
            "Exportados " + todos.size() + " registros");

        return pdf;
    }

    private void registrarAccesoDeExportacion(String administradorQueExporta, String ipOrigen, String detalle) {
        registrar(
            AuditActionType.EXPORTACION,
            administradorQueExporta,
            "registro-de-auditoria",
            null,
            detalle,
            "Exportación del registro de auditoría (HU-AUD-004)",
            ipOrigen
        );
    }

    private byte[] generarPdf(List<AuditLog> registros) {
        try (PDDocument documento = new PDDocument()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteCelda = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fuenteCabecera = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDPage pagina = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            documento.addPage(pagina);
            float altoPagina = pagina.getMediaBox().getHeight();
            float y = altoPagina - MARGEN;

            PDPageContentStream contenido = new PDPageContentStream(documento, pagina);
            try {
                y = escribirLinea(contenido, fuenteTitulo, TAMANO_LETRA_TITULO, MARGEN, y,
                    "Registro de auditoría — exportación (HU-AUD-004)");
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
        String fecha = registro.getFechaHora() != null ? FORMATO_FECHA_PDF.format(registro.getFechaHora()) : "-";
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

    private Specification<AuditLog> construirEspecificacion(String administradorId,
                                                            AuditActionType tipoAccion,
                                                            Instant desde,
                                                            Instant hasta) {
        Specification<AuditLog> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (administradorId != null && !administradorId.isBlank()) {
            spec = spec.and(conAdministrador(administradorId));
        }

        if (tipoAccion != null) {
            spec = spec.and(conTipoAccion(tipoAccion));
        }

        if (desde != null || hasta != null) {
            spec = spec.and(entreFechas(desde, hasta));
        }

        return spec;
    }

    public AuditLog registrarDesdeSolicitud(RegistrarAuditoriaRequest solicitud) {
        AuditActionType tipoAccion;
        try {
            tipoAccion = AuditActionType.valueOf(solicitud.tipoAccion());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "tipoAccion inválido: " + solicitud.tipoAccion()
                    + ". Valores permitidos: " + java.util.Arrays.toString(AuditActionType.values()));
        }
        return registrar(
            tipoAccion,
            solicitud.administradorId(),
            solicitud.afectado(),
            solicitud.valorAnterior(),
            solicitud.valorNuevo(),
            solicitud.motivo(),
            solicitud.ipOrigen()
        );
    }
}

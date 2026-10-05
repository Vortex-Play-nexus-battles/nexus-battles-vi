package com.nexusbattles.plataforma.notificaciones.catalogo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Hasta donde vio cada jugador los cambios del catalogo — HU-NOT-001, tabla
 * {@code cursor_alertas_catalogo} (V3).
 *
 * <p>{@code hasta} es la marca que devolvio productos en el ultimo lote
 * incorporado y se envia como {@code desde} en la siguiente consulta;
 * {@code consultadoEn} es cuando se pregunto por ultima vez, para no llamar a
 * productos en cada pagina que abre el jugador (intervalo minimo).
 *
 * <p>Se escribe con las sentencias de {@link CursorCatalogoRepository}, que
 * deciden en la base: el cursor solo avanza aunque dos sesiones escriban a la
 * vez. Esta clase solo se lee.
 */
@Entity
@Table(name = "cursor_alertas_catalogo")
public class RegistroDeCursorCatalogo {

    @Id
    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(name = "hasta", nullable = false)
    private Instant hasta;

    @Column(name = "consultado_en", nullable = false)
    private Instant consultadoEn;

    protected RegistroDeCursorCatalogo() {
    }

    public RegistroDeCursorCatalogo(String usuarioId, Instant hasta, Instant consultadoEn) {
        this.usuarioId = usuarioId;
        this.hasta = hasta;
        this.consultadoEn = consultadoEn;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Instant getHasta() {
        return hasta;
    }

    public Instant getConsultadoEn() {
        return consultadoEn;
    }
}

package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;

import com.nexusbattles.plataforma.comentarios.Comentario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lo que quedo escrito de una decision de moderacion — RF-COM-008.
 *
 * <p>La ficha pide cinco cosas concretas: "el usuario que la realizo, la fecha
 * y hora, el motivo y los estados anterior y nuevo del comentario". Las cinco
 * son columnas, no un texto libre: un historico que hay que interpretar
 * leyendo frases no es trazabilidad, es prosa.
 *
 * <p>El moderador sale del token. Un asiento que dijera quien actuo a partir
 * de un campo del cuerpo no valdria para nada: cualquiera podria firmar una
 * decision con el nombre de otro, que es justo lo contrario de lo que un
 * historico de moderacion existe para garantizar.
 */
@Entity
@Table(name = "comentario_moderacion")
public class AsientoDeModeracion {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "comentario_id", nullable = false, length = 36)
    private String comentarioId;

    @Column(name = "moderador_id", nullable = false, length = 64)
    private String moderadorId;

    @Column(name = "apodo_moderador", nullable = false, length = 100)
    private String apodoModerador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionDeModeracion accion;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 20)
    private Comentario.Estado estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private Comentario.Estado estadoNuevo;

    @Column(nullable = false)
    private Instant fecha;

    protected AsientoDeModeracion() {
    }

    public AsientoDeModeracion(String id, String comentarioId, String moderadorId,
            String apodoModerador, AccionDeModeracion accion, String motivo,
            Comentario.Estado estadoAnterior, Comentario.Estado estadoNuevo, Instant fecha) {
        this.id = id;
        this.comentarioId = comentarioId;
        this.moderadorId = moderadorId;
        this.apodoModerador = apodoModerador;
        this.accion = accion;
        this.motivo = motivo;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.fecha = fecha;
    }

    public String id() {
        return id;
    }

    public String comentarioId() {
        return comentarioId;
    }

    public String moderadorId() {
        return moderadorId;
    }

    public String apodoModerador() {
        return apodoModerador;
    }

    public AccionDeModeracion accion() {
        return accion;
    }

    public String motivo() {
        return motivo;
    }

    public Comentario.Estado estadoAnterior() {
        return estadoAnterior;
    }

    public Comentario.Estado estadoNuevo() {
        return estadoNuevo;
    }

    public Instant fecha() {
        return fecha;
    }
}

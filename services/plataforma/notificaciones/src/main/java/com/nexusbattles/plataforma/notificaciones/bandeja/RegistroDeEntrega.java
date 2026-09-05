package com.nexusbattles.plataforma.notificaciones.bandeja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Deja constancia de que una sesion ya recibio un aviso. */
@Entity
@Table(name = "notificacion_entregas")
public class RegistroDeEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fila_id")
    private Long filaId;

    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(name = "aviso_id", nullable = false, length = 64)
    private String avisoId;

    @Column(name = "sesion_id", nullable = false, length = 128)
    private String sesionId;

    protected RegistroDeEntrega() {
    }

    RegistroDeEntrega(String usuarioId, String avisoId, String sesionId) {
        this.usuarioId = usuarioId;
        this.avisoId = avisoId;
        this.sesionId = sesionId;
    }

    public String getAvisoId() {
        return avisoId;
    }

    public String getSesionId() {
        return sesionId;
    }
}

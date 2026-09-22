package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Forma persistida de un aviso, mapeada a la tabla que crea la migracion V1.
 *
 * <p>Se separa del registro de dominio a proposito. El dominio es inmutable y
 * valida sus reglas, mientras que esta clase solo sabe guardarse y volver. El
 * identificador de fila es subrogado porque el aviso solo es unico dentro de la
 * bandeja de su dueno, no en toda la tabla.
 */
@Entity
@Table(name = "notificaciones")
public class RegistroDeNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fila_id")
    private Long filaId;

    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(name = "aviso_id", nullable = false, length = 64)
    private String avisoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false)
    private String cuerpo;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(nullable = false)
    private boolean leida;

    protected RegistroDeNotificacion() {
    }

    RegistroDeNotificacion(String usuarioId, String avisoId, String tipo, String titulo,
            String cuerpo, Instant creadaEn, boolean leida) {
        this.usuarioId = usuarioId;
        this.avisoId = avisoId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.cuerpo = cuerpo;
        this.creadaEn = creadaEn;
        this.leida = leida;
    }

    public String getAvisoId() {
        return avisoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public boolean isLeida() {
        return leida;
    }

    void marcarLeida() {
        this.leida = true;
    }
}

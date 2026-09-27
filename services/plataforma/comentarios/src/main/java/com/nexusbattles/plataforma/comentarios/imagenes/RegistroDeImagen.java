package com.nexusbattles.plataforma.comentarios.imagenes;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una imagen de comentario guardada en PostgreSQL — tabla
 * {@code imagenes_de_comentario} de V5.
 *
 * <p>Guarda los bytes ({@code bytea}) junto con lo que se comprobo de ellos:
 * el tipo detectado por su firma, el tamano y su huella SHA-256. El nombre
 * original del archivo no se guarda nunca: lo eligio quien la subio, no dice
 * nada que el servicio necesite y es un sitio clasico para colar rutas o
 * caracteres raros. La imagen se llama por su UUID.
 *
 * <p>{@code comentarioId} es nulo mientras esta pendiente. Las consultas que
 * no necesitan los bytes no cargan la entidad (ver
 * {@link RepositorioDeImagenes}): 2 MB por fila no se leen para contar.
 */
@Entity
@Table(name = "imagenes_de_comentario")
public class RegistroDeImagen {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "autor_id", nullable = false, length = 64)
    private String autorId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private int tamano;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private byte[] datos;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "comentario_id", length = 36)
    private String comentarioId;

    protected RegistroDeImagen() {
    }

    public RegistroDeImagen(String id, String autorId, TipoDeImagen tipo, byte[] datos,
            String sha256, Instant creadaEn) {
        this.id = id;
        this.autorId = autorId;
        this.tipo = tipo.tipoMime();
        this.tamano = datos.length;
        this.sha256 = sha256;
        this.datos = datos;
        this.creadaEn = creadaEn;
    }

    public String id() {
        return id;
    }

    public String autorId() {
        return autorId;
    }

    public TipoDeImagen tipo() {
        return TipoDeImagen.porTipoMime(tipo);
    }

    public int tamano() {
        return tamano;
    }

    public String sha256() {
        return sha256;
    }

    public byte[] datos() {
        return datos;
    }

    public Instant creadaEn() {
        return creadaEn;
    }

    public String comentarioId() {
        return comentarioId;
    }

    /** Si ya la usa un comentario. */
    public boolean estaAsociada() {
        return comentarioId != null;
    }
}

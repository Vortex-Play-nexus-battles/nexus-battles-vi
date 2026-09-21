package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.nexusbattles.plataforma.comentarios.Comentario;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Forma persistida de un {@link Comentario}, mapeada al esquema comentarios
 * que crea la migracion V1 de Flyway.
 *
 * <p>Se separa del registro de dominio a proposito: el dominio es inmutable y
 * valida sus reglas, mientras que esta clase solo sabe guardarse y volver. La
 * conversion vive aqui, en {@link #desde(Comentario)} y {@link #aDominio()},
 * para que el servicio no arme entidades a mano.
 */
@Entity
@Table(name = "comentarios")
public class RegistroDeComentario {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "producto_id", nullable = false, length = 64)
    private String productoId;

    @Column(name = "autor_id", nullable = false, length = 64)
    private String autorId;

    @Column(name = "apodo_autor", nullable = false, length = 100)
    private String apodoAutor;

    @Column(nullable = false)
    private String texto;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "comentario_imagenes",
            joinColumns = @JoinColumn(name = "comentario_id"))
    @OrderColumn(name = "orden")
    @Column(name = "nombre_archivo", nullable = false)
    private List<String> imagenes = new ArrayList<>();

    private Integer estrellas;

    @Column(name = "fecha_publicacion", nullable = false)
    private Instant fechaPublicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Comentario.Estado estado;

    protected RegistroDeComentario() {
    }

    /** Convierte el comentario del dominio en su forma persistida. */
    public static RegistroDeComentario desde(Comentario comentario) {
        RegistroDeComentario registro = new RegistroDeComentario();
        registro.id = comentario.id();
        registro.productoId = comentario.productoId();
        registro.autorId = comentario.autorId();
        registro.apodoAutor = comentario.apodoAutor();
        registro.texto = comentario.texto();
        registro.imagenes = new ArrayList<>(comentario.imagenes());
        registro.estrellas = comentario.estrellas();
        registro.fechaPublicacion = comentario.fechaPublicacion();
        registro.estado = comentario.estado();
        return registro;
    }

    /** Reconstruye el comentario del dominio, que revalida sus reglas al crearse. */
    public Comentario aDominio() {
        return new Comentario(
                id, productoId, autorId, apodoAutor, texto,
                List.copyOf(imagenes), estrellas, fechaPublicacion, estado);
    }

    public String getId() {
        return id;
    }

    public String getProductoId() {
        return productoId;
    }
}

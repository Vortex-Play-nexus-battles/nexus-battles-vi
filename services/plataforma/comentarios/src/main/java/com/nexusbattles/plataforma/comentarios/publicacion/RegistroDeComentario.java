package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

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
 *
 * <p><b>B3.</b> La columna {@code estrellas} sigue en la tabla con los datos
 * de antes, pero ya no se mapea: la calificacion vive en su propia tabla
 * (V5) y un comentario nuevo no guarda estrellas. Que no este mapeada
 * garantiza que nada la vuelva a escribir por descuido al actualizar un
 * comentario viejo.
 *
 * <p>Las imagenes se cargan en diferido y por lotes: el hilo se lee paginado y
 * con carga inmediata cada comentario de la pagina costaba una consulta mas
 * (el N+1 que la auditoria senalo). Con {@link BatchSize} las de toda la
 * pagina llegan en una sola. {@link #aDominio()} se llama siempre dentro de la
 * transaccion del servicio, que es donde vive la carga diferida.
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

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(
            name = "comentario_imagenes",
            joinColumns = @JoinColumn(name = "comentario_id"))
    @OrderColumn(name = "orden")
    @Column(name = "nombre_archivo", nullable = false)
    private List<String> imagenes = new ArrayList<>();

    @Column(name = "fecha_publicacion", nullable = false)
    private Instant fechaPublicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Comentario.Estado estado;

    @Column(nullable = false)
    private boolean editado;

    @Column(nullable = false)
    private boolean marcado;

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
        registro.fechaPublicacion = comentario.fechaPublicacion();
        registro.estado = comentario.estado();
        registro.editado = comentario.editado();
        registro.marcado = comentario.marcado();
        return registro;
    }

    /** Reconstruye el comentario del dominio, que revalida sus reglas al crearse. */
    public Comentario aDominio() {
        return new Comentario(
                id, productoId, autorId, apodoAutor, texto,
                List.copyOf(imagenes), fechaPublicacion, estado, editado, marcado);
    }

    public String getId() {
        return id;
    }

    public String getProductoId() {
        return productoId;
    }
}

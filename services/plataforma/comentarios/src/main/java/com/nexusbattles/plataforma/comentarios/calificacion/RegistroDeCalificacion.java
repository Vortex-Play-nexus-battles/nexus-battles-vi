package com.nexusbattles.plataforma.comentarios.calificacion;

import java.time.Instant;

import com.nexusbattles.plataforma.comentarios.Calificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Forma persistida de una {@link Calificacion}, tabla {@code calificaciones}
 * de la migracion V5.
 *
 * <p>Solo se lee por JPA. Se escribe con una sentencia propia
 * ({@link RepositorioDeCalificaciones#insertarSiNoExiste}) y no con
 * {@code save}, porque la regla de «una sola vez» la decide la restriccion
 * unica de la base y {@code save} convertiria el choque en una excepcion que
 * deja la transaccion inservible: al comentar con estrellas hace falta seguir
 * y guardar el comentario aunque la calificacion ya existiera.
 */
@Entity
@Table(name = "calificaciones")
public class RegistroDeCalificacion {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "producto_id", nullable = false, length = 64)
    private String productoId;

    @Column(name = "autor_id", nullable = false, length = 64)
    private String autorId;

    @Column(nullable = false)
    private int estrellas;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    protected RegistroDeCalificacion() {
    }

    /** Para construir filas en las pruebas; en produccion se inserta por SQL. */
    public static RegistroDeCalificacion desde(Calificacion calificacion) {
        RegistroDeCalificacion registro = new RegistroDeCalificacion();
        registro.id = calificacion.id();
        registro.productoId = calificacion.productoId();
        registro.autorId = calificacion.autorId();
        registro.estrellas = calificacion.estrellas();
        registro.creadaEn = calificacion.creadaEn();
        return registro;
    }

    public Calificacion aDominio() {
        return new Calificacion(id, productoId, autorId, estrellas, creadaEn);
    }

    public String getAutorId() {
        return autorId;
    }

    public int getEstrellas() {
        return estrellas;
    }
}

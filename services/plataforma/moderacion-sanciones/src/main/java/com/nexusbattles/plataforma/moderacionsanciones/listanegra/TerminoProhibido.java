package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Un termino de la lista negra — HU-ADM-002, 7.1.1 y 7.3.2 del documento del
 * curso, {@code moderacion-lista-negra.yaml} 2.0.x.
 *
 * <p>Guarda el termino tal como lo escribieron y su forma normalizada, que es
 * con la que se compara y la que no se puede repetir (dos terminos que se
 * escriben distinto pero normalizan igual, «Spider-Man» y «spiderman», son el
 * mismo termino). La forma la calcula siempre {@link NormalizadorDeTexto}: no
 * hay un segundo algoritmo en SQL.
 *
 * <p>{@code fecha_creacion} (V1) sigue en la tabla con su valor por omision y
 * no se mapea: {@code creado_en} (V4) la sustituye con zona horaria.
 */
@Entity
@Table(name = "terminos_prohibidos")
public class TerminoProhibido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String termino;

    @Column(nullable = false, unique = true)
    private String normalizado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaDeTermino categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModoDeCoincidencia modo;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    /** Exigido por JPA. */
    protected TerminoProhibido() {
    }

    public TerminoProhibido(String termino, String normalizado, CategoriaDeTermino categoria,
                            ModoDeCoincidencia modo, boolean activo, String creadoPor, OffsetDateTime creadoEn) {
        this.termino = Objects.requireNonNull(termino);
        this.normalizado = Objects.requireNonNull(normalizado);
        this.categoria = Objects.requireNonNull(categoria);
        this.modo = Objects.requireNonNull(modo);
        this.activo = activo;
        this.creadoPor = creadoPor;
        this.creadoEn = Objects.requireNonNull(creadoEn);
    }

    /** Cambia lo que se pida; lo que llega nulo se queda como estaba. */
    public void actualizar(String nuevoTermino, String nuevoNormalizado, CategoriaDeTermino nuevaCategoria,
                           ModoDeCoincidencia nuevoModo, Boolean nuevoActivo, OffsetDateTime ahora) {
        this.termino = Objects.requireNonNull(nuevoTermino);
        this.normalizado = Objects.requireNonNull(nuevoNormalizado);
        if (nuevaCategoria != null) {
            this.categoria = nuevaCategoria;
        }
        if (nuevoModo != null) {
            this.modo = nuevoModo;
        }
        if (nuevoActivo != null) {
            this.activo = nuevoActivo;
        }
        this.actualizadoEn = Objects.requireNonNull(ahora);
    }

    public Long id() {
        return id;
    }

    public String termino() {
        return termino;
    }

    public String normalizado() {
        return normalizado;
    }

    public CategoriaDeTermino categoria() {
        return categoria;
    }

    public ModoDeCoincidencia modo() {
        return modo;
    }

    public boolean activo() {
        return activo;
    }

    public String creadoPor() {
        return creadoPor;
    }

    public OffsetDateTime creadoEn() {
        return creadoEn;
    }

    public OffsetDateTime actualizadoEn() {
        return actualizadoEn;
    }
}

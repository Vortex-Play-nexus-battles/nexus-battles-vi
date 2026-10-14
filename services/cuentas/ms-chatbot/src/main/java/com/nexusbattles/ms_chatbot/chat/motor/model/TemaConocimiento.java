package com.nexusbattles.ms_chatbot.chat.motor.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entrada de la base de conocimiento del chatbot (HU-CHA-004).
 * Cada fila representa un "tema" que el motor de respuestas puede
 * reconocer a partir de sus palabras clave (español/inglés) y para el
 * cual tiene una respuesta ya redactada en ambos idiomas.
 *
 * <p>Desde HU-CHA-012 cada tema pertenece a una version de la base de
 * conocimiento; el mismo tema tiene una copia por version, y todas sus
 * copias comparten la misma {@code clave}.
 *
 * <p>Desde ms-chatbot.yaml 1.6.0 (7.4.10 «asociar respuestas con múltiples
 * intenciones») la misma respuesta puede atender, además de su
 * {@code categoria}, otras intenciones: ver {@link #atiende(Categoria)}.
 */
@Entity
@Table(name = "temas_conocimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemaConocimiento {

    /** 1.6.0: cuántas intenciones puede atender un tema además de su categoría. */
    public static final int MAXIMO_DE_INTENCIONES_ADICIONALES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // HU-CHA-012: version a la que pertenece esta copia del tema.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private VersionBaseConocimiento version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_respuesta", nullable = false, length = 40)
    private TipoRespuesta tipoRespuesta;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "palabras_clave_es", nullable = false, columnDefinition = "TEXT")
    private String palabrasClaveEs;

    @Column(name = "palabras_clave_en", columnDefinition = "TEXT")
    private String palabrasClaveEn;

    @Column(name = "contenido_respuesta_es", nullable = false, columnDefinition = "TEXT")
    private String contenidoRespuestaEs;

    @Column(name = "contenido_respuesta_en", columnDefinition = "TEXT")
    private String contenidoRespuestaEn;

    @Column(nullable = false)
    private boolean activo = true;

    // HU-CHA-012: identifica al MISMO tema en todas sus copias, una por version
    // de la base de conocimiento (V4). El id cambia en cada copia; la clave no.
    // Las analiticas de "temas frecuentes" agrupan por este valor.
    @Column(nullable = false, length = 80)
    private String clave;

    // HU-CHA-012: desempata cuando dos temas empatan en puntaje (gana el mayor).
    @Column(nullable = false)
    private int prioridad;

    // 1.6.0 (V12): otras intenciones que atiende la misma respuesta, ademas de
    // `categoria`. Hasta MAXIMO_DE_INTENCIONES_ADICIONALES, sin la principal
    // (asignarIntencionesAdicionales lo garantiza; el panel recibe un 400 con
    // el motivo antes, en BaseConocimientoAdminService).
    //
    // EAGER y por subconsulta: el motor las mira en cada pregunta y lee todos
    // los temas de produccion de una vez, a veces fuera de una transaccion
    // (las pruebas, las tareas programadas). Asi son UNA consulta mas por
    // lectura de temas, no una por tema, y nunca un LazyInitializationException.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "intenciones_adicionales_de_tema", joinColumns = @JoinColumn(name = "tema_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 40)
    @Fetch(FetchMode.SUBSELECT)
    private Set<Categoria> intencionesAdicionales = new HashSet<>();

    public TemaConocimiento(VersionBaseConocimiento version, String clave, Categoria categoria,
                            TipoRespuesta tipoRespuesta, String titulo,
                            String palabrasClaveEs, String palabrasClaveEn,
                            String contenidoRespuestaEs, String contenidoRespuestaEn,
                            int prioridad, boolean activo) {
        this.version = version;
        this.clave = clave;
        this.categoria = categoria;
        this.tipoRespuesta = tipoRespuesta;
        this.titulo = titulo;
        this.palabrasClaveEs = palabrasClaveEs;
        this.palabrasClaveEn = palabrasClaveEn;
        this.contenidoRespuestaEs = contenidoRespuestaEs;
        this.contenidoRespuestaEn = contenidoRespuestaEn;
        this.prioridad = prioridad;
        this.activo = activo;
    }
    // HU-CHA-012: copia de este tema para otra version, con la MISMA clave
    // (asi las analiticas lo siguen reconociendo como el mismo tema).
    // 1.6.0: la copia atiende las mismas intenciones adicionales.
    public TemaConocimiento copiarA(VersionBaseConocimiento destino) {
        TemaConocimiento copia = new TemaConocimiento(destino, clave, categoria, tipoRespuesta, titulo,
            palabrasClaveEs, palabrasClaveEn, contenidoRespuestaEs, contenidoRespuestaEn,
            prioridad, activo);
        copia.intencionesAdicionales.addAll(intencionesAdicionales);
        return copia;
    }

    // HU-CHA-012: edicion desde el panel. La version y la clave no cambian.
    // 1.6.0: con sus intenciones adicionales (null = ninguna).
    public void actualizar(Categoria categoria, TipoRespuesta tipoRespuesta, String titulo,
                           String palabrasClaveEs, String palabrasClaveEn,
                           String contenidoRespuestaEs, String contenidoRespuestaEn,
                           int prioridad, boolean activo, Collection<Categoria> intencionesAdicionales) {
        this.categoria = categoria;
        this.tipoRespuesta = tipoRespuesta;
        this.titulo = titulo;
        this.palabrasClaveEs = palabrasClaveEs;
        this.palabrasClaveEn = palabrasClaveEn;
        this.contenidoRespuestaEs = contenidoRespuestaEs;
        this.contenidoRespuestaEn = contenidoRespuestaEn;
        this.prioridad = prioridad;
        this.activo = activo;
        asignarIntencionesAdicionales(intencionesAdicionales);
    }

    /**
     * 1.6.0: reemplaza las intenciones adicionales (null = ninguna). Se
     * modifica la misma colección, no se cambia por otra: así Hibernate borra
     * e inserta solo las filas que cambian.
     *
     * @throws IllegalArgumentException si son más de
     *     {@value #MAXIMO_DE_INTENCIONES_ADICIONALES} o incluyen la categoría
     *     principal (quien llama ya lo validó con un mensaje para el panel)
     */
    public void asignarIntencionesAdicionales(Collection<Categoria> intenciones) {
        Set<Categoria> nuevas = intenciones == null ? Set.of() : Set.copyOf(intenciones);
        if (nuevas.size() > MAXIMO_DE_INTENCIONES_ADICIONALES || nuevas.contains(categoria)) {
            throw new IllegalArgumentException("Intenciones adicionales invalidas para el tema '" + titulo + "': "
                + nuevas + " (principal " + categoria + ").");
        }
        intencionesAdicionales.retainAll(nuevas);
        intencionesAdicionales.addAll(nuevas);
    }

    /** 1.6.0: las intenciones adicionales en el orden del enum (el del contrato), sin modificar. */
    public List<Categoria> getIntencionesAdicionales() {
        return intencionesAdicionales.stream().sorted().toList();
    }

    /**
     * 1.6.0: si este tema atiende esa intención, como categoría principal o
     * como adicional. Es lo que cuenta para la vista del jugador, las
     * preguntas relacionadas y las sugerencias por categoría. False con null.
     */
    public boolean atiende(Categoria intencion) {
        return intencion != null && (categoria == intencion || intencionesAdicionales.contains(intencion));
    }
}

package com.nexusbattles.ms_chatbot.chat.motor.model;

import jakarta.persistence.Column;
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
 */
@Entity
@Table(name = "temas_conocimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemaConocimiento {

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
    public TemaConocimiento copiarA(VersionBaseConocimiento destino) {
        return new TemaConocimiento(destino, clave, categoria, tipoRespuesta, titulo,
            palabrasClaveEs, palabrasClaveEn, contenidoRespuestaEs, contenidoRespuestaEn,
            prioridad, activo);
    }

    // HU-CHA-012: edicion desde el panel. La version y la clave no cambian.
    public void actualizar(Categoria categoria, TipoRespuesta tipoRespuesta, String titulo,
                           String palabrasClaveEs, String palabrasClaveEn,
                           String contenidoRespuestaEs, String contenidoRespuestaEn,
                           int prioridad, boolean activo) {
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
}

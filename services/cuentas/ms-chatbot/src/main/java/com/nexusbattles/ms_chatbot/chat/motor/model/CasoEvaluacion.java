package com.nexusbattles.ms_chatbot.chat.motor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-014): una pregunta de prueba con la respuesta que se
// espera del motor. Con estos casos se compara una version candidata contra
// la de produccion antes de desplegarla.
//
// temaClaveEsperada:
//   una clave -> el motor debe responder con el tema de esa clave.
//   null      -> el motor debe ESCALAR (es algo que el bot no deberia saber).
@Entity
@Table(name = "casos_evaluacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class CasoEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String pregunta;

    @Column(name = "tema_clave_esperada", length = 80)
    private String temaClaveEsperada;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    public static CasoEvaluacion nuevo(String pregunta, String temaClaveEsperada) {
        CasoEvaluacion caso = new CasoEvaluacion();
        caso.pregunta = pregunta;
        caso.temaClaveEsperada = temaClaveEsperada;
        caso.activo = true;
        caso.fechaCreacion = Instant.now();
        return caso;
    }

    public void actualizar(String pregunta, String temaClaveEsperada, boolean activo) {
        this.pregunta = pregunta;
        this.temaClaveEsperada = temaClaveEsperada;
        this.activo = activo;
    }

    public boolean esperaEscalamiento() {
        return temaClaveEsperada == null;
    }
}

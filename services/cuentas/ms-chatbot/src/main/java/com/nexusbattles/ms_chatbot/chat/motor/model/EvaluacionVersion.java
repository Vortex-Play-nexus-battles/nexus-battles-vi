package com.nexusbattles.ms_chatbot.chat.motor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-014): resultado de evaluar una version con los casos de
// evaluacion activos en ese momento. Se guarda cada evaluacion (antes de un
// despliegue y las periodicas) para poder comparar y detectar degradacion.
@Entity
@Table(name = "evaluaciones_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class EvaluacionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private VersionBaseConocimiento version;

    @Column(name = "casos_evaluados", nullable = false)
    private int casosEvaluados;

    @Column(nullable = false)
    private int aciertos;

    @Column(nullable = false)
    private Instant fecha;

    public static EvaluacionVersion registrar(VersionBaseConocimiento version, int casosEvaluados, int aciertos,
                                              Instant fecha) {
        if (casosEvaluados < 0 || aciertos < 0 || aciertos > casosEvaluados) {
            // Mismo limite que el CHECK chk_evaluaciones_aciertos de V4.
            throw new IllegalArgumentException(
                "Aciertos fuera de rango: " + aciertos + " de " + casosEvaluados + ".");
        }
        EvaluacionVersion evaluacion = new EvaluacionVersion();
        evaluacion.version = version;
        evaluacion.casosEvaluados = casosEvaluados;
        evaluacion.aciertos = aciertos;
        evaluacion.fecha = fecha;
        return evaluacion;
    }

    // Proporcion entre 0 y 1; null si no se evaluo ningun caso (mismo criterio
    // que las tasas de las analiticas).
    public Double tasaAcierto() {
        return casosEvaluados == 0 ? null : (double) aciertos / casosEvaluados;
    }
}

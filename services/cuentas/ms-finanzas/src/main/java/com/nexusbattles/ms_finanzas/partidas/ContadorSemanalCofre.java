package com.nexusbattles.ms_finanzas.partidas;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador de créditos ganados y cofres entregados a un jugador dentro de
 * una semana ISO. Es la fuente única de verdad para decidir cuándo entregar
 * un cofre (al llegar a 20 créditos) y cuándo dejar de entregarlo (al
 * alcanzar 2 cofres en la semana).
 *
 * <p>El contador de créditos se resetea a 0 al entregarse un cofre — así
 * el jugador puede acumular otros 20 para el segundo cofre — pero
 * {@code cofresEntregados} sí crece hasta 2, ese es el tope semanal.
 */
@Entity
@Table(name = "contador_semanal_cofre")
@Getter
@Setter
@NoArgsConstructor
public class ContadorSemanalCofre {

    @EmbeddedId
    private ContadorSemanalCofreId id;

    @Column(name = "creditos_ganados", nullable = false)
    private int creditosGanados;

    @Column(name = "cofres_entregados", nullable = false)
    private int cofresEntregados;

    public ContadorSemanalCofre(String uidJugador, String semanaIso) {
        this.id = new ContadorSemanalCofreId(uidJugador, semanaIso);
        this.creditosGanados = 0;
        this.cofresEntregados = 0;
    }
}

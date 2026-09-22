package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Historial de cofres entregados a un jugador. Alimenta la pantalla
 * "Mis cofres" y la trazabilidad de la HU-JUE-012.
 *
 * <p>El campo {@code contenido} guarda un identificador de recompensa
 * (por ahora un placeholder tipo {@code COFRE_ESTANDAR_v1}); la definición
 * exacta del contenido y de las probabilidades del cofre queda por
 * confirmar con el Product Owner (SRS RF-JUE-013).
 */
@Entity
@Table(name = "cofre_entregado")
@Getter
@Setter
@NoArgsConstructor
public class CofreEntregado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uid_jugador", length = 64, nullable = false)
    private String uidJugador;

    @Column(name = "semana_iso", length = 10, nullable = false)
    private String semanaIso;

    @Column(nullable = false, length = 64)
    private String contenido;

    @Column(name = "entregado_en", nullable = false)
    private Instant entregadoEn;
}

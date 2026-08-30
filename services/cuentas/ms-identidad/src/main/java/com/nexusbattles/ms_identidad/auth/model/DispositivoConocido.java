package com.nexusbattles.ms_identidad.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispositivos_conocidos")
@Getter
@Setter
@NoArgsConstructor
public class DispositivoConocido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(nullable = false)
    private LocalDateTime fechaPrimerAcceso;

    public DispositivoConocido(Usuario usuario, String huella) {
        this.usuario = usuario;
        this.huella = huella;
        this.fechaPrimerAcceso = LocalDateTime.now();
    }
}

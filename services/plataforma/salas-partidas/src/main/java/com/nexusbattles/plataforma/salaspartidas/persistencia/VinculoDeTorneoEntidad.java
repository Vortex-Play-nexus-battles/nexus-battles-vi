package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code encuentros_de_torneo} (V12). */
@Entity
@Table(name = "encuentros_de_torneo")
class VinculoDeTorneoEntidad {

    @Id
    @Column(name = "sala_id")
    private UUID idSala;

    @Column(name = "torneo_id", nullable = false)
    private UUID idTorneo;

    @Column(nullable = false)
    private int numero;

    @Column(name = "vinculado_por", nullable = false)
    private UUID vinculadoPor;

    @Column(name = "vinculado_en", nullable = false)
    private Instant vinculadoEn;

    @Column(name = "informado_en")
    private Instant informadoEn;

    @Column(name = "ultimo_fallo", length = 500)
    private String ultimoFallo;

    protected VinculoDeTorneoEntidad() {
    }

    static VinculoDeTorneoEntidad desde(VinculoDeTorneo v) {
        VinculoDeTorneoEntidad e = new VinculoDeTorneoEntidad();
        e.idSala = v.idSala();
        e.idTorneo = v.idTorneo();
        e.numero = v.numero();
        e.vinculadoPor = v.vinculadoPor();
        e.vinculadoEn = v.vinculadoEn();
        e.informadoEn = v.informadoEn();
        e.ultimoFallo = v.ultimoFallo();
        return e;
    }

    VinculoDeTorneo aDominio() {
        return new VinculoDeTorneo(idSala, idTorneo, numero, vinculadoPor, vinculadoEn, informadoEn, ultimoFallo);
    }
}

package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Representacion de una sala en la base de datos.
 *
 * <p>Deliberadamente separada de {@link Sala}: el dominio es inmutable y se
 * valida al construirse, mientras que JPA necesita constructor sin argumentos y
 * campos mutables. Mezclarlos obligaria a abrir el dominio para que encajara con
 * el ORM, y entonces las reglas del juego dejarian de estar garantizadas.
 *
 * <p>Los enumerados se guardan como texto, no como ordinal: si manana se
 * reordena {@link Modalidad}, las filas antiguas seguirian significando lo mismo.
 */
@Entity
@Table(name = "salas")
class SalaEntidad {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSala estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Modalidad modalidad;

    @Column(name = "maximo_participantes", nullable = false)
    private short maximoParticipantes;

    @Column(name = "recompensa_creditos", nullable = false)
    private int recompensaCreditos;

    @Column(name = "incluir_heroe_ia", nullable = false)
    private boolean incluirHeroeIA;

    @Column(nullable = false)
    private boolean privada;

    @Column(name = "tamano_equipo")
    private Short tamanoEquipo;

    @Column(name = "id_anfitrion", nullable = false)
    private UUID idAnfitrion;

    @Column(nullable = false)
    private short ocupacion;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    /** Exigido por JPA. No usar desde el codigo. */
    protected SalaEntidad() {
    }

    static SalaEntidad desde(Sala sala) {
        SalaEntidad entidad = new SalaEntidad();
        entidad.id = sala.id();
        entidad.estado = sala.estado();
        entidad.modalidad = sala.modalidad();
        entidad.maximoParticipantes = (short) sala.maximoParticipantes();
        entidad.recompensaCreditos = sala.recompensaCreditos();
        entidad.incluirHeroeIA = sala.incluirHeroeIA();
        entidad.privada = sala.privada();
        entidad.tamanoEquipo = sala.tamanoEquipo() == null ? null : sala.tamanoEquipo().shortValue();
        entidad.idAnfitrion = sala.idAnfitrion();
        entidad.ocupacion = (short) sala.ocupacion();
        entidad.creadaEn = sala.creadaEn();
        return entidad;
    }

    Sala aDominio() {
        return Sala.rehidratar(
                id,
                estado,
                modalidad,
                maximoParticipantes,
                recompensaCreditos,
                incluirHeroeIA,
                privada,
                tamanoEquipo == null ? null : tamanoEquipo.intValue(),
                idAnfitrion,
                ocupacion,
                creadaEn);
    }
}

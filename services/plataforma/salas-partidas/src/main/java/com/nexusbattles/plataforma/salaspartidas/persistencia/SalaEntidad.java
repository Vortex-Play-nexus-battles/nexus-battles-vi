package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /**
     * Numero de participantes. Se conserva para poder listar salas sin unir con
     * la tabla de participantes, pero NO es fuente de verdad: {@link #desde} lo
     * escribe siempre a partir del tamano del conjunto, asi que no puede
     * desmentir a las identidades.
     */
    @Column(nullable = false)
    private short ocupacion;

    /**
     * Identidades de quienes estan dentro — HU-SAL-002.
     *
     * <p>Se mapea como coleccion de valores y no como entidad propia porque un
     * participante no es mas que un identificador: no tiene ciclo de vida ni
     * atributos aparte. La clave compuesta (id_sala, id_jugador) de la
     * migracion impide el duplicado en la base.
     *
     * <p><b>EAGER a proposito.</b> {@code buscarPorId} devuelve el dominio fuera
     * de la sesion de JPA, y el dominio necesita el conjunto para aplicar las
     * reglas de ingreso. Con carga perezosa reventaria al leerlo, y una sala sin
     * participantes es justo el error que este incremento vino a corregir.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "participantes_de_sala",
            joinColumns = @JoinColumn(name = "id_sala"))
    @Column(name = "id_jugador", nullable = false)
    private Set<UUID> participantes = new LinkedHashSet<>();

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
        entidad.participantes = new LinkedHashSet<>(sala.participantes());
        // Derivado del conjunto, nunca copiado de otro contador: es la unica
        // forma de que la columna no pueda contradecir a las identidades.
        entidad.ocupacion = (short) entidad.participantes.size();
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
                participantes,
                creadaEn);
    }
}

package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Turno;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representacion de una partida en la base de datos.
 *
 * <p>Separada del dominio por el mismo motivo que {@code SalaEntidad}: JPA
 * necesita constructor sin argumentos y campos mutables, y el dominio se valida
 * al construirse.
 *
 * <p>Los participantes van con {@link OrderColumn} porque <b>el orden es el
 * orden de los turnos</b>. Sin esa columna, la lista volveria de la base en el
 * orden que quisiera el motor y la rotacion cambiaria tras cada reinicio.
 */
@Entity
@Table(name = "partidas")
class PartidaEntidad {

    @Id
    private UUID id;

    @Column(name = "id_sala", nullable = false, unique = true)
    private UUID idSala;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPartida estado;

    @Column(name = "turno_id_jugador", nullable = false)
    private UUID turnoIdJugador;

    @Column(name = "turno_numero", nullable = false)
    private int turnoNumero;

    @Column(name = "recompensa_en_juego", nullable = false)
    private int recompensaEnJuego;

    @Column(name = "iniciada_en", nullable = false)
    private Instant iniciadaEn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "partida_participantes",
            joinColumns = @JoinColumn(name = "id_partida"))
    @OrderColumn(name = "orden")
    private List<ParticipanteEmbebido> participantes = new ArrayList<>();

    protected PartidaEntidad() {
        // JPA.
    }

    static PartidaEntidad desde(Partida partida) {
        PartidaEntidad entidad = new PartidaEntidad();
        entidad.id = partida.id();
        entidad.idSala = partida.idSala();
        entidad.estado = partida.estado();
        entidad.turnoIdJugador = partida.turnoActual().idJugador();
        entidad.turnoNumero = partida.turnoActual().numeroTurno();
        entidad.recompensaEnJuego = partida.recompensaEnJuego();
        entidad.iniciadaEn = partida.iniciadaEn();
        entidad.participantes = partida.participantes().stream()
                .map(ParticipanteEmbebido::desde)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return entidad;
    }

    Partida aDominio() {
        List<ParticipanteDePartida> enCombate = participantes.stream()
                .map(ParticipanteEmbebido::aDominio)
                .toList();
        return Partida.rehidratar(id, idSala, estado, enCombate,
                new Turno(turnoIdJugador, turnoNumero), recompensaEnJuego, iniciadaEn);
    }

    /**
     * Un participante dentro de la fila de la partida.
     *
     * <p>El heroe se guarda aplanado en columnas en vez de como entidad propia:
     * no tiene vida fuera de la partida, no se consulta por su cuenta y su dueno
     * real es el modulo de inventario. Una tabla mas seria una entidad que este
     * servicio no posee.
     */
    @Embeddable
    static class ParticipanteEmbebido {

        @Column(name = "id_jugador", nullable = false)
        private UUID idJugador;

        @Column(name = "es_ia", nullable = false)
        private boolean esIA;

        @Column(name = "equipo")
        private Integer equipo;

        @Column(name = "creditos_apostados", nullable = false)
        private int creditosApostados;

        @Column(name = "heroe_id", length = 100)
        private String heroeId;

        @Column(name = "heroe_nombre", length = 120)
        private String heroeNombre;

        /** Prototipo del catalogo (V8). Sin el, el motor no resuelve el ataque. */
        @Column(name = "heroe_prototipo", length = 120)
        private String heroePrototipo;

        /** Defensa del prototipo (V8). Con la vida en su lugar, nadie acertaba. */
        @Column(name = "heroe_defensa")
        private Integer heroeDefensa;

        @Column(name = "heroe_retrato_url", length = 500)
        private String heroeRetratoUrl;

        @Column(name = "heroe_nivel")
        private Integer heroeNivel;

        @Column(name = "heroe_vida_actual")
        private Integer heroeVidaActual;

        @Column(name = "heroe_vida_maxima")
        private Integer heroeVidaMaxima;

        protected ParticipanteEmbebido() {
            // JPA.
        }

        static ParticipanteEmbebido desde(ParticipanteDePartida participante) {
            ParticipanteEmbebido fila = new ParticipanteEmbebido();
            fila.idJugador = participante.idJugador();
            fila.esIA = participante.esIA();
            fila.equipo = participante.equipo();
            fila.creditosApostados = participante.creditosApostados();
            HeroeDeCombate heroe = participante.heroe();
            if (heroe != null) {
                fila.heroeId = heroe.id();
                fila.heroeNombre = heroe.nombre();
                fila.heroePrototipo = heroe.prototipo();
                fila.heroeDefensa = heroe.defensa();
                fila.heroeRetratoUrl = heroe.retratoUrl();
                fila.heroeNivel = heroe.nivel();
                fila.heroeVidaActual = heroe.vidaActual();
                fila.heroeVidaMaxima = heroe.vidaMaxima();
            }
            return fila;
        }

        ParticipanteDePartida aDominio() {
            HeroeDeCombate heroe = heroeId == null ? null
                    : new HeroeDeCombate(heroeId, heroeNombre, heroePrototipo, heroeRetratoUrl,
                            heroeNivel, heroeVidaActual, heroeVidaMaxima, heroeDefensa);
            return new ParticipanteDePartida(idJugador, heroe, esIA, equipo, creditosApostados);
        }
    }
}

package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
    @jakarta.persistence.MapKeyColumn(name = "id_jugador")
    private Map<UUID, FichaEmbebida> participantes = new LinkedHashMap<>();

    /**
     * Ficha del participante en columnas planas — V7.
     *
     * <p>Un {@code @MapKeyColumn} y no un {@code @OrderColumn}: la clave
     * primaria de la tabla sigue siendo {@code (id_sala, id_jugador)}, que es
     * lo que impide el duplicado, y anadir una columna de orden la habria
     * cambiado. A la sala no le importa el orden de ingreso —el del combate lo
     * fija la partida—, le importa quien esta dentro.
     *
     * <p>Todo anulable: las filas anteriores a V7 no tienen ficha y decirlo con
     * NULL es la verdad.
     */
    @jakarta.persistence.Embeddable
    static class FichaEmbebida {

        /**
         * Discriminante. Es lo que impide que la fila quede toda nula: Hibernate
         * colapsa un embebido con todas sus columnas nulas, lo relee como
         * {@code null} y entonces <b>descarta la entrada del mapa</b>, con lo
         * que el participante desaparece de la sala. Lo vio
         * {@code IngresoConcurrenteIT}: Ana entraba y al releer ya no estaba.
         */
        @Column(name = "con_ficha", nullable = false)
        private boolean conFicha;

        @Column(name = "apodo", length = 120)
        private String apodo;

        @Column(name = "heroe_id", length = 100)
        private String heroeId;

        @Column(name = "heroe_nombre", length = 120)
        private String heroeNombre;

        /** Prototipo del catalogo (V8). Sin el, el motor no resuelve el ataque. */
        @Column(name = "heroe_prototipo", length = 120)
        private String heroePrototipo;

        @Column(name = "heroe_retrato_url", length = 500)
        private String heroeRetratoUrl;

        @Column(name = "heroe_nivel")
        private Integer heroeNivel;

        @Column(name = "heroe_vida_actual")
        private Integer heroeVidaActual;

        @Column(name = "heroe_vida_maxima")
        private Integer heroeVidaMaxima;

        protected FichaEmbebida() {
            // JPA.
        }

        /**
         * {@code null} cuando no hay ficha, y NO un embebido con todo a nulo.
         *
         * <p>La diferencia no es de estilo. Hibernate colapsa un embebido con
         * todas sus columnas nulas y lo <b>relee como {@code null}</b>. Si al
         * guardar se escribiera el embebido vacio, la copia gestionada tendria
         * {@code null} y la nuestra un objeto: la entrada pareceria nueva y el
         * siguiente guardado reintentaria el INSERT sobre una clave que ya
         * existe. Lo destapo {@code IngresoConcurrenteIT} con un «duplicate key
         * value violates unique constraint participantes_de_sala_pk» sobre el
         * identificador del anfitrion, que es justo el participante que puede
         * no tener ficha.
         */
        static FichaEmbebida desde(FichaDeParticipante ficha) {
            FichaEmbebida fila = new FichaEmbebida();
            if (ficha == null) {
                // Sin ficha, pero CON fila: el participante esta dentro igual.
                fila.conFicha = false;
                return fila;
            }
            fila.conFicha = true;
            fila.apodo = ficha.apodo();
            HeroeDeCombate heroe = ficha.heroe();
            fila.heroeId = heroe.id();
            fila.heroeNombre = heroe.nombre();
            fila.heroePrototipo = heroe.prototipo();
            fila.heroeRetratoUrl = heroe.retratoUrl();
            fila.heroeNivel = heroe.nivel();
            fila.heroeVidaActual = heroe.vidaActual();
            fila.heroeVidaMaxima = heroe.vidaMaxima();
            return fila;
        }

        /**
         * Igualdad por valor — OBLIGATORIA, no cosmetica.
         *
         * <p>Hibernate compara los elementos de una {@code @ElementCollection}
         * con {@code equals} para decidir que filas ya estaban y cuales son
         * nuevas. Sin esto cada guardado las considera todas nuevas y reintenta
         * el INSERT sobre una clave que ya existe; lo destapo
         * {@code IngresoConcurrenteIT} con un
         * «duplicate key value violates unique constraint
         * participantes_de_sala_pk» al guardar dos veces la misma sala.
         */
        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof FichaEmbebida ficha)) {
                return false;
            }
            return conFicha == ficha.conFicha
                    && java.util.Objects.equals(apodo, ficha.apodo)
                    && java.util.Objects.equals(heroeId, ficha.heroeId)
                    && java.util.Objects.equals(heroeNombre, ficha.heroeNombre)
                    && java.util.Objects.equals(heroePrototipo, ficha.heroePrototipo)
                    && java.util.Objects.equals(heroeRetratoUrl, ficha.heroeRetratoUrl)
                    && java.util.Objects.equals(heroeNivel, ficha.heroeNivel)
                    && java.util.Objects.equals(heroeVidaActual, ficha.heroeVidaActual)
                    && java.util.Objects.equals(heroeVidaMaxima, ficha.heroeVidaMaxima);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(conFicha, apodo, heroeId, heroeNombre, heroePrototipo,
                    heroeRetratoUrl,
                    heroeNivel, heroeVidaActual, heroeVidaMaxima);
        }

        /** {@code null} cuando la fila no trae ficha: no se inventa una vacia. */
        FichaDeParticipante aDominio() {
            if (!conFicha) {
                return null;
            }
            return new FichaDeParticipante(apodo, new HeroeDeCombate(
                    heroeId, heroeNombre, heroePrototipo, heroeRetratoUrl, heroeNivel,
                    heroeVidaActual, heroeVidaMaxima));
        }
    }

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    /**
     * Codigo de invitacion de una sala privada; nulo en las publicas.
     *
     * <p>Se guarda en claro y no cifrado ni resumido: hay que poder devolverselo
     * al anfitrion cuando vuelva a consultar su sala, y un resumen solo serviria
     * para comprobarlo, no para mostrarlo. Es un codigo de acceso a una sala de
     * juego, no una credencial de cuenta.
     */
    @Column(name = "codigo_invitacion", length = 20)
    private String codigoInvitacion;

    /** Reserva de creditos ligada a la sala; nula si no compromete creditos. */
    @Column(name = "id_reserva_creditos")
    private UUID idReservaCreditos;

    /**
     * Bloqueo optimista — HU-SAL-002. Hibernate anade {@code AND version = ?}
     * a cada UPDATE y lanza {@code OptimisticLockException} si otra escritura
     * se adelanto. Primitivo a proposito: con un {@code Long} nulo Spring Data
     * decidiria «es nueva» por la version y no por el identificador, que aqui
     * se asigna en el dominio.
     */
    @Version
    @Column(nullable = false)
    private long version;

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
        entidad.participantes = new LinkedHashMap<>();
        sala.fichas().forEach((jugador, ficha) ->
                entidad.participantes.put(jugador, FichaEmbebida.desde(ficha)));
        // Derivado del conjunto, nunca copiado de otro contador: es la unica
        // forma de que la columna no pueda contradecir a las identidades.
        entidad.ocupacion = (short) entidad.participantes.size();
        entidad.creadaEn = sala.creadaEn();
        entidad.codigoInvitacion = sala.codigoInvitacion();
        entidad.idReservaCreditos = sala.idReservaCreditos();
        // La version que el dominio leyo: es lo que permite detectar que otro
        // ingreso se guardo entre la lectura y esta escritura.
        entidad.version = sala.version();
        return entidad;
    }

    private Map<UUID, FichaDeParticipante> fichasDeDominio() {
        Map<UUID, FichaDeParticipante> fichas = new LinkedHashMap<>();
        participantes.forEach((jugador, fila) ->
                fichas.put(jugador, fila == null ? null : fila.aDominio()));
        return fichas;
    }

    /** Marca de concurrencia tal como esta en la base. */
    long version() {
        return version;
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
                fichasDeDominio(),
                creadaEn,
                version,
                codigoInvitacion,
                idReservaCreditos);
    }
}

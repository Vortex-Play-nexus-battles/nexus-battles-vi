package com.nexusbattles.ms_subastas.subastas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Borrador de la tabla Subastas pendiente del diseno conjunto del Dia 1
 * (Cristian HU-SUB-011, Edwin HU-SUB-001, Andres HU-SUB-004). Edwin es quien
 * crea el dato base; este modelo solo cubre lo que el motor de pujas necesita
 * leer/escribir y puede cambiar cuando se cierre el diseno compartido.
 *
 * Campos agregados por Cristian (HU-SUB-011, listado) para soportar el
 * listado paginado con filtros del SRS 7.7.9 -- PENDIENTES DE CONFIRMAR
 * con Edwin en la sesion conjunta. No asumir que estan cerrados.
 *
 * nombreProducto/tipoProducto/precioInicial/fechaPublicacion se dejan
 * nullable a nivel de esquema/entidad a proposito, mientras el diseno
 * conjunto no cierre.
 *
 * SIN @AllArgsConstructor: al agregar estos campos, Lombok dejaria de
 * generar el constructor de 10 parametros que ya usan las pruebas de
 * Andres (MotorPujasServiceTest y 5 mas) -- @AllArgsConstructor genera
 * un unico constructor con TODOS los campos, no uno por version. Se
 * escribe a mano ese constructor de 10, identico al que Lombok generaba
 * antes, para no romper esas pruebas. Los campos nuevos quedan en sus
 * valores por defecto (null/0/false) cuando se usa este constructor.
 */
@Entity
@Table(name = "subastas")
@Data
@NoArgsConstructor
public class Subasta implements Persistable<UUID> {

    /**
     * Lo asigna la aplicacion, no la base de datos.
     *
     * <p>Tiene que ser asi: {@code PublicarSubastaApplicationService} necesita
     * el identificador ANTES de guardar, porque con el reserva el elemento en
     * inventario y debita la comision. Si lo generara la base al insertar, no
     * habria nada con que reservar.
     *
     * <p>Hasta R10 esto declaraba ademas {@code @GeneratedValue}, que decia lo
     * contrario de lo que el codigo hace. Y la mezcla era mortal: con el id ya
     * puesto y la version primitiva de abajo, Spring Data decidia que la entidad
     * NO era nueva y mandaba {@code merge} en vez de {@code persist};
     * Hibernate la trataba como separada, no encontraba la fila y lanzaba
     * {@code StaleObjectStateException}. Publicar una subasta terminaba en 500
     * <b>siempre</b> contra una base de datos real. No lo veia nadie porque las
     * pruebas de publicacion usan un repositorio doble.
     */
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID productoId;

    // HU-SUB-001: identifica la instancia del producto en el inventario.
    @Column(nullable = false)
    private String elementoInventarioId;

    @Column(nullable = false)
    private UUID vendedorId;

    @Column(nullable = false)
    private BigDecimal ofertaVigente;

    @Column(nullable = false)
    private BigDecimal incrementoMinimo;

    private BigDecimal precioCompraInmediata;

    private UUID mejorPostorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSubasta estado;

    @Column(nullable = false)
    private Instant fechaFin;

    @Version
    private long version;

    /**
     * Si esta instancia todavia no esta en la base — R10.
     *
     * <p>Con el id asignado por la aplicacion, Spring Data no puede deducirlo:
     * {@code JpaMetamodelEntityInformation} mira el id, lo encuentra puesto y
     * concluye que la entidad ya existe, asi que manda {@code merge} en vez de
     * {@code persist}. Hibernate la trata entonces como separada, no encuentra
     * la fila y lanza {@code StaleObjectStateException}: publicar una subasta
     * terminaba en 500 <b>siempre</b> contra una base real.
     *
     * <p>Se dice explicitamente con {@link Persistable} en vez de apoyarse en
     * que la version sea nula, porque esto no depende de como este declarado
     * ningun otro campo: una instancia recien construida es nueva, y deja de
     * serlo cuando Hibernate la carga o la inserta.
     */
    @Transient
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private boolean nueva = true;

    @PostLoad
    @PostPersist
    void yaEstaEnLaBase() {
        this.nueva = false;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    // --- Datos del producto, copiados del catalogo al publicar ---
    // No se consultan en vivo: backend-spring.md prohibe leer la BD de otro
    // servicio, y el listado exige carga rapida. BORRADOR.

    private String nombreProducto;

    @Enumerated(EnumType.STRING)
    private TipoProducto tipoProducto;

    private String rareza;

    private String miniaturaUrl;

    @Column(length = 500)
    private String descripcionCorta;

    @Column(length = 1000)
    private String habilidades;

    // --- Datos propios de esta historia (HU-SUB-011) ---

    private BigDecimal precioInicial;

    @Column(nullable = false)
    private int cantidadPujas = 0;

    @Column(nullable = false)
    private boolean esMaestroDeJuego = false;

    private Instant fechaPublicacion;

    @Column(nullable = false)
    private int vistas = 0;

    // --- B8 (7.7 del documento del curso) ---

    /**
     * Comision de publicacion que se cobro (Tabla 25). La penalizacion de
     * cancelar es el 50 % de esta cifra (7.7.10), asi que tiene que quedar
     * guardada: la duracion por si sola no dice si el vendedor era el Maestro
     * de Juego, que no paga.
     */
    private BigDecimal comisionCobrada;

    /** Lo que se debito al cancelar (7.7.10); nulo si no se cancelo. */
    private BigDecimal penalizacionCobrada;

    /** Cuando dejo de estar ACTIVA: adjudicada, sin adjudicacion o cancelada. */
    private Instant cerradaEn;

    /** El aviso de 1 hora antes del cierre (7.7.8) sale una sola vez. */
    private Instant recordatorioEnviadoEn;

    /** Apodo del vendedor al publicar (claim {@code sub}); una foto, no la cuenta viva. */
    @Column(length = 60)
    private String apodoVendedor;

    /**
     * Constructor historico (10 parametros), identico al que generaba
     * @AllArgsConstructor antes de HU-SUB-011. Se mantiene a mano
     * exclusivamente para no romper las pruebas de Andres que ya lo
     * llaman por posicion.
     */
    public Subasta(UUID id, UUID productoId, UUID vendedorId, BigDecimal ofertaVigente,
                   BigDecimal incrementoMinimo, BigDecimal precioCompraInmediata,
                   UUID mejorPostorId, EstadoSubasta estado, Instant fechaFin, long version) {
        this.id = id;
        this.productoId = productoId;
        this.vendedorId = vendedorId;
        this.ofertaVigente = ofertaVigente;
        this.incrementoMinimo = incrementoMinimo;
        this.precioCompraInmediata = precioCompraInmediata;
        this.mejorPostorId = mejorPostorId;
        this.estado = estado;
        this.fechaFin = fechaFin;
        this.version = version;
    }

    public boolean estaActiva() {
        return estado == EstadoSubasta.ACTIVA;
    }

    public boolean esVendedor(UUID jugadorId) {
        return vendedorId.equals(jugadorId);
    }

    /**
     * Si alguien ya pujo: hay un mejor postor. Mientras nadie puja, la oferta
     * vigente es el precio minimo que fijo el vendedor.
     */
    public boolean tieneOfertas() {
        return mejorPostorId != null;
    }

    /**
     * Lo minimo que se puede ofrecer ahora (7.7.2 y 7.7.6).
     *
     * <p>La primera puja tiene que llegar al precio minimo que fijo el vendedor
     * («establecer precio minimo de puja»); desde la segunda, superar la
     * oferta vigente en al menos el incremento minimo («las pujas deben ser
     * superiores a la oferta actual», «incremento minimo configurable entre
     * pujas»). Hasta B8 la primera exigia precio minimo MAS incremento, que no
     * es lo que dice el documento: el incremento es entre pujas.
     */
    public BigDecimal pujaMinimaSiguiente() {
        return tieneOfertas() ? ofertaVigente.add(incrementoMinimo) : ofertaVigente;
    }

    /**
     * Si la compra inmediata se puede ejecutar ahora.
     *
     * <p>Deja de estarlo cuando una puja alcanza o supera su precio: comprar
     * por debajo de lo que otro jugador ya ofrecio le quitaria el producto al
     * mejor postor, que ademas llego primero. El documento solo dice «opcion
     * de Comprar ahora si esta disponible» (7.7.9); cuando deja de estarlo es
     * esta decision tecnica, anotada en el README del servicio.
     */
    public boolean compraInmediataDisponible() {
        if (!estaActiva() || precioCompraInmediata == null) {
            return false;
        }
        return !(tieneOfertas() && ofertaVigente.compareTo(precioCompraInmediata) >= 0);
    }

    /** Deja constancia de que termino, sea como sea. */
    public void cerrar(EstadoSubasta estadoFinal, Instant cuando) {
        if (estadoFinal == EstadoSubasta.ACTIVA) {
            throw new IllegalArgumentException("cerrar una subasta es dejarla en un estado final");
        }
        this.estado = estadoFinal;
        this.cerradaEn = cuando;
    }
}

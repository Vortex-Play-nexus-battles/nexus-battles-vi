package com.nexusbattles.ms_subastas.subastas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
public class Subasta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
}

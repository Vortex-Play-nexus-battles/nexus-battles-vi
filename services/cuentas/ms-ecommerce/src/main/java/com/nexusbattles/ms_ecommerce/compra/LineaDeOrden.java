package com.nexusbattles.ms_ecommerce.compra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Una linea de una orden (tabla {@code lineas_orden}, V4): el producto, la
 * cantidad y el precio calculado por el servidor al crear la orden, que es el
 * que se cobra aunque el catalogo cambie despues.
 *
 * <p>{@link #posicion} forma parte de las claves de idempotencia de la reserva
 * de tiraje ({@code orden-{id}-l{posicion}-u{unidad}}): es estable, a
 * diferencia del id de la fila.
 */
@Entity
@Table(name = "lineas_orden")
@Getter
@Setter
public class LineaDeOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @Column(nullable = false)
    private int posicion;

    @Column(name = "producto_ref", nullable = false, length = 64)
    private String productoRef;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "precio_original", nullable = false)
    private BigDecimal precioOriginal;

    @Column(name = "descuento_porcentaje")
    private Integer descuentoPorcentaje;

    @Column(nullable = false)
    private BigDecimal subtotal;

    /** Unidades ya reservadas en el catalogo; la reserva sigue desde aqui. */
    @Column(name = "unidades_reservadas", nullable = false)
    private int unidadesReservadas;
}

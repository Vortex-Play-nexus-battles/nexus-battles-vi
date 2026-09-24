package com.nexusbattles.ms_ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Una linea del carrito.
 *
 * <p>Desde V2 la linea apunta al producto del <b>catalogo maestro</b> (servicio
 * productos, ids UUID) y guarda una instantanea de lo que se agrego: la
 * referencia, el nombre, el precio unitario y su moneda. Ya no mapea la
 * relacion con la tabla local {@code productos}: esa tabla nace vacia y no esta
 * conectada a nada. La columna {@code producto_id} sigue en la base —las
 * lineas anteriores la usan y V2 no borra nada— pero la entidad ya no la
 * mapea, y {@code ddl-auto=validate} ignora las columnas que no se mapean.
 */
@Entity
@Table(name = "items_carrito")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Fuera de toString/equals/hashCode: Carrito ya incluye sus lineas, y con
    // las dos direcciones cualquier log o comparacion recorria el ciclo sin fin.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrito_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Carrito carrito;

    /** Identificador del producto en el catalogo maestro (el UUID del servicio productos). */
    @Column(name = "producto_ref", length = 64)
    private String productoRef;

    /** Nombre del producto cuando se agrego. */
    @Column(name = "producto_nombre")
    private String productoNombre;

    /** Moneda de {@link #precioUnitario} (ISO 4217; hoy siempre COP). */
    @Column(name = "moneda", length = 3)
    private String moneda;

    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;

    public void calcularSubtotal() {
        if (precioUnitario != null && cantidad != null) {
            this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        }
    }
}

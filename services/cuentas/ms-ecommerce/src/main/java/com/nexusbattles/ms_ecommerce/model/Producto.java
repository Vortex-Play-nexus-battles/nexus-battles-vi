package com.nexusbattles.ms_ecommerce.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String imagenUrl;

    @Column(length = 1000)
    private String descripcion;

    private String habilidades;
    private String tipo;

    private BigDecimal precioBaseCop;

    private Boolean enPromocion = false;
    private Integer porcentajeDescuento = 0;
    private LocalDateTime fechaFinPromocion;
}
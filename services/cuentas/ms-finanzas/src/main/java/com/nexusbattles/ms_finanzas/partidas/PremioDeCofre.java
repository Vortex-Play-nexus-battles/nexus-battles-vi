package com.nexusbattles.ms_finanzas.partidas;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una línea del contenido de un cofre — esquema {@code PremioDeCofre} de
 * cofres.yaml 1.1.0: un producto del catálogo y cuántas unidades.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PremioDeCofre {

    @Column(name = "producto_id", length = 64, nullable = false)
    private String productoId;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    public PremioDeCofre(String productoId, int cantidad) {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("Un premio es un producto del catálogo.");
        }
        if (cantidad < 1) {
            throw new IllegalArgumentException("Un premio trae al menos una unidad.");
        }
        this.productoId = productoId;
        this.cantidad = cantidad;
    }
}

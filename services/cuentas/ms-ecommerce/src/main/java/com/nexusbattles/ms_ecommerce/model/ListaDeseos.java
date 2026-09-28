package com.nexusbattles.ms_ecommerce.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un producto en la lista de deseos de un jugador (7.5: «los productos que son
 * enviados a la lista de deseos siempre tendran una distincion»).
 *
 * <p>B5 — apunta al producto del <b>catalogo maestro</b> ({@code producto_ref},
 * el UUID del servicio productos). Hasta V3 mapeaba una relacion con la tabla
 * local {@code productos}, que nace vacia: no se podia guardar ningun producto
 * de los que la tienda vende, y por eso no tenia controlador. La columna
 * {@code producto_id} sigue en la base, sin uso, como en el carrito desde V2.
 *
 * <p>Una fila por jugador y producto (indice unico de V3): anadir dos veces es
 * no hacer nada.
 */
@Entity
@Table(name = "lista_deseos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListaDeseos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** El {@code uid} del token del jugador. */
    @Column(nullable = false)
    private String usuarioId;

    /** Identificador del producto en el catalogo maestro. */
    @Column(name = "producto_ref", length = 64)
    private String productoRef;

    /** Nombre del producto cuando se guardo: el que se ensena si el catalogo no responde. */
    @Column(name = "producto_nombre")
    private String productoNombre;

    @Column(name = "agregado_en")
    private Instant agregadoEn;
}

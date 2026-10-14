package com.nexusbattles.ms_ecommerce.dto;

import java.math.BigDecimal;

/**
 * Un producto de la vitrina ({@code GET /api/v1/vitrina}), proyectado del
 * catalogo maestro.
 *
 * <p>Misma forma que {@link ProductoVitrinaDto} —el frontend ya la sabe leer—
 * salvo el {@code id}, que ahora es el UUID del catalogo (texto) y no el BIGINT
 * de la tabla local. Es otro tipo y no un cambio de {@code ProductoVitrinaDto}
 * para que el endpoint legado {@code GET /api/v1/productos} siga devolviendo
 * exactamente lo mismo que antes.
 *
 * <p>B5 (contrato 1.4.0): los precios estan en la moneda pedida y con la
 * promocion vigente ya aplicada, calculados por el servidor
 * ({@code CalculadoraDePrecios}); {@code esPropio} y {@code enListaDeseos} se
 * calculan cuando hay sesion de usuario y son false sin ella.
 */
public record ProductoEnVentaDto(
        String id,
        String nombre,
        String imagenUrl,
        String descripcion,
        String habilidades,
        String tipo,
        BigDecimal precioFinal,
        BigDecimal precioOriginal,
        String moneda,
        Boolean enPromocion,
        Integer porcentajeDescuento,
        Boolean esPropio,
        Boolean enListaDeseos,
        Long precioCreditos,
        Boolean premium,
        Boolean unico) {

    /**
     * Sin las marcas del catalogo: la forma de antes de 1.8.0.
     *
     * <p>{@code premium} y {@code unico} (SA-13, contrato 1.8.0, §7.2.1 y
     * §7.2.2): un producto de pago en moneda real se identifica como tal en la
     * tienda, y uno en estado UNICO tambien. Null cuando quien construye no los
     * sabe (las pruebas y las formas anteriores).
     */
    public ProductoEnVentaDto(String id, String nombre, String imagenUrl, String descripcion, String habilidades,
                              String tipo, BigDecimal precioFinal, BigDecimal precioOriginal, String moneda,
                              Boolean enPromocion, Integer porcentajeDescuento, Boolean esPropio,
                              Boolean enListaDeseos, Long precioCreditos) {
        this(id, nombre, imagenUrl, descripcion, habilidades, tipo, precioFinal, precioOriginal, moneda, enPromocion,
                porcentajeDescuento, esPropio, enListaDeseos, precioCreditos, null, null);
    }

    /**
     * Sin precio en creditos: la forma de antes de 1.6.0.
     *
     * <p>{@code precioCreditos} (D-44, contrato 1.6.0) es lo que cuesta pagado
     * con creditos del juego, con la promocion vigente; null si no se puede
     * pagar asi (premium o sin precio en creditos en el catalogo).
     */
    public ProductoEnVentaDto(String id, String nombre, String imagenUrl, String descripcion, String habilidades,
                              String tipo, BigDecimal precioFinal, BigDecimal precioOriginal, String moneda,
                              Boolean enPromocion, Integer porcentajeDescuento, Boolean esPropio,
                              Boolean enListaDeseos) {
        this(id, nombre, imagenUrl, descripcion, habilidades, tipo, precioFinal, precioOriginal, moneda, enPromocion,
                porcentajeDescuento, esPropio, enListaDeseos, null);
    }
}

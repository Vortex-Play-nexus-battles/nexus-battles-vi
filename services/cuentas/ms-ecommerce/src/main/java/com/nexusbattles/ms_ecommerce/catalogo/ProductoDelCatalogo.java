package com.nexusbattles.ms_ecommerce.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Un producto tal como lo publica el catalogo maestro: el {@code ProductoCreado}
 * del servicio productos ({@code contracts/openapi/productos.yaml}).
 *
 * <p>Solo se declaran los campos que la tienda usa; los demas (prototipo,
 * heroe, efectos, version, fechas...) se ignoran. La lectura es tolerante a
 * proposito: el catalogo es de otro equipo, puede crecer sin avisar, y un
 * campo nuevo no puede dejar la tienda sin productos.
 *
 * <p>Por eso los numeros y booleanos son envoltorios y no primitivos: Jackson 3
 * falla por omision al leer un nulo (o una ausencia) en un primitivo, y el
 * catalogo publica con {@code non_null}, asi que un campo sin valor
 * sencillamente no viene. Por eso tambien {@code tipo} y {@code estado} son
 * texto y no enumerados: un estado que la tienda no conoce no es un error de
 * lectura, es un producto que no se vende.
 *
 * @param tiraje           -1 ilimitado, mayor que cero unidades restantes, 0 agotado
 * @param precioMonedaReal precio en moneda real (COP); nulo o cero si el producto
 *                         no se vende en moneda real
 * @param estado           ACTIVO, UNICO o SUSPENDIDO
 * @param habilidades      texto, lista de textos o ausente, segun el tipo de producto
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoDelCatalogo(
        String id,
        String nombre,
        String imagen,
        String descripcion,
        String tipo,
        Integer tiraje,
        Integer precioCreditos,
        BigDecimal precioMonedaReal,
        Boolean premium,
        String estado,
        Object habilidades) {

    /** Tiraje de un producto sin limite de unidades. */
    public static final int TIRAJE_ILIMITADO = -1;

    /**
     * Estados en los que el catalogo ofrece el producto. SUSPENDIDO no
     * (RN-PRD-004), y un estado desconocido tampoco: ante la duda, no se vende.
     */
    public static final Set<String> ESTADOS_EN_VENTA = Set.of("ACTIVO", "UNICO");

    /** RN-PRD-003/004: el catalogo lo ofrece ahora mismo. */
    public boolean estaEnVenta() {
        return estado != null && ESTADOS_EN_VENTA.contains(estado);
    }

    /**
     * RF-PRD-003 / RF-CAR-007: quedan unidades — tiraje ilimitado (-1) o
     * mayor que cero. Es el "tiraje distinto de 0" de la regla, para todo
     * valor que el contrato admite; sin tiraje no hay existencias que
     * validar, y lo que no se puede validar no se vende.
     */
    public boolean tieneExistencias() {
        return tiraje != null && (tiraje == TIRAJE_ILIMITADO || tiraje > 0);
    }

    /**
     * RF-CAR-002 / RN-PAG-001: la tienda cobra en moneda real. Un producto sin
     * ese precio no se vende aqui; nunca se ensena a 0.
     *
     * <p>Cero tampoco es un precio en moneda real para esta tienda: el contrato
     * del catalogo permite precioMonedaReal: 0 y los productos que solo se
     * venden en creditos lo traen asi (la semilla del banco E2E, por ejemplo).
     * Cobrar 0 COP por la pasarela no tiene sentido, y ensenarlo seria decir
     * «gratis» de algo que no lo es.
     */
    public boolean tienePrecioEnMonedaReal() {
        return precioMonedaReal != null && precioMonedaReal.signum() > 0;
    }
}

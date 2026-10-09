package nexus.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import nexus.dominio.EstadoProducto;
import nexus.dominio.OrigenProducto;
import nexus.dominio.ParteArmadura;
import nexus.dominio.TipoProducto;

/**
 * Un producto tal como sale por la API (esquema {@code ProductoCreado}).
 *
 * <p>Desde B4 hay dos proyecciones del mismo registro (ver
 * {@code ProyeccionDeProductos}): la completa, para un servicio o un
 * administrador, y la publica, que deja en nulo —y por tanto fuera del JSON,
 * que se publica con {@code non_null}— lo interno: {@code version},
 * {@code tasaDeCaida}, {@code origen}, {@code semillaVersion} y
 * {@code modificadoPor}. Por eso {@code version} es un {@link Integer}.
 */
public record ProductoCreado(

        String id,

        String nombre,

        String imagen,

        String descripcion,

        TipoProducto tipo,

        String rareza,

        List<String> habilidades,

        int tiraje,

        Integer precioCreditos,

        BigDecimal precioMonedaReal,

        boolean premium,

        String prototipo,

        String heroe,

        Integer costoPoder,

        BigDecimal multiplicadorNivel,

        Integer turnosCarga,

        Integer turnosRecarga,

        String efectoGeneral,

        String efectoPotenciado,

        Integer defensa,

        ParteArmadura parte,

        String efecto,

        Integer poderDeAtaque,

        BigDecimal tasaDeCaida,

        EstadoProducto estado,

        Integer version,

        Instant creadoEn,

        Instant modificadoEn,

        PromocionVista promocion,

        OrigenProducto origen,

        Integer semillaVersion,

        String modificadoPor) {
}

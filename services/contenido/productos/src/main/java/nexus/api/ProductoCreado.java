package nexus.api;

import java.math.BigDecimal;
import java.time.Instant;

import nexus.dominio.EstadoProducto;
import nexus.dominio.ParteArmadura;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;

public record ProductoCreado(

        String id,

        String nombre,

        String imagen,

        String descripcion,

        TipoProducto tipo,

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

        int version,

        Instant creadoEn,

        Instant modificadoEn) {

        public static ProductoCreado desde(Producto producto) {
                return new ProductoCreado(
                        producto.id(),
                        producto.nombre(),
                        producto.imagen(),
                        producto.descripcion(),
                        producto.tipo(),
                        producto.tiraje(),
                        producto.precioCreditos(),
                        producto.precioMonedaReal(),
                        producto.premium(),
                        producto.prototipo(),
                        producto.heroe(),
                        producto.costoPoder(),
                        producto.multiplicadorNivel(),
                        producto.turnosCarga(),
                        producto.turnosRecarga(),
                        producto.efectoGeneral(),
                        producto.efectoPotenciado(),
                        producto.defensa(),
                        producto.parte(),
                        producto.efecto(),
                        producto.poderDeAtaque(),
                        producto.tasaDeCaida(),
                        producto.estado(),
                        producto.version(),
                        producto.creadoEn(),
                        producto.modificadoEn());
        }
}
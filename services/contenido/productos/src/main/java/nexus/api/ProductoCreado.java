package nexus.api;

import java.math.BigDecimal;
import java.time.Instant;

import nexus.dominio.EstadoProducto;
import nexus.dominio.ParteArmadura;
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
}

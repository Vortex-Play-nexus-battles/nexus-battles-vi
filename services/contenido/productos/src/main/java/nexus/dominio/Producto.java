package nexus.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "productos")
public record Producto(

        @Id
        String id,

        String nombre,

        String imagen,

        String descripcion,

        TipoProducto tipo,

        int tiraje,

        Integer precioCreditos,

        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal precioMonedaReal,

        boolean premium,

        String prototipo,

        String heroe,

        Integer costoPoder,

        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal multiplicadorNivel,

        Integer turnosCarga,

        Integer turnosRecarga,

        String efectoGeneral,

        String efectoPotenciado,

        Integer defensa,

        ParteArmadura parte,

        String efecto,

        Integer poderDeAtaque,

        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal tasaDeCaida,

        EstadoProducto estado,

        int version,

        Instant creadoEn,

        Instant modificadoEn) {
}
package nexus.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import nexus.dominio.TipoProducto;

public record SolicitudCrearProducto(

        @NotBlank
        String nombre,

        @NotBlank
        String imagen,

        @NotBlank
        String descripcion,

        @NotNull
        TipoProducto tipo,

        @NotNull
        Integer tiraje,

        @PositiveOrZero
        Integer precioCreditos,

        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal precioMonedaReal,

        @NotNull
        Boolean premium,

        @Positive
        Integer poderDeAtaque,

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal tasaDeCaida) {

                @AssertTrue(message = "El tiraje debe ser -1 o un entero mayor que cero")
        public boolean isTirajeValido() {
                return tiraje == null || tiraje == -1 || tiraje > 0;
        }

        @AssertTrue(message = "El precio no corresponde al tipo de compra")
        public boolean isPrecioValido() {
                if (premium == null) {
                        return true;
                }

                if (premium) {
                        return precioMonedaReal != null && precioCreditos == null;
                }

                return precioCreditos != null;
        }

        @AssertTrue(message = "Faltan atributos obligatorios para el tipo de producto")
        public boolean isAtributosDelTipoValidos() {
                if (tipo == null) {
                        return true;
                }

                return switch (tipo) {
                        case ARMA -> poderDeAtaque != null && tasaDeCaida != null;
                        default -> true;
                };
        }

}
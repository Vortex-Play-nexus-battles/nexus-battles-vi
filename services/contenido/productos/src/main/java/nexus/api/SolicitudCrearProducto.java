package nexus.api;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import nexus.dominio.ParteArmadura;
import nexus.dominio.TipoProducto;
import jakarta.validation.constraints.Pattern;

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

        String prototipo,

        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "El identificador del héroe debe tener formato UUID")
        String heroe,


        @Positive
        Integer costoPoder,

        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal multiplicadorNivel,

        @PositiveOrZero
        Integer turnosCarga,


        @PositiveOrZero
        Integer turnosRecarga,

        String efectoGeneral,

        String efectoPotenciado,

        @Positive
        Integer defensa,

        ParteArmadura parte,

        String efecto,


        @Positive
        Integer poderDeAtaque,

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal tasaDeCaida) {

        private static final Set<String> PROTOTIPOS_VALIDOS = Set.of(
                "Guerrero Tanque",
                "Guerrero Armas",
                "Mago Fuego",
                "Mago Hielo",
                "Pícaro Veneno",
                "Pícaro Machete",
                "Chamán",
                "Médico");

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
                        case HEROE ->
                                prototipo != null
                                        && PROTOTIPOS_VALIDOS.contains(prototipo);
                        case HABILIDAD ->
                                heroe != null
                                        && costoPoder != null
                                        && multiplicadorNivel != null
                                        && turnosCarga != null;
                        case ARMA ->
                                poderDeAtaque != null
                                        && tasaDeCaida != null;
                        case ARMADURA ->
                                defensa != null
                                        && parte != null
                                        && tasaDeCaida != null;
                                                case ITEM ->
                                efecto != null
                                        && !efecto.isBlank()
                                        && tasaDeCaida != null;
                        case EPICA ->
                                heroe != null
                                        && turnosRecarga != null
                                        && efectoGeneral != null
                                        && !efectoGeneral.isBlank()
                                        && efectoPotenciado != null
                                        && !efectoPotenciado.isBlank();
                        default -> true;
                };
        }

        @AssertTrue(message = "La solicitud contiene atributos que no corresponden al tipo de producto")
        public boolean isSinAtributosIncompatibles() {
                if (tipo == null) {
                        return true;
                }

                return switch (tipo) {
                        case HEROE ->
                                heroe == null
                                        && costoPoder == null
                                        && multiplicadorNivel == null
                                        && turnosCarga == null
                                        && turnosRecarga == null
                                        && efectoGeneral == null
                                        && efectoPotenciado == null
                                        && defensa == null
                                        && parte == null
                                        && efecto == null
                                        && poderDeAtaque == null
                                        && tasaDeCaida == null;
                        case HABILIDAD ->
                                prototipo == null
                                        && turnosRecarga == null
                                        && efectoGeneral == null
                                        && efectoPotenciado == null
                                        && defensa == null
                                        && parte == null
                                        && efecto == null
                                        && poderDeAtaque == null
                                        && tasaDeCaida == null;
                        case ARMA ->
                                prototipo == null
                                        && heroe == null
                                        && costoPoder == null
                                        && multiplicadorNivel == null
                                        && turnosCarga == null
                                        && turnosRecarga == null
                                        && efectoGeneral == null
                                        && efectoPotenciado == null
                                        && defensa == null
                                        && parte == null
                                        && efecto == null;
                        case ARMADURA ->
                                prototipo == null
                                        && heroe == null
                                        && costoPoder == null
                                        && multiplicadorNivel == null
                                        && turnosCarga == null
                                        && turnosRecarga == null
                                        && efectoGeneral == null
                                        && efectoPotenciado == null
                                        && efecto == null
                                        && poderDeAtaque == null;
                        case ITEM ->
                                prototipo == null
                                        && heroe == null
                                        && costoPoder == null
                                        && multiplicadorNivel == null
                                        && turnosCarga == null
                                        && turnosRecarga == null
                                        && efectoGeneral == null
                                        && efectoPotenciado == null
                                        && defensa == null
                                        && parte == null
                                        && poderDeAtaque == null;
                        case EPICA ->
                                prototipo == null
                                        && costoPoder == null
                                        && multiplicadorNivel == null
                                        && turnosCarga == null
                                        && defensa == null
                                        && parte == null
                                        && efecto == null
                                        && poderDeAtaque == null
                                        && tasaDeCaida == null;
                };
        }
}

package nexus.combate;

import java.util.List;
import java.util.Objects;

public record ResultadoBotin(
        EstadoResultadoBotin estado,
        List<BotinOtorgado> botines) {

    public ResultadoBotin {
        Objects.requireNonNull(estado, "El estado es obligatorio");
        botines = List.copyOf(Objects.requireNonNull(botines, "Los botines son obligatorios"));
        if (estado == EstadoResultadoBotin.OTORGADO && botines.isEmpty()) {
            throw new IllegalArgumentException("Un resultado otorgado debe contener botines");
        }
        if (estado != EstadoResultadoBotin.OTORGADO && !botines.isEmpty()) {
            throw new IllegalArgumentException("Solo un resultado otorgado puede contener botines");
        }
    }

    public static ResultadoBotin otorgado(List<BotinOtorgado> botines) {
        return new ResultadoBotin(EstadoResultadoBotin.OTORGADO, botines);
    }

    public static ResultadoBotin sinBotin() {
        return new ResultadoBotin(EstadoResultadoBotin.SIN_BOTIN, List.of());
    }

    public static ResultadoBotin yaProcesado() {
        return new ResultadoBotin(EstadoResultadoBotin.YA_PROCESADO, List.of());
    }
}

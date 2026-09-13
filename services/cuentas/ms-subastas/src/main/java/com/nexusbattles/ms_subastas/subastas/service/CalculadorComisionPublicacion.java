package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CalculadorComisionPublicacion {
    public BigDecimal calcular(DuracionSubasta duracion, boolean esMaestroDeJuego) {
        if (duracion == null) throw new IllegalArgumentException("La duración es obligatoria");
        if (esMaestroDeJuego) return BigDecimal.ZERO;
        return duracion == DuracionSubasta.H24 ? BigDecimal.ONE : BigDecimal.valueOf(3);
    }
}

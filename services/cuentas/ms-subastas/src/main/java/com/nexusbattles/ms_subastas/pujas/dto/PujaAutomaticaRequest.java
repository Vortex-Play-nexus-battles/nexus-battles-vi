package com.nexusbattles.ms_subastas.pujas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param limite tope hasta el que el motor ofertara por el jugador. Que sea
 *               alcanzable y que el saldo lo cubra no se valida aqui: depende
 *               de la subasta y del saldo, y lo decide
 *               {@code MotorPujaAutomaticaService}. Aqui solo se descarta lo
 *               que no es un limite en absoluto.
 */
public record PujaAutomaticaRequest(
        @NotNull(message = "el limite de la puja automatica es obligatorio")
        @DecimalMin(value = "0.01", message = "el limite de la puja automatica debe ser positivo")
        BigDecimal limite) {
}

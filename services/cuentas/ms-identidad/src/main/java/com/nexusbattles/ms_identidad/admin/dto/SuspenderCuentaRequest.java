package com.nexusbattles.ms_identidad.admin.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class SuspenderCuentaRequest {

    @NotNull
    @Future
    private LocalDateTime suspendidoHasta;

    /**
     * B2 — causal documentada (7.3.2 «Causales de sancion»). Opcional por
     * compatibilidad (ms-identidad-admin.yaml): sin ella se usa un motivo
     * generico, porque moderacion-sanciones exige uno.
     */
    @Size(max = 1000)
    private String motivo;

    public LocalDateTime getSuspendidoHasta() { return suspendidoHasta; }
    public void setSuspendidoHasta(LocalDateTime suspendidoHasta) { this.suspendidoHasta = suspendidoHasta; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}

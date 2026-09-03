package com.nexusbattles.ms_identidad.admin.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class SuspenderCuentaRequest {

    @NotNull
    @Future
    private LocalDateTime suspendidoHasta;

    public LocalDateTime getSuspendidoHasta() { return suspendidoHasta; }
    public void setSuspendidoHasta(LocalDateTime suspendidoHasta) { this.suspendidoHasta = suspendidoHasta; }
}

package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.sanciones.CausalesDeSancion;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

public class SuspenderCuentaRequest {

    /**
     * SA-21: llega como instante con su zona (la consola manda
     * {@code toISOString()}) y se guarda en la hora del servidor; una hora sin
     * zona se lee como antes ({@link HoraDelServidor}).
     */
    @NotNull
    @Future
    @JsonDeserialize(using = HoraDelServidor.class)
    private LocalDateTime suspendidoHasta;

    /**
     * B2 — causal documentada (7.3.2 «Causales de sancion»). Opcional por
     * compatibilidad (ms-identidad-admin.yaml): sin ella se usa un motivo
     * generico, porque moderacion-sanciones exige uno.
     */
    @Size(max = 1000)
    private String motivo;

    /**
     * SA-4 — una de las ocho causales de §7.3.2 ({@link CausalesDeSancion}).
     * Opcional en la API (ms-identidad-admin.yaml); la consola la exige.
     */
    @Pattern(regexp = CausalesDeSancion.PATRON, message = CausalesDeSancion.MENSAJE)
    private String causal;

    /** SA-4 — la politica violada (§7.3.7 «referencias a las politicas violadas»). Opcional. */
    @Size(max = 200)
    private String politica;

    public LocalDateTime getSuspendidoHasta() { return suspendidoHasta; }
    public void setSuspendidoHasta(LocalDateTime suspendidoHasta) { this.suspendidoHasta = suspendidoHasta; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getCausal() { return causal; }
    public void setCausal(String causal) { this.causal = causal; }
    public String getPolitica() { return politica; }
    public void setPolitica(String politica) { this.politica = politica; }
}

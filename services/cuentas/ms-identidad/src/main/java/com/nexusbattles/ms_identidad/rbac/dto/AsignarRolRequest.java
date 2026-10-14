package com.nexusbattles.ms_identidad.rbac.dto;

import com.nexusbattles.ms_identidad.rbac.model.Role;
import jakarta.validation.constraints.NotNull;

public class AsignarRolRequest {

    @NotNull
    private Role nuevoRol;

    /** 7.3.5 — la justificacion que queda en la auditoria (rbac.yaml 1.3.0; opcional para no romper a nadie). */
    @jakarta.validation.constraints.Size(max = 500)
    private String motivo;

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Role getNuevoRol() {
        return nuevoRol;
    }

    public void setNuevoRol(Role nuevoRol) {
        this.nuevoRol = nuevoRol;
    }
}
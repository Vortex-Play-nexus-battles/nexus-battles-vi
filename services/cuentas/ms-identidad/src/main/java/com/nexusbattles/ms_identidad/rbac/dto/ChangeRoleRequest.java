package com.nexusbattles.ms_identidad.rbac.dto;

import com.nexusbattles.ms_identidad.rbac.model.Role;
import jakarta.validation.constraints.NotNull;

public class ChangeRoleRequest {

    @NotNull(message = "El identificador del solicitante es obligatorio")
    private Long idSolicitante;

    @NotNull(message = "El nuevo rol es obligatorio")
    private Role nuevoRol;

    public ChangeRoleRequest() {
    }

    public Long getIdSolicitante() {
        return idSolicitante;
    }

    public void setIdSolicitante(Long idSolicitante) {
        this.idSolicitante = idSolicitante;
    }

    public Role getNuevoRol() {
        return nuevoRol;
    }

    public void setNuevoRol(Role nuevoRol) {
        this.nuevoRol = nuevoRol;
    }
}
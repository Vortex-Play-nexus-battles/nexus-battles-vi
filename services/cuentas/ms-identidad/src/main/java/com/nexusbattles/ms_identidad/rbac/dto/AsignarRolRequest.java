package com.nexusbattles.ms_identidad.rbac.dto;

import com.nexusbattles.ms_identidad.rbac.model.Role;
import jakarta.validation.constraints.NotNull;

public class AsignarRolRequest {

    @NotNull
    private Role nuevoRol;

    public Role getNuevoRol() {
        return nuevoRol;
    }

    public void setNuevoRol(Role nuevoRol) {
        this.nuevoRol = nuevoRol;
    }
}
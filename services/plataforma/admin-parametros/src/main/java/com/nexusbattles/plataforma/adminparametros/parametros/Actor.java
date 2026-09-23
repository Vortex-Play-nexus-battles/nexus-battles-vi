package com.nexusbattles.plataforma.adminparametros.parametros;

import java.util.Set;
import java.util.UUID;

/** Quien actua, sacado del token. Configurar parametros es de administracion (HU-ADM-001). */
public record Actor(UUID id, String rol) {

    static final Set<String> ADMINISTRACION = Set.of("ADMINISTRADOR", "SUPER_ADMINISTRADOR");

    public boolean puedeConfigurar() {
        return id != null && ADMINISTRACION.contains(rol);
    }
}

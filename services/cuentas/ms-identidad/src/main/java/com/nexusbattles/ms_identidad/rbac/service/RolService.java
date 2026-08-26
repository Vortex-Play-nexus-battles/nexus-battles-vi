package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.springframework.stereotype.Service;

@Service
public class RolService {

    private final RolRepository rolRepository;

    public RolService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    // Contrato: auth llama a este método para resolver el rol por nombre.
    public RolEntity obtenerRolPorNombre(String nombre) {
        return rolRepository.findByNombre(nombre)
            .orElseThrow(() -> new IllegalStateException(
                "El rol '" + nombre + "' no existe. Verifique que RolSeeder se haya ejecutado."));
    }
}

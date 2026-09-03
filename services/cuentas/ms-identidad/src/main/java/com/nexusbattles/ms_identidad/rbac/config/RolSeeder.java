package com.nexusbattles.ms_identidad.rbac.config;

import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RolSeeder implements CommandLineRunner {

    private final RolRepository rolRepository;

    public RolSeeder(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("JUGADOR", "Usuario estándar del juego");
        crearSiNoExiste("MODERADOR", "Modera comentarios y aplica sanciones temporales");
        crearSiNoExiste("ADMINISTRADOR", "Gestiona usuarios, productos y configuraciones");
        crearSiNoExiste("SUPER_ADMINISTRADOR", "Acceso total, incluida la gestión de administradores");
    }

    private void crearSiNoExiste(String nombre, String descripcion) {
        if (rolRepository.findByNombre(nombre).isEmpty()) {
            RolEntity rol = new RolEntity();
            rol.setNombre(nombre);
            rol.setDescripcion(descripcion);
            rolRepository.save(rol);
        }
    }
}

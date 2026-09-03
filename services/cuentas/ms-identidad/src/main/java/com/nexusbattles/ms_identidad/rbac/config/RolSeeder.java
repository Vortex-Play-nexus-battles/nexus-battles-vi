package com.nexusbattles.ms_identidad.rbac.config;

import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra el catálogo de roles del sistema. Debe correr en todos los
 * ambientes: sin roles no funciona el RBAC.
 *
 * Los usuarios de demostración se siembran aparte, solo en dev/test, en
 * {@code auth.config.UsuariosDemoSeeder} (nunca en producción).
 */
@Component
@Order(1)
public class RolSeeder implements CommandLineRunner {

    private final RolRepository rolRepository;

    public RolSeeder(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    @Override
    public void run(String... args) {
        crearRolSiNoExiste("JUGADOR", "Usuario estándar del juego");
        crearRolSiNoExiste("MODERADOR", "Modera comentarios y aplica sanciones temporales");
        crearRolSiNoExiste("ADMINISTRADOR", "Gestiona usuarios, productos y configuraciones");
        crearRolSiNoExiste("SUPER_ADMINISTRADOR", "Acceso total, incluida la gestión de administradores");
    }

    private void crearRolSiNoExiste(String nombre, String descripcion) {
        if (rolRepository.findByNombre(nombre).isEmpty()) {
            RolEntity rol = new RolEntity();
            rol.setNombre(nombre);
            rol.setDescripcion(descripcion);
            rolRepository.save(rol);
        }
    }
}

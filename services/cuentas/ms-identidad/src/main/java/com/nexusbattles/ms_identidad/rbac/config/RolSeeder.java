package com.nexusbattles.ms_identidad.rbac.config;

import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Los cuatro roles de RF-RBAC-001, creados al arrancar.
 *
 * El {@code @Order} no es adorno. Un {@code CommandLineRunner} sin orden
 * declarado recibe {@code Ordered.LOWEST_PRECEDENCE}, es decir, va el ultimo;
 * y todo lo que necesite que los roles ya existan --
 * {@code AdministradoresIniciales}, por ejemplo -- correria ANTES y se
 * encontraria la tabla vacia. Paso exactamente eso: el arranque se cayo con
 * "El rol 'SUPER_ADMINISTRADOR' no existe". Este seeder no depende de nadie,
 * asi que va primero y lo dice.
 */
@Component
@Order(10)
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

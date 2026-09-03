package com.nexusbattles.ms_identidad.rbac.config;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RolSeeder implements CommandLineRunner {

    // Hash BCrypt de "MiClave123!"
    private static final String PASSWORD_BCRYPT = "$2b$12$pINy9Mytt0XtcGgwmit0JuEzpHfqIqx8//ZY2KrRR0iDckCLUVfru";

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;

    public RolSeeder(RolRepository rolRepository, UsuarioRepository usuarioRepository) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void run(String... args) {
        crearRolSiNoExiste("JUGADOR", "Usuario estándar del juego");
        crearRolSiNoExiste("MODERADOR", "Modera comentarios y aplica sanciones temporales");
        crearRolSiNoExiste("ADMINISTRADOR", "Gestiona usuarios, productos y configuraciones");
        crearRolSiNoExiste("SUPER_ADMINISTRADOR", "Acceso total, incluida la gestión de administradores");

        crearUsuarioSiNoExiste("pruebaadmin", "pruebaadmin@test.com", "ADMINISTRADOR");
        crearUsuarioSiNoExiste("pruebajugador", "pruebajugador@test.com", "JUGADOR");
        crearUsuarioSiNoExiste("pruebamod", "pruebamod@test.com", "MODERADOR");
        crearUsuarioSiNoExiste("pruebasuper", "pruebasuper@test.com", "SUPER_ADMINISTRADOR");
    }

    private void crearRolSiNoExiste(String nombre, String descripcion) {
        if (rolRepository.findByNombre(nombre).isEmpty()) {
            RolEntity rol = new RolEntity();
            rol.setNombre(nombre);
            rol.setDescripcion(descripcion);
            rolRepository.save(rol);
        }
    }

    private void crearUsuarioSiNoExiste(String apodo, String email, String rolNombre) {
        if (usuarioRepository.findByEmail(email).isEmpty()) {
            rolRepository.findByNombre(rolNombre).ifPresent(rol -> {
                Usuario u = new Usuario();
                u.setApodo(apodo);
                u.setEmail(email);
                u.setPassword(PASSWORD_BCRYPT);
                u.setEstado("ACTIVO");
                u.setIntentosFallidos(0);
                u.setRol(rol);
                u.setVersionToken(1);
                usuarioRepository.save(u);
            });
        }
    }
}

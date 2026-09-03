package com.nexusbattles.ms_identidad.auth.config;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra usuarios de prueba (uno por rol) para poder iniciar sesión como
 * cada rol durante la demostración del Sprint.
 *
 * SOLO en los perfiles {@code dev} y {@code test}. En producción no se
 * ejecuta: sembrar un SUPER_ADMINISTRADOR con contraseña conocida sería un
 * agujero de seguridad. Corre después de {@link com.nexusbattles.ms_identidad.rbac.config.RolSeeder}.
 */
@Component
@Order(2)
@Profile({"dev", "test"})
public class UsuariosDemoSeeder implements CommandLineRunner {

    // Hash BCrypt de "MiClave123!" — credencial de demostración, nunca de producción.
    private static final String PASSWORD_BCRYPT =
        "$2b$12$pINy9Mytt0XtcGgwmit0JuEzpHfqIqx8//ZY2KrRR0iDckCLUVfru";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;

    public UsuariosDemoSeeder(UsuarioRepository usuarioRepository, RolRepository rolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
    }

    @Override
    public void run(String... args) {
        crearUsuarioSiNoExiste("pruebajugador", "pruebajugador@test.com", "JUGADOR");
        crearUsuarioSiNoExiste("pruebamod", "pruebamod@test.com", "MODERADOR");
        crearUsuarioSiNoExiste("pruebaadmin", "pruebaadmin@test.com", "ADMINISTRADOR");
        crearUsuarioSiNoExiste("pruebasuper", "pruebasuper@test.com", "SUPER_ADMINISTRADOR");
    }

    private void crearUsuarioSiNoExiste(String apodo, String email, String rolNombre) {
        if (usuarioRepository.findByEmail(email).isPresent()) {
            return;
        }
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

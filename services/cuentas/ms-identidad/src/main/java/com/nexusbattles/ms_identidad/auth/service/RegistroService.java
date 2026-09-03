package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ApodoBlacklistValidator apodoBlacklistValidator;

    @Autowired
    private PasswordPolicyValidator passwordPolicyValidator;

    @Autowired
    private RolService rolService;

    @Autowired
    private PerfilUsuarioService perfilUsuarioService;

    @Autowired
    private CorreoClient correoClient;

    @Autowired
    private AvatarStorageService avatarStorageService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Usuario registrarUsuario(RegistroRequest datos) {

        if (usuarioRepository.findByEmail(datos.getEmail()).isPresent()) {
            throw new RuntimeException("El correo electrónico ya está registrado.");
        }

        if (usuarioRepository.findByApodo(datos.getApodo()).isPresent()) {
            throw new RuntimeException("El apodo ya está en uso.");
        }

        apodoBlacklistValidator.validar(datos.getApodo());

        passwordPolicyValidator.validar(datos.getPassword());

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setApodo(datos.getApodo());
        nuevoUsuario.setEmail(datos.getEmail());
        nuevoUsuario.setPassword(passwordEncoder.encode(datos.getPassword()));
        nuevoUsuario.setEstado("ACTIVO");
        nuevoUsuario.setRol(rolService.obtenerRolPorNombre("JUGADOR"));

        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // El avatar ahora es una foto real subida por el usuario (antes era
        // una URL fija de una galería predefinida). AvatarStorageService
        // valida, guarda el archivo en disco, y devuelve la URL con la que
        // se sirve después.
        String urlAvatar = avatarStorageService.guardarAvatar(datos.getAvatar());

        perfilUsuarioService.crearPerfil(
            usuarioGuardado, datos.getNombres(), datos.getApellidos(), urlAvatar
        );

        // Integración real con el módulo de correo de Santiago Anaya
        // (contracts/openapi/correo.yaml). Protegida con Resilience4j: si el
        // servicio de correo falla, el registro se completa igual (fail-open).
        correoClient.enviarBienvenida(new CorreoBienvenidaRequest(
            datos.getEmail(), datos.getApodo(), datos.getNombres(), datos.getApellidos()
        ));

        return usuarioGuardado;
    }
}

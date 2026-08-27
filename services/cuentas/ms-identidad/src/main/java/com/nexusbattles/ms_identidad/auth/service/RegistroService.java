package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
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

        perfilUsuarioService.crearPerfil(
                usuarioGuardado, datos.getNombres(), datos.getApellidos(), datos.getAvatar()
        );

        // TODO [INTEGRACIÓN FUTURA]: Disparar la integración con el módulo de correo corporativo
        // para el mensaje de bienvenida (confirmar si es Grupo de Felipe o Grupo de Simón).

        return usuarioGuardado;
    }
}
package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
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
    private PerfilUsuarioService perfilUsuarioService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Usuario registrarUsuario(RegistroRequest datos) {

        // 1. Validar si el correo electrónico ya se encuentra registrado
        if (usuarioRepository.findByEmail(datos.getEmail()).isPresent()) {
            throw new RuntimeException("El correo electrónico ya está registrado.");
        }

        // 2. Validar si el apodo ya existe en la base de datos
        if (usuarioRepository.findByApodo(datos.getApodo()).isPresent()) {
            throw new RuntimeException("El apodo ya está en uso.");
        }

        // 3. Validar el apodo contra la lista negra (componente compartido con Sanabria)
        apodoBlacklistValidator.validar(datos.getApodo());

        // 4. Validar la política estricta de la contraseña (HU-AUT-002)
        validarPoliticaContrasena(datos.getPassword());

        // 5. Armar el Usuario con los datos propios de auth
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setApodo(datos.getApodo());
        nuevoUsuario.setEmail(datos.getEmail());
        nuevoUsuario.setPassword(passwordEncoder.encode(datos.getPassword()));
        nuevoUsuario.setEstado("ACTIVO");
        nuevoUsuario.setRol(Role.JUGADOR);

        // 6. Guardar el usuario en la base de datos
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // 7. Crear el perfil asociado (contrato con Sanabria) con los datos del formulario
        perfilUsuarioService.crearPerfil(
                usuarioGuardado, datos.getNombres(), datos.getApellidos(), datos.getAvatar()
        );

        // TODO [INTEGRACIÓN FUTURA]: Disparar la integración con el módulo de correo corporativo
        // para el mensaje de bienvenida (confirmar si es Grupo de Felipe o Grupo de Simón).

        return usuarioGuardado;
    }

    private void validarPoliticaContrasena(String password) {
        if (password == null || password.length() <= 8) {
            throw new RuntimeException("La contraseña debe tener una longitud superior a 8 caracteres.");
        }
        boolean tieneMayuscula = password.chars().anyMatch(Character::isUpperCase);
        boolean tieneMinuscula = password.chars().anyMatch(Character::isLowerCase);
        boolean tieneNumero = password.chars().anyMatch(Character::isDigit);
        boolean tieneSimbolo = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        if (!tieneMayuscula || !tieneMinuscula || !tieneNumero || !tieneSimbolo) {
            throw new RuntimeException("La contraseña debe contener al menos una mayúscula, una minúscula, un número y un símbolo.");
        }
    }
}
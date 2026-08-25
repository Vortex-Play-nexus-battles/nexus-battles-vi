package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegistroService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Lista negra temporal en memoria mientras el equipo de Felipe expone el servicio definitivo (HU-ADM-002)
    private static final List<String> APODOS_PROHIBIDOS_TEMPORAL = List.of(
            "admin", "root", "system", "moderador", "sex", "hack"
    );

    public Usuario registrarUsuario(Usuario nuevoUsuario) {

        // 1. Validar si el correo electrónico ya se encuentra registrado
        if (usuarioRepository.findByEmail(nuevoUsuario.getEmail()).isPresent()) {
            throw new RuntimeException("El correo electrónico ya está registrado.");
        }

        // 2. Validar si el apodo ya existe en la base de datos local
        if (usuarioRepository.findByApodo(nuevoUsuario.getApodo()).isPresent()) {
            throw new RuntimeException("El apodo ya está en uso.");
        }

        // 3. Validar el apodo contra la lista negra temporal (Placeholder)
        validarListaNegraApodo(nuevoUsuario.getApodo());
        // TODO [INTEGRACIÓN FUTURA]: Reemplazar esta validación local por la llamada al servicio
        // de Lista Negra de términos prohibidos del equipo de Felipe (HU-ADM-002 / RF-ADM-002).

        // 4. Validar la política estricta de la contraseña (HU-AUT-002)
        validarPoliticaContrasena(nuevoUsuario.getPassword());

        // 5. CIFRAR LA CONTRASEÑA antes de guardarla
        String passwordCifrada = passwordEncoder.encode(nuevoUsuario.getPassword());
        nuevoUsuario.setPassword(passwordCifrada);

        // 6. Guardar el usuario en la base de datos
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // TODO [INTEGRACIÓN FUTURA]: Disparar la integración con el módulo de correo corporativo
        // para el mensaje de bienvenida (HU-COR-002 / Grupo de Felipe).

        return usuarioGuardado;
    }

    // Método auxiliar para validar la lista negra temporal
    private void validarListaNegraApodo(String apodo) {
        if (apodo == null) return;
        String apodoNormalizado = apodo.toLowerCase().trim();
        for (String prohibido : APODOS_PROHIBIDOS_TEMPORAL) {
            if (apodoNormalizado.contains(prohibido)) {
                throw new RuntimeException("El apodo contiene términos prohibidos por la política de la comunidad.");
            }
        }
    }

    // Método auxiliar para validar la política estricta de la contraseña
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
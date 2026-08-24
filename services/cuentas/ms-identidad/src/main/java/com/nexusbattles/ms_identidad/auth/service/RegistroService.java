package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegistroService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Usuario registrarUsuario(Usuario nuevoUsuario) {

        // 1. Validar si el correo electrónico ya se encuentra registrado
        if (usuarioRepository.findByEmail(nuevoUsuario.getEmail()).isPresent()) {
            throw new RuntimeException("El correo electrónico ya está registrado.");
        }

        // 2. Validar si el apodo ya existe en la base de datos de usuarios
        if (usuarioRepository.findByApodo(nuevoUsuario.getApodo()).isPresent()) {
            throw new RuntimeException("El apodo ya está en uso.");
        }

        // TODO [INTEGRACIÓN FUTURA]: Validar el apodo contra el servicio de Lista Negra
        // que entregará el otro equipo (HU-AUT-003 / RF-AUT-003)[cite: 2].

        // TODO [INTEGRACIÓN FUTURA]: Validar la política estricta de la contraseña
        // (longitud > 8, mayúsculas, minúsculas, números y símbolos - HU-AUT-002)[cite: 2].

        // 3. Guardar el usuario en la base de datos con rol "Jugador" y estado "activo" por defecto
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // TODO [INTEGRACIÓN FUTURA]: Disparar la integración con el módulo de correo corporativo
        // para el mensaje de bienvenida (HU-COR-002 / Grupo de Simón)[cite: 2].
        // Si el correo falla, la cuenta se crea igual y el reenvío queda encolado[cite: 2].

        return usuarioGuardado;
    }
}
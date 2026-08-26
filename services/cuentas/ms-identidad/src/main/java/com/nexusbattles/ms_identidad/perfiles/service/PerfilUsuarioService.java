package com.nexusbattles.ms_identidad.perfiles.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilUsuarioService {

    private final PerfilUsuarioRepository perfilUsuarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApodoBlacklistValidator apodoBlacklistValidator;

    public PerfilUsuarioService(PerfilUsuarioRepository perfilUsuarioRepository,
                                UsuarioRepository usuarioRepository,
                                ApodoBlacklistValidator apodoBlacklistValidator) {
        this.perfilUsuarioRepository = perfilUsuarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
    }

    // Contrato: Cristian llama a este método al terminar de crear un Usuario
    // (HU-AUT-001), para que se cree el registro de perfil asociado.
    public PerfilUsuario crearPerfil(Usuario usuario, String nombres, String apellidos, String avatar) {
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres(nombres);
        perfil.setApellidos(apellidos);
        perfil.setAvatar(avatar);
        return perfilUsuarioRepository.save(perfil);
    }

    // HU-USR-001: consultar mi propio perfil
    public PerfilUsuario obtenerPorUsuarioId(Long usuarioId) {
        return perfilUsuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("No existe perfil para el usuario " + usuarioId));
    }

    // HU-USR-001: modificar mi propio perfil (y opcionalmente el apodo)
    @Transactional
    public PerfilUsuario actualizarPerfilPropio(Long usuarioId, String nombres, String apellidos,
                                                String avatar, String biografia, String preferencias,
                                                String nuevoApodo) {

        PerfilUsuario perfil = obtenerPorUsuarioId(usuarioId);
        perfil.setNombres(nombres);
        perfil.setApellidos(apellidos);
        perfil.setAvatar(avatar);
        perfil.setBiografia(biografia);
        perfil.setPreferencias(preferencias);
        perfilUsuarioRepository.save(perfil);

        if (nuevoApodo != null && !nuevoApodo.isBlank()) {
            Usuario usuario = perfil.getUsuario();
            if (!nuevoApodo.equals(usuario.getApodo())) {
                apodoBlacklistValidator.validar(nuevoApodo);
                if (usuarioRepository.findByApodo(nuevoApodo).isPresent()) {
                    throw new IllegalArgumentException("El apodo ya está en uso.");
                }
                usuario.setApodo(nuevoApodo);
                usuarioRepository.save(usuario);
            }
        }

        // TODO [INTEGRACIÓN FUTURA - HU-AUD-001, ms-cumplimiento]: registrar este cambio
        // en auditoría. El paquete auditoria de ms-cumplimiento todavía no tiene servicio expuesto.

        return perfil;
    }
}
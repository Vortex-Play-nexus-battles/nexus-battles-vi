package com.nexusbattles.ms_identidad.perfiles.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PerfilUsuarioService {

    private final PerfilUsuarioRepository perfilUsuarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApodoBlacklistValidator apodoBlacklistValidator;
    private final AvatarStorageService avatarStorageService;

    public PerfilUsuarioService(PerfilUsuarioRepository perfilUsuarioRepository,
                                UsuarioRepository usuarioRepository,
                                ApodoBlacklistValidator apodoBlacklistValidator,
                                AvatarStorageService avatarStorageService) {
        this.perfilUsuarioRepository = perfilUsuarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
        this.avatarStorageService = avatarStorageService;
    }

    // Contrato: Cristian llama a este método al terminar de crear un Usuario
    // (HU-AUT-001), pasando ya la URL resuelta del avatar (él mismo llama a
    // AvatarStorageService antes de esto en su RegistroService).
    public PerfilUsuario crearPerfil(Usuario usuario, String nombres, String apellidos, String avatarUrl) {
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres(nombres);
        perfil.setApellidos(apellidos);
        perfil.setAvatar(avatarUrl);
        return perfilUsuarioRepository.save(perfil);
    }

    // HU-USR-001: consultar mi propio perfil
    public PerfilUsuario obtenerPorUsuarioId(Long usuarioId) {
        return perfilUsuarioRepository.findByIdConUsuario(usuarioId)
            .orElseThrow(() -> new IllegalStateException("No existe perfil para el usuario " + usuarioId));
    }

    // HU-USR-001: modificar mi propio perfil (y opcionalmente el apodo y el avatar)
    @Transactional
    public PerfilUsuario actualizarPerfilPropio(Long usuarioId, String nombres, String apellidos,
                                                MultipartFile nuevoAvatar, String preferencias,
                                                String nuevoApodo) {

        PerfilUsuario perfil = obtenerPorUsuarioId(usuarioId);
        perfil.setNombres(nombres);
        perfil.setApellidos(apellidos);
        perfil.setPreferencias(preferencias);

        // Solo se reemplaza el avatar si de verdad llega un archivo nuevo;
        // si no, se conserva el que ya tenia.
        if (nuevoAvatar != null && !nuevoAvatar.isEmpty()) {
            String urlAvatar = avatarStorageService.guardarAvatar(nuevoAvatar);
            perfil.setAvatar(urlAvatar);
        }

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

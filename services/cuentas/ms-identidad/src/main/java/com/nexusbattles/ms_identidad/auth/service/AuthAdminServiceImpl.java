package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuthAdminServiceImpl implements AuthAdminService {

    private final UsuarioRepository usuarioRepository;
    private final ApodoBlacklistValidator apodoBlacklistValidator;
    private final RolService rolService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final List<String> ESTADOS_VALIDOS = List.of("ACTIVA", "SUSPENDIDA", "BANEADA");

    public AuthAdminServiceImpl(UsuarioRepository usuarioRepository,
                                ApodoBlacklistValidator apodoBlacklistValidator,
                                RolService rolService) {
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
        this.rolService = rolService;
    }

    // ---------- Para Edwin (HU-RBAC-003) ----------

    @Override
    public Role obtenerRolDeUsuario(Long usuarioId) {
        return Role.valueOf(buscarOFallar(usuarioId).getRol().getNombre());
    }

    @Override
    public long contarPorRol(Role rol) {
        return usuarioRepository.countByRol(rolService.obtenerRolPorNombre(rol.name()));
    }

    @Override
    public void actualizarRol(Long usuarioId, Role nuevoRol) {
        Usuario usuario = buscarOFallar(usuarioId);
        usuario.setRol(rolService.obtenerRolPorNombre(nuevoRol.name()));
        usuarioRepository.save(usuario);
    }

    // ---------- Para Sanabria (HU-USR-002) ----------

    @Override
    public Usuario crearCuentaConRol(String nombres, String apellidos, String email,
                                     String apodo, String avatar, Role rol) {

        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }
        if (usuarioRepository.findByApodo(apodo).isPresent()) {
            throw new IllegalArgumentException("El apodo ya está en uso.");
        }
        apodoBlacklistValidator.validar(apodo);

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setApodo(apodo);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setEstado("INACTIVO");
        nuevoUsuario.setRol(rolService.obtenerRolPorNombre(rol.name()));

        String passwordTemporal = generarPasswordTemporal();
        nuevoUsuario.setPassword(passwordEncoder.encode(passwordTemporal));

        Usuario guardado = usuarioRepository.save(nuevoUsuario);

        // TODO [INTEGRACIÓN FUTURA]: enviar la contraseña temporal por correo
        // corporativo, en vez de generarla y perderla como ahora. Requiere el
        // módulo de correo (Grupo de Simón/Felipe, confirmar).

        return guardado;
    }

    // ---------- Para Sanabria (HU-USR-003) ----------

    @Override
    public void actualizarEstadoCuenta(Long usuarioId, String nuevoEstado) {
        if (!ESTADOS_VALIDOS.contains(nuevoEstado)) {
            throw new IllegalArgumentException(
                    "Estado inválido. Valores permitidos: " + ESTADOS_VALIDOS);
        }
        Usuario usuario = buscarOFallar(usuarioId);
        usuario.setEstado(nuevoEstado);
        usuarioRepository.save(usuario);
    }

    @Override
    public void restablecerContrasena(Long usuarioId) {
        Usuario usuario = buscarOFallar(usuarioId);
        String passwordTemporal = generarPasswordTemporal();
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuarioRepository.save(usuario);

        // TODO [INTEGRACIÓN FUTURA]: enviar passwordTemporal por correo al usuario.
    }

    // ---------- Auxiliares ----------

    private Usuario buscarOFallar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("No existe el usuario " + usuarioId));
    }

    private String generarPasswordTemporal() {
        return "Tmp-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
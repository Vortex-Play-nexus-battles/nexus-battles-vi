package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthAdminServiceImpl implements AuthAdminService {

    private final UsuarioRepository usuarioRepository;
    private final ApodoBlacklistValidator apodoBlacklistValidator;
    private final RolService rolService;
    private final TokenCredencialService tokenCredencialService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Corregido: "ACTIVO" (no "ACTIVA") — coincide con el valor real que usa
    // Usuario.estado por defecto en el auto-registro (RegistroService).
    // Antes había una inconsistencia de ortografía entre los 2 flujos.
    private static final List<String> ESTADOS_VALIDOS = List.of("ACTIVO", "SUSPENDIDA", "BANEADA");

    public AuthAdminServiceImpl(UsuarioRepository usuarioRepository,
                                ApodoBlacklistValidator apodoBlacklistValidator,
                                RolService rolService,
                                TokenCredencialService tokenCredencialService) {
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
        this.rolService = rolService;
        this.tokenCredencialService = tokenCredencialService;
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

        // Password de relleno: nadie la conoce ni la necesita conocer. Es
        // solo para satisfacer la restricción NOT NULL de la columna hasta
        // que el usuario canjee su token y defina su propia contraseña real.
        nuevoUsuario.setPassword(passwordEncoder.encode(generarValorAleatorio()));

        Usuario guardado = usuarioRepository.save(nuevoUsuario);

        // Corregido (hallazgo de Sanabria, punto 1): antes esta contraseña
        // se perdía sin ninguna forma de que el usuario accediera a su
        // cuenta. Ahora se genera un token de activación de un solo uso.
        tokenCredencialService.generarYRegistrarToken(guardado, "ACTIVACION");

        return guardado;
    }

    // ---------- Para Sanabria (HU-USR-003) ----------

    @Override
    public void actualizarEstadoCuenta(Long usuarioId, String nuevoEstado, LocalDateTime suspendidoHasta) {
        if (!ESTADOS_VALIDOS.contains(nuevoEstado)) {
            throw new IllegalArgumentException(
                "Estado inválido. Valores permitidos: " + ESTADOS_VALIDOS);
        }
        Usuario usuario = buscarOFallar(usuarioId);
        usuario.setEstado(nuevoEstado);

        if ("SUSPENDIDA".equals(nuevoEstado)) {
            usuario.setSuspendidoHasta(suspendidoHasta);
        } else {
            usuario.setSuspendidoHasta(null);
        }

        usuarioRepository.save(usuario);
    }

    @Override
    public void restablecerContrasena(Long usuarioId) {
        Usuario usuario = buscarOFallar(usuarioId);

        // Mismo motivo que en crearCuentaConRol: password de relleno,
        // inservible, hasta que se canjee el token.
        usuario.setPassword(passwordEncoder.encode(generarValorAleatorio()));
        usuarioRepository.save(usuario);

        // Corregido (hallazgo de Sanabria, punto 1): antes esta contraseña
        // también se perdía. Ahora se genera un token de restablecimiento.
        tokenCredencialService.generarYRegistrarToken(usuario, "RESTABLECIMIENTO");
    }

    @Override
    public String obtenerEstadoCuenta(Long usuarioId) {
        // Nuevo (hallazgo de Sanabria, punto 3): permite consultar el
        // estado actual antes de decidir una acción administrativa, por
        // ejemplo evitar reactivar una cuenta que en realidad está baneada.
        return buscarOFallar(usuarioId).getEstado();
    }

    // ---------- Auxiliares ----------

    private Usuario buscarOFallar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalStateException("No existe el usuario " + usuarioId));
    }

    private String generarValorAleatorio() {
        return UUID.randomUUID().toString();
    }
}

package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
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
    private final CodigosDeCorreo codigos;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // B2 — los estados de sancion con el nombre del contrato
    // (ms-identidad-admin.yaml): SUSPENDIDO y BANEADO, ya no en femenino.
    // Solo los usa la via local (cuentas sin uid, que moderacion-sanciones no
    // conoce); las demas proyectan la sancion desde moderacion.
    private static final List<String> ESTADOS_VALIDOS =
            List.of(EstadoCuenta.ACTIVO, EstadoCuenta.SUSPENDIDO, EstadoCuenta.BANEADO);

    public AuthAdminServiceImpl(UsuarioRepository usuarioRepository,
                                ApodoBlacklistValidator apodoBlacklistValidator,
                                RolService rolService,
                                CodigosDeCorreo codigos) {
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
        this.rolService = rolService;
        this.codigos = codigos;
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

        // Nuevo (pedido de Edwin, HU-RBAC-003): al cambiar el rol, se
        // incrementa la versión de token. Cualquier JWT ya emitido con la
        // versión anterior deja de ser válido en la próxima verificación,
        // aunque su firma siga siendo correcta y no haya expirado —evita
        // que alguien siga actuando con un rol que ya le fue revocado.
        usuario.setVersionToken(usuario.getVersionToken() + 1);

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
        nuevoUsuario.setEstado(EstadoCuenta.INACTIVO);
        nuevoUsuario.setRol(rolService.obtenerRolPorNombre(rol.name()));

        // Password de relleno: nadie la conoce ni la necesita conocer. Es
        // solo para satisfacer la restricción NOT NULL de la columna hasta
        // que el usuario canjee su token y defina su propia contraseña real.
        nuevoUsuario.setPassword(passwordEncoder.encode(generarValorAleatorio()));

        Usuario guardado = usuarioRepository.save(nuevoUsuario);

        // Corregido (hallazgo de Sanabria, punto 1): antes esta contraseña
        // se perdía sin ninguna forma de que el usuario accediera a su
        // cuenta. Ahora se genera un codigo de activación de un solo uso.
        // B1: guardado como resumen BCrypt; el correo (proposito ACTIVACION)
        // sale al confirmarse la transaccion del alta.
        codigos.emitir(guardado, TipoCodigo.ACTIVACION);

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

        if (EstadoCuenta.SUSPENDIDO.equals(nuevoEstado)) {
            usuario.setSuspendidoHasta(suspendidoHasta);
        } else {
            usuario.setSuspendidoHasta(null);
        }
        // B2: suspender o banear cierra las sesiones abiertas (antes el token
        // seguia valido hasta caducar, 24 h, aunque el login ya se negara).
        if (!EstadoCuenta.ACTIVO.equals(nuevoEstado)) {
            usuario.setVersionToken(usuario.getVersionToken() + 1);
        }

        usuarioRepository.save(usuario);
    }

    @Override
    public void restablecerContrasena(Long usuarioId) {
        Usuario usuario = buscarOFallar(usuarioId);

        // Mismo motivo que en crearCuentaConRol: password de relleno,
        // inservible, hasta que se canjee el codigo. Y como la contraseña
        // anterior deja de valer, tambien las sesiones abiertas con ella (B1).
        usuario.setPassword(passwordEncoder.encode(generarValorAleatorio()));
        usuario.setVersionToken(usuario.getVersionToken() + 1);
        usuarioRepository.save(usuario);

        // Corregido (hallazgo de Sanabria, punto 1): antes esta contraseña
        // también se perdía. B1: la misma emision que «olvide mi contraseña»
        // (resumen BCrypt, anula los anteriores, correo tras el commit).
        codigos.emitir(usuario, TipoCodigo.RESTABLECIMIENTO);
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

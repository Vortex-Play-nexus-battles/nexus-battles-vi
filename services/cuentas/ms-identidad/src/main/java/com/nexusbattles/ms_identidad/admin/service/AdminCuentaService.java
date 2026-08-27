package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AdminCuentaService {

    private final UsuarioRepository usuarioRepository;
    private final ApodoBlacklistValidator apodoBlacklistValidator;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PerfilUsuarioService perfilUsuarioService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminCuentaService(UsuarioRepository usuarioRepository,
                              ApodoBlacklistValidator apodoBlacklistValidator,
                              PasswordPolicyValidator passwordPolicyValidator,
                              PerfilUsuarioService perfilUsuarioService) {
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @Transactional
    public Usuario crearCuentaAdministrativa(CrearCuentaAdminRequest datos) {

        if (datos.getRol() != Role.MODERADOR && datos.getRol() != Role.ADMINISTRADOR) {
            throw new IllegalArgumentException(
                    "Solo se pueden crear cuentas con rol MODERADOR o ADMINISTRADOR desde este endpoint.");
        }

        String emailNormalizado = datos.getEmail().trim().toLowerCase(Locale.ROOT);
        String apodoNormalizado = datos.getApodo().trim();

        apodoBlacklistValidator.validar(apodoNormalizado);
        passwordPolicyValidator.validar(datos.getPassword());

        if (usuarioRepository.findByEmail(emailNormalizado).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }
        if (usuarioRepository.findByApodo(apodoNormalizado).isPresent()) {
            throw new IllegalArgumentException("El apodo ya está en uso.");
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setApodo(apodoNormalizado);
        nuevoUsuario.setEmail(emailNormalizado);
        nuevoUsuario.setPassword(passwordEncoder.encode(datos.getPassword()));
        // TODO [DEPENDE DE HU-AUT-004]: nada en el proyecto cambia este estado a ACTIVO
        // todavia. El contrato pendiente es: cuando Cristian implemente el login,
        // la primera autenticacion exitosa de esta cuenta debe poner estado=ACTIVO.
        nuevoUsuario.setEstado("INACTIVO");
        nuevoUsuario.setRol(datos.getRol());

        Usuario usuarioGuardado;
        try {
            usuarioGuardado = usuarioRepository.save(nuevoUsuario);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("El correo o el apodo ya están en uso.");
        }

        perfilUsuarioService.crearPerfil(
                usuarioGuardado, datos.getNombres(), datos.getApellidos(), datos.getAvatar()
        );

        // TODO [INTEGRACIÓN FUTURA - HU-AUT-007]: obligar enrolamiento de segundo factor.
        // TODO [INTEGRACIÓN FUTURA - HU-AUD-001, ms-cumplimiento]: registrar en auditoría.

        return usuarioGuardado;
    }
}
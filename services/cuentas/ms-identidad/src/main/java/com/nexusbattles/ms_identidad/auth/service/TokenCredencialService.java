package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoConfirmacionCuentaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoRecuperacionClaveRequest;
import com.nexusbattles.ms_identidad.auth.exception.TokenInvalidoException;
import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class TokenCredencialService {

    // Sin 0/O ni 1/I: caracteres que se confunden facil al transcribir un
    // codigo a mano desde un correo. 10 caracteres de este alfabeto de 32
    // dan ~1.1x10^15 combinaciones -- mucho mas fuerte que los 6 digitos
    // numericos que sugiere el ejemplo del contrato de correo ('482915'),
    // y cabe dentro del limite real: codigo maxLength: 12
    // (contracts/openapi/correo.yaml).
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LONGITUD_CODIGO = 10;

    private final SecureRandom aleatorio = new SecureRandom();

    @Value("${app.seguridad.horas-expiracion-token:24}")
    private int horasExpiracion;

    @Autowired
    private TokenCredencialRepository tokenCredencialRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CorreoClient correoClient;

    // HU-AUT-006 (hallazgo de #441): el canje de recuperacion aceptaba
    // cualquier contraseña nueva; la politica de RF-AUT-002 solo se aplicaba
    // en el registro. Misma pieza que el registro y el cambio de contraseña.
    @Autowired
    private PasswordPolicyValidator passwordPolicyValidator;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Punto de entrada publico de auto-servicio: "olvide mi contraseña".
     * Si el correo no corresponde a ninguna cuenta, no hace nada -- para
     * no permitir enumerar correos registrados (ver mensaje generico en
     * AuthController).
     */
    @Transactional
    public void solicitarRestablecimiento(String email) {
        usuarioRepository.findByEmail(email)
            .ifPresent(usuario -> generarYRegistrarToken(usuario, "RESTABLECIMIENTO"));
    }

    @Transactional
    public void generarYRegistrarToken(Usuario usuario, String tipo) {
        String token = generarCodigoUnico();
        LocalDateTime expiracion = LocalDateTime.now().plusHours(horasExpiracion);

        tokenCredencialRepository.save(new TokenCredencial(usuario, token, tipo, expiracion));

        enviarCorreoSegunTipo(usuario, tipo, token);
    }

    /**
     * HALLAZGO: antes generaba un UUID de 32 caracteres (sin guiones).
     * El contrato real de correo (CorreoConfirmacionCuentaRequest y
     * CorreoRecuperacionClaveRequest) exige codigo con maxLength: 12 --
     * un UUID nunca pudo pasar esa validacion. Esto no es un bug nuevo de
     * HU-COR-003: afecta por igual a "ACTIVACION" (HU-COR-002), que
     * comparte este mismo metodo desde Sprint 1. Es muy probable que
     * ningun correo de confirmacion de cuenta se haya enviado nunca,
     * cayendo siempre en el fallback silencioso de CorreoClient sin que
     * nadie lo notara hasta probar el flujo de punta a punta hoy.
     */
    private String generarCodigoUnico() {
        String codigo;
        do {
            StringBuilder builder = new StringBuilder(LONGITUD_CODIGO);
            for (int i = 0; i < LONGITUD_CODIGO; i++) {
                builder.append(ALFABETO_CODIGO.charAt(aleatorio.nextInt(ALFABETO_CODIGO.length())));
            }
            codigo = builder.toString();
        } while (tokenCredencialRepository.findByToken(codigo).isPresent());
        return codigo;
    }

    private void enviarCorreoSegunTipo(Usuario usuario, String tipo, String token) {
        int minutosVigencia = horasExpiracion * 60;

        if ("RESTABLECIMIENTO".equals(tipo)) {
            correoClient.enviarRecuperacionClave(new CorreoRecuperacionClaveRequest(
                usuario.getEmail(), usuario.getApodo(), token, minutosVigencia
            ));
        } else if ("ACTIVACION".equals(tipo)) {
            correoClient.enviarConfirmacionCuenta(new CorreoConfirmacionCuentaRequest(
                usuario.getEmail(), usuario.getApodo(), token, minutosVigencia
            ));
        }
    }

    @Transactional
    public void canjearToken(String token, String nuevaPassword) {
        TokenCredencial tokenCredencial = tokenCredencialRepository.findByToken(token)
            .orElseThrow(() -> new TokenInvalidoException("El enlace no es válido."));

        if (tokenCredencial.isUsado()) {
            throw new TokenInvalidoException("Este enlace ya fue utilizado.");
        }
        if (LocalDateTime.now().isAfter(tokenCredencial.getFechaExpiracion())) {
            throw new TokenInvalidoException("Este enlace ha expirado.");
        }

        // La politica se comprueba con el codigo ya validado y ANTES de
        // marcarlo usado: un rechazo por politica no puede quemar el codigo.
        passwordPolicyValidator.validar(nuevaPassword);

        Usuario usuario = tokenCredencial.getUsuario();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));

        if ("ACTIVACION".equals(tokenCredencial.getTipo())) {
            usuario.setEstado("ACTIVO");
        }

        tokenCredencial.setUsado(true);

        usuarioRepository.save(usuario);
        tokenCredencialRepository.save(tokenCredencial);
    }
}

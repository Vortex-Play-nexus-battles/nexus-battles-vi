package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.exception.TokenInvalidoException;
import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TokenCredencialService {

    // TODO [INTEGRACIÓN FUTURA]: reemplazar este logger por el envío real
    // del correo con el link de canje, en cuanto Santiago publique
    // contracts/openapi/correo.yaml. Mientras tanto, el token queda
    // registrado aquí para poder probar el flujo manualmente.
    private static final Logger log = LoggerFactory.getLogger("TOKENS_CREDENCIAL");

    @Value("${app.seguridad.horas-expiracion-token:24}")
    private int horasExpiracion;

    @Autowired
    private TokenCredencialRepository tokenCredencialRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public void generarYRegistrarToken(Usuario usuario, String tipo) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiracion = LocalDateTime.now().plusHours(horasExpiracion);

        tokenCredencialRepository.save(new TokenCredencial(usuario, token, tipo, expiracion));

        log.info("TOKEN_GENERADO tipo={} usuarioId={} email={} token={} expira={}",
            tipo, usuario.getId(), usuario.getEmail(), token, expiracion);
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

package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.onboarding.service.OnboardingService;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistroService {

    /** Limites de las columnas de {@code usuarios} (V1): pasarlos era un 500 de la base de datos. */
    static final int MAX_APODO = 50;
    static final int MAX_EMAIL = 100;
    static final int MAX_NOMBRE = 255;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ApodoBlacklistValidator apodoBlacklistValidator;

    @Autowired
    private PasswordPolicyValidator passwordPolicyValidator;

    @Autowired
    private RolService rolService;

    @Autowired
    private PerfilUsuarioService perfilUsuarioService;

    @Autowired
    private CorreoClient correoClient;

    @Autowired
    private AvatarStorageService avatarStorageService;

    @Autowired
    private OnboardingService onboardingService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Usuario registrarUsuario(RegistroRequest datos) {
        return registrarUsuario(datos, null, null);
    }

    /**
     * Alta de un jugador (HU-AUT-001) y arranque de su bootstrap (R17).
     *
     * <p>La cuenta, su perfil y el registro de su alta se guardan en esta
     * transaccion; los creditos y el heroe iniciales se piden a sus duenos
     * cuando se confirma ({@code AlRegistrarJugador}). Si algo de aqui falla,
     * no queda nada: ni cuenta a medias ni alta huerfana.
     *
     * @param traceparent cabecera W3C de la peticion, si vino; el alta la
     *                    conserva para que todos sus intentos compartan traza
     * @param ip          para la auditoria del alta
     */
    @Transactional
    public Usuario registrarUsuario(RegistroRequest datos, String traceparent, String ip) {

        String email = normalizarCorreo(datos.getEmail());
        String apodo = datos.getApodo() == null ? "" : datos.getApodo().trim();
        exigirLongitud(apodo, MAX_APODO, "apodo", "El apodo");
        exigirLongitud(email, MAX_EMAIL, "email", "El correo");
        exigirLongitud(datos.getNombres(), MAX_NOMBRE, "nombres", "Los nombres");
        exigirLongitud(datos.getApellidos(), MAX_NOMBRE, "apellidos", "Los apellidos");

        // Sin distinguir mayusculas (R17): el login encuentra la cuenta por el
        // correo en minusculas, asi que dos cuentas que solo difieren en
        // mayusculas serian una sola puerta con dos llaves.
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegistroRechazadoException(Motivo.CORREO_EN_USO, "El correo electrónico ya está registrado.");
        }

        if (usuarioRepository.existsByApodoIgnoreCase(apodo)) {
            throw new RegistroRechazadoException(Motivo.APODO_EN_USO, "El apodo ya está en uso.");
        }

        try {
            apodoBlacklistValidator.validar(apodo);
        } catch (IllegalArgumentException prohibido) {
            throw new RegistroRechazadoException(Motivo.APODO_NO_PERMITIDO, prohibido.getMessage());
        }

        try {
            passwordPolicyValidator.validar(datos.getPassword());
        } catch (IllegalArgumentException debil) {
            throw new RegistroRechazadoException(Motivo.CONTRASENA_DEBIL, debil.getMessage());
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setApodo(apodo);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setPassword(passwordEncoder.encode(datos.getPassword()));
        nuevoUsuario.setEstado("ACTIVO");
        nuevoUsuario.setRol(rolService.obtenerRolPorNombre("JUGADOR"));

        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);

        // El avatar ahora es una foto real subida por el usuario (antes era
        // una URL fija de una galería predefinida). AvatarStorageService
        // valida, guarda el archivo en disco, y devuelve la URL con la que
        // se sirve después.
        String urlAvatar;
        try {
            urlAvatar = avatarStorageService.guardarAvatar(datos.getAvatar());
        } catch (IllegalArgumentException imagenInvalida) {
            throw new RegistroRechazadoException(Motivo.AVATAR_INVALIDO, imagenInvalida.getMessage());
        }

        perfilUsuarioService.crearPerfil(
            usuarioGuardado, datos.getNombres(), datos.getApellidos(), urlAvatar
        );

        // R17 — el alta del jugador nace con la cuenta. Todo jugador nuevo
        // empieza con creditos y un heroe equipado; eso lo hacen sus duenos
        // (ms-finanzas, inventario) por API cuando esta transaccion se confirme.
        onboardingService.iniciar(
            usuarioGuardado.getPublicId(), apodo,
            Traza.traceIdDe(traceparent).orElseGet(Traza::nuevoTraceId), ip
        );

        // Integración real con el módulo de correo de Santiago Anaya
        // (contracts/openapi/correo.yaml). Protegida con Resilience4j: si el
        // servicio de correo falla, el registro se completa igual (fail-open).
        correoClient.enviarBienvenida(new CorreoBienvenidaRequest(
            email, apodo, datos.getNombres(), datos.getApellidos()
        ));

        return usuarioGuardado;
    }

    /** Correo como se guarda desde R17: sin espacios alrededor y en minusculas. */
    static String normalizarCorreo(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    private static void exigirLongitud(String valor, int maximo, String campo, String nombre) {
        if (valor != null && valor.length() > maximo) {
            throw new RegistroRechazadoException(Motivo.DATOS_INVALIDOS,
                nombre + " no puede tener más de " + maximo + " caracteres.", campo);
        }
    }
}

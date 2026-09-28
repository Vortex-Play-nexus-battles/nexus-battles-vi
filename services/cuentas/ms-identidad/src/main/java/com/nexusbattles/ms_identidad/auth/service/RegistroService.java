package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.CuentaRegistrada;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Autorregistro de un jugador (HU-AUT-001).
 *
 * <p><b>B1 — la cuenta nace sin verificar.</b> Feedback del profesor tras la
 * demo: «se puede registrar un correo que no existe y seguir usando la
 * cuenta». Desde B1 el alta deja la cuenta en {@code PENDIENTE_VERIFICACION}
 * y envia al correo un codigo de un solo uso; hasta que se confirma
 * ({@code POST /auth/verificacion/confirmacion}) el login responde 403
 * {@code cuenta-no-verificada}. La propiedad del buzon se prueba con ese
 * codigo, <b>nunca</b> preguntando si el buzon existe (sin SMTP VRFY, sin
 * heuristicas): eso no prueba nada y ademas filtra informacion.
 *
 * <p>Por lo mismo, <b>el alta del jugador</b> (creditos de bienvenida, heroe
 * inicial y equipo) y el correo de bienvenida ya no ocurren aqui: ocurren al
 * confirmar el correo, una sola vez. Una cuenta con un correo que nadie lee
 * no recibe nada.
 */
@Service
public class RegistroService {

    /** Limites de las columnas de {@code usuarios} (V1): pasarlos era un 500 de la base de datos. */
    static final int MAX_APODO = 50;
    static final int MAX_EMAIL = 100;
    static final int MAX_NOMBRE = 255;

    private final UsuarioRepository usuarioRepository;
    private final ApodoBlacklistValidator apodoBlacklistValidator;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final RolService rolService;
    private final PerfilUsuarioService perfilUsuarioService;
    private final AvatarStorageService avatarStorageService;
    private final CodigosDeCorreo codigos;
    private final ApplicationEventPublisher eventos;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // R17 — inyeccion por constructor (Sonar S6813): antes eran campos
    // @Autowired, y cada dependencia nueva sumaba un aviso.
    public RegistroService(UsuarioRepository usuarioRepository,
                           ApodoBlacklistValidator apodoBlacklistValidator,
                           PasswordPolicyValidator passwordPolicyValidator,
                           RolService rolService,
                           PerfilUsuarioService perfilUsuarioService,
                           AvatarStorageService avatarStorageService,
                           CodigosDeCorreo codigos,
                           ApplicationEventPublisher eventos) {
        this.usuarioRepository = usuarioRepository;
        this.apodoBlacklistValidator = apodoBlacklistValidator;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.rolService = rolService;
        this.perfilUsuarioService = perfilUsuarioService;
        this.avatarStorageService = avatarStorageService;
        this.codigos = codigos;
        this.eventos = eventos;
    }

    @Transactional
    public Usuario registrarUsuario(RegistroRequest datos) {
        return registrarUsuario(datos, null);
    }

    /**
     * Alta de una cuenta pendiente de verificar.
     *
     * <p>La cuenta, su perfil y su codigo de verificacion se guardan en esta
     * transaccion; el correo con el codigo y la auditoria del alta salen
     * cuando se confirma ({@code CorreosDeCuenta}). Si algo de aqui falla, no
     * queda nada: ni cuenta a medias ni un codigo enviado a nadie.
     *
     * <p>La lista negra es <b>fail-closed</b> (B2): si moderacion no responde,
     * {@code ModeracionNoDisponibleException} deshace todo y la ruta responde
     * 503; un apodo sin comprobar no se da por bueno.
     *
     * @param ip para la auditoria del alta
     */
    @Transactional
    public Usuario registrarUsuario(RegistroRequest datos, String ip) {

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

        // La lista negra va ANTES que la unicidad (B13): un apodo prohibido es
        // prohibido exista o no una cuenta con el. En DEV, «SpiderMan» se
        // habia registrado antes de la lista negra y quien probaba la lista
        // recibia «El apodo ya está en uso»: parecia que no funcionaba. Asi
        // tampoco se revela que esa cuenta existe.
        try {
            apodoBlacklistValidator.validar(apodo);
        } catch (IllegalArgumentException prohibido) {
            throw new RegistroRechazadoException(Motivo.APODO_NO_PERMITIDO, prohibido.getMessage());
        }

        if (usuarioRepository.existsByApodoIgnoreCase(apodo)) {
            throw new RegistroRechazadoException(Motivo.APODO_EN_USO, "El apodo ya está en uso.");
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
        nuevoUsuario.setEstado(EstadoCuenta.PENDIENTE_VERIFICACION);
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

        // B1 — el codigo que prueba que el correo es de quien se registra. Sale
        // al correo (proposito VERIFICACION) cuando esta transaccion se confirma.
        codigos.emitir(usuarioGuardado, TipoCodigo.VERIFICACION);
        eventos.publishEvent(new CuentaRegistrada(usuarioGuardado.getPublicId(), apodo, ip));

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

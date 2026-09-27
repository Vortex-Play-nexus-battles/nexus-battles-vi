package com.nexusbattles.ms_identidad.auth.verificacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion;
import com.nexusbattles.ms_identidad.auth.codigos.CorreoVerificado;
import com.nexusbattles.ms_identidad.auth.codigos.IgualadorDeTiempo;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.onboarding.service.OnboardingService;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Confirmacion del correo de un autorregistro y reenvio del codigo (B1; 7.4.12
 * «confirmar la creacion de una cuenta de usuario»).
 *
 * <p>Las dos rutas son publicas —quien las usa todavia no puede iniciar
 * sesion— y ninguna dice si una cuenta existe: la confirmacion responde el
 * mismo 400 {@code codigo-invalido} a todo caso negativo y el reenvio el
 * mismo 202 siempre. Los dos caminos, con o sin cuenta, pagan el mismo BCrypt
 * ({@link IgualadorDeTiempo}).
 */
@Service
public class VerificacionDeCorreoService {

    static final String CONFIRMADA = "Tu correo quedó verificado. Ya puedes iniciar sesión.";
    public static final String MENSAJE_REENVIO = "Si existe una cuenta pendiente asociada, recibirás un código nuevo.";

    private final UsuarioRepository usuarios;
    private final PerfilUsuarioRepository perfiles;
    private final CodigosDeCorreo codigos;
    private final IgualadorDeTiempo igualador;
    private final OnboardingService onboarding;
    private final ApplicationEventPublisher eventos;

    public VerificacionDeCorreoService(UsuarioRepository usuarios,
                                       PerfilUsuarioRepository perfiles,
                                       CodigosDeCorreo codigos,
                                       IgualadorDeTiempo igualador,
                                       OnboardingService onboarding,
                                       ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.codigos = codigos;
        this.igualador = igualador;
        this.onboarding = onboarding;
        this.eventos = eventos;
    }

    /**
     * Canjea el codigo: {@code PENDIENTE_VERIFICACION -> ACTIVO}, y entonces
     * (una sola vez) el alta del jugador, la auditoria y la bienvenida.
     *
     * <p>El orden importa: primero se marca el codigo usado y despues la
     * cuenta pasa a ACTIVO con un UPDATE condicionado al estado pendiente.
     * Dos confirmaciones a la vez: una de las dos se encuentra el codigo ya
     * usado o la cuenta ya activa, responde 400 y se deshace; el alta nunca se
     * lanza dos veces.
     *
     * @throws CodigoInvalidoException  400 {@code codigo-invalido}
     * @throws com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException 429
     */
    @Transactional
    public VerificacionResponse confirmar(String email, String codigo, String ip) {
        Optional<Usuario> encontrada = usuarios.buscarPorCorreo(email);
        if (encontrada.isEmpty() || !EstadoCuenta.PENDIENTE_VERIFICACION.equals(encontrada.get().getEstado())) {
            igualador.comparar(codigo);
            throw new CodigoInvalidoException();
        }
        Usuario usuario = encontrada.get();

        Comprobacion valida = codigos.comprobar(usuario.getId(), TipoCodigo.VERIFICACION, codigo).exigirValida();
        if (!codigos.marcarUsado(valida.codigoId())
                || usuarios.activarSiPendiente(usuario.getId(), EstadoCuenta.PENDIENTE_VERIFICACION,
                        EstadoCuenta.ACTIVO) != 1) {
            throw new CodigoInvalidoException();
        }

        if (usuario.getPublicId() != null) {
            onboarding.iniciar(usuario.getPublicId(), usuario.getApodo(),
                    Traza.actual().orElseGet(Traza::nuevoTraceId), ip);
        }
        Optional<PerfilUsuario> perfil = perfiles.findByIdConUsuario(usuario.getId());
        eventos.publishEvent(new CorreoVerificado(usuario.getPublicId(), usuario.getEmail(), usuario.getApodo(),
                perfil.map(PerfilUsuario::getNombres).orElse(null),
                perfil.map(PerfilUsuario::getApellidos).orElse(null), ip));
        return new VerificacionResponse(EstadoCuenta.ACTIVO, CONFIRMADA);
    }

    /**
     * Emite un codigo nuevo si hay una cuenta PENDIENTE con ese correo y no se
     * ha pasado el limite de frecuencia; si no, no hace nada. Nunca dice cual
     * de los casos ocurrio.
     */
    @Transactional
    public void reenviar(String email) {
        // Bloqueada la fila de la cuenta, dos reenvios a la vez se turnan y el
        // segundo ve el codigo del primero al contar: el limite es exacto.
        Optional<Usuario> pendiente = usuarios.buscarPorCorreo(email)
                .filter(usuario -> EstadoCuenta.PENDIENTE_VERIFICACION.equals(usuario.getEstado()))
                .flatMap(usuario -> usuarios.bloquear(usuario.getId()));
        if (pendiente.isEmpty() || !codigos.admiteOtroEnvio(pendiente.get().getId(), TipoCodigo.VERIFICACION)) {
            igualador.resumir();
            return;
        }
        codigos.emitir(pendiente.get(), TipoCodigo.VERIFICACION);
    }
}

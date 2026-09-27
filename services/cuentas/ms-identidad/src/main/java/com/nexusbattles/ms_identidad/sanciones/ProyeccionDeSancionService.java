package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * La proyeccion de una sancion sobre la cuenta (B2, sanciones unificadas).
 *
 * <p>La fuente de verdad de una sancion es moderacion-sanciones: su historial
 * dice quien, cuando, por que y hasta cuando. Aqui solo queda lo que el
 * login necesita para negarse sin llamar a otro servicio (el 7.3.2 pide que
 * la suspension «impida acceder al sistema durante el periodo» y que el baneo
 * «inhabilite permanentemente»): el estado, el fin de la suspension y que
 * sancion los produjo.
 *
 * <p>Reglas, pensadas para que moderacion pueda reintentar sin miedo y para
 * que el orden de llegada no deshaga lo que no toca:
 * <ul>
 *   <li><b>Idempotente</b> por {@code sancionId} + {@code estado}: repetir la
 *       misma proyeccion no vuelve a revocar sesiones. Con SUSPENDIDO solo
 *       puede cambiar el fin (una apelacion REDUCIDA).</li>
 *   <li>Pasar a SUSPENDIDO (vigente) o BANEADO sube {@code versionToken}: los
 *       tokens ya emitidos caducan en el acto en este servicio.</li>
 *   <li>Una suspension no rebaja un baneo de otra sancion.</li>
 *   <li>ACTIVO solo levanta la restriccion si la produjo ESA misma sancion: el
 *       levantamiento de una sancion vieja no borra otra vigente.</li>
 *   <li>Levantar una restriccion devuelve la cuenta a ACTIVO, o a
 *       PENDIENTE_VERIFICACION si nunca confirmo su correo: una sancion no
 *       puede servir de atajo para saltarse la verificacion.</li>
 * </ul>
 */
@Service
public class ProyeccionDeSancionService {

    static final Set<String> ESTADOS = Set.of(EstadoCuenta.ACTIVO, EstadoCuenta.SUSPENDIDO, EstadoCuenta.BANEADO);

    private final UsuarioRepository usuarios;
    private final CodigosDeCorreo codigos;
    private final Clock reloj;

    @Autowired
    public ProyeccionDeSancionService(UsuarioRepository usuarios, CodigosDeCorreo codigos) {
        this(usuarios, codigos, Clock.systemDefaultZone());
    }

    public ProyeccionDeSancionService(UsuarioRepository usuarios, CodigosDeCorreo codigos, Clock reloj) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.reloj = reloj;
    }

    /** {@code PUT /internal/usuarios/{uid}/estado-sancion}: lo que manda moderacion-sanciones. */
    @Transactional
    public EstadoDeCuentaResponse proyectar(UUID uid, ProyeccionDeSancionRequest proyeccion) {
        validar(proyeccion);
        // Bloqueada: dos proyecciones de la misma cuenta a la vez se turnan.
        Usuario usuario = usuarios.bloquearPorIdentificadorPublico(uid).orElseThrow(CuentaNoEncontradaException::new);
        aplicar(usuario, proyeccion.estado(), proyeccion.hasta(), proyeccion.sancionId());
        return estadoDe(usuario);
    }

    /**
     * Aplica una proyeccion sobre una cuenta ya cargada. La usa tambien el
     * panel, con la respuesta de moderacion, antes de que llegue la de
     * moderacion por la ruta interna (que entonces no cambia nada).
     */
    @Transactional
    public void aplicar(Usuario usuario, String estado, OffsetDateTime hasta, UUID sancionId) {
        String actual = EstadoCuenta.normalizado(usuario.getEstado());
        boolean mismaSancion = sancionId != null && sancionId.equals(usuario.getSancionId());
        switch (estado) {
            case EstadoCuenta.SUSPENDIDO -> suspender(usuario, actual, mismaSancion, local(hasta), sancionId);
            case EstadoCuenta.BANEADO -> banear(usuario, actual, mismaSancion, sancionId);
            case EstadoCuenta.ACTIVO -> {
                if (mismaSancion) {
                    quitarRestriccion(usuario);
                }
            }
            default -> throw new ProyeccionInvalidaException("Estado de sancion desconocido: " + estado);
        }
        usuarios.save(usuario);
    }

    /**
     * Levanta la restriccion que haya, venga de donde venga (reactivacion desde
     * el panel cuando moderacion confirma que no hay ninguna sancion vigente,
     * o una suspension antigua del panel anterior a B2).
     */
    @Transactional
    public void levantar(Usuario usuario) {
        quitarRestriccion(usuario);
        usuarios.save(usuario);
    }

    /**
     * Al iniciar sesion: una suspension cuyo plazo ya paso se limpia y la
     * persona entra (7.3.2: la suspension dura «el periodo»). Devuelve el
     * estado resultante, que puede ser PENDIENTE_VERIFICACION si la cuenta
     * nunca confirmo su correo.
     */
    @Transactional
    public String levantarSiVencida(Usuario usuario) {
        LocalDateTime hasta = usuario.getSuspendidoHasta();
        if (EstadoCuenta.esSuspendido(usuario.getEstado())
                && (hasta == null || !LocalDateTime.now(reloj).isBefore(hasta))) {
            quitarRestriccion(usuario);
            usuarios.save(usuario);
        }
        return usuario.getEstado();
    }

    public EstadoDeCuentaResponse estadoDe(Usuario usuario) {
        return new EstadoDeCuentaResponse(usuario.getPublicId(), EstadoCuenta.normalizado(usuario.getEstado()),
                usuario.getSuspendidoHasta() == null ? null
                        : usuario.getSuspendidoHasta().atZone(reloj.getZone()).toOffsetDateTime(),
                usuario.getVersionToken());
    }

    private void suspender(Usuario usuario, String actual, boolean mismaSancion, LocalDateTime fin, UUID sancionId) {
        if (fin == null) {
            throw new ProyeccionInvalidaException("Una suspension necesita su fin ('hasta').");
        }
        if (mismaSancion && EstadoCuenta.SUSPENDIDO.equals(actual)) {
            // La misma suspension otra vez, o reducida por una apelacion: el
            // fin puede cambiar, las sesiones ya se revocaron al empezar.
            usuario.setSuspendidoHasta(fin);
            return;
        }
        if (EstadoCuenta.BANEADO.equals(actual) && !mismaSancion) {
            return;
        }
        usuario.setEstado(EstadoCuenta.SUSPENDIDO);
        usuario.setSuspendidoHasta(fin);
        usuario.setSancionId(sancionId);
        if (fin.isAfter(LocalDateTime.now(reloj))) {
            revocarSesiones(usuario);
        }
    }

    private void banear(Usuario usuario, String actual, boolean mismaSancion, UUID sancionId) {
        if (mismaSancion && EstadoCuenta.BANEADO.equals(actual)) {
            return;
        }
        usuario.setEstado(EstadoCuenta.BANEADO);
        usuario.setSuspendidoHasta(null);
        usuario.setSancionId(sancionId);
        revocarSesiones(usuario);
    }

    private void quitarRestriccion(Usuario usuario) {
        String actual = usuario.getEstado();
        if (EstadoCuenta.esSuspendido(actual) || EstadoCuenta.esBaneado(actual)) {
            usuario.setEstado(codigos.nuncaVerificada(usuario.getId())
                    ? EstadoCuenta.PENDIENTE_VERIFICACION : EstadoCuenta.ACTIVO);
        }
        usuario.setSuspendidoHasta(null);
    }

    private static void revocarSesiones(Usuario usuario) {
        usuario.setVersionToken(usuario.getVersionToken() + 1);
    }

    private LocalDateTime local(OffsetDateTime instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante.toInstant(), reloj.getZone());
    }

    private static void validar(ProyeccionDeSancionRequest proyeccion) {
        if (proyeccion == null || proyeccion.sancionId() == null) {
            throw new ProyeccionInvalidaException("Falta sancionId: la proyeccion necesita la sancion que la produce.");
        }
        if (proyeccion.estado() == null || !ESTADOS.contains(proyeccion.estado())) {
            throw new ProyeccionInvalidaException("El estado debe ser ACTIVO, SUSPENDIDO o BANEADO.");
        }
        if (EstadoCuenta.SUSPENDIDO.equals(proyeccion.estado()) && proyeccion.hasta() == null) {
            throw new ProyeccionInvalidaException("Una suspension necesita su fin ('hasta').");
        }
    }
}

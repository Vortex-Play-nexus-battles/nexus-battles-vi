package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.recuperacion.PreguntaSeguridadRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ejecuta el derecho al olvido de una cuenta cuyo plazo vencio (HU-PRV-005
 * CA-01/CA-04, RF-PRV-005).
 *
 * <p><b>Que se elimina</b> (todo de la base de ms-identidad, en una sola
 * transaccion): el perfil (nombres, apellidos, avatar, preferencias), las
 * preguntas de seguridad, las huellas de los dispositivos, los codigos de
 * un solo uso y el segundo factor (secreto TOTP cifrado, codigos de
 * recuperacion y desafios de acceso, V5). El archivo del avatar se borra del
 * disco despues de confirmar.
 *
 * <p><b>Que se sustituye</b> en la fila de la cuenta, que no se borra porque
 * su {@code uid} es la referencia de los registros que hay que conservar: el
 * apodo pasa a un alias aleatorio ({@code eliminado-<12 hex>}) que no deriva
 * del anterior; el correo, a una direccion del dominio reservado
 * {@code .invalid} (RFC 2606: nunca se entrega); la contrasena, al resumen de
 * un valor aleatorio que nadie conoce; el estado, a {@code ELIMINADO}; y la
 * version de token sube, asi que toda sesion abierta deja de valer. Se limpian
 * los intentos, el bloqueo, la suspension y la ultima entrada.
 *
 * <p><b>Que se conserva</b> (RF-PRV-005, RF-AUD-005): el {@code uid}, el rol y
 * la fecha de alta, y con ellos los registros financieros y de auditoria de
 * los demas servicios, que ya no permiten saber quien era la persona.
 *
 * <p><b>Idempotente.</b> Bloquea la solicitud y solo actua si sigue
 * {@code PROGRAMADA} y vencida: dos ejecuciones a la vez, o una repetida tras
 * un reinicio, no anonimizan dos veces ni cambian el alias.
 *
 * <p><b>Lo que todavia no hace</b> (queda en el informe de RFINAL-05): enviar
 * el correo de confirmacion (correo no tiene plantilla de cierre de cuenta) y
 * anonimizar las copias del apodo que guardan otros servicios.
 */
@Component
public class AnonimizadorDeCuentas {

    private static final Logger log = LoggerFactory.getLogger(AnonimizadorDeCuentas.class);

    static final String PREFIJO_ALIAS = "eliminado-";
    /** Dominio reservado: ningun proveedor entrega a {@code .invalid} (RFC 2606). */
    static final String DOMINIO_NO_ENTREGABLE = "cuenta-eliminada.invalid";

    private static final SecureRandom AZAR = new SecureRandom();

    private final SolicitudDeCierreRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final PerfilUsuarioRepository perfiles;
    private final PreguntaSeguridadRepository preguntas;
    private final AvatarStorageService avatares;
    private final AuditoriaDeCuenta auditoria;
    private final TransactionTemplate transaccion;
    private final PasswordEncoder cifrador;
    private final Clock reloj;

    /** Lo que hay que hacer cuando la transaccion ya se confirmo. */
    private record Hecho(UUID uid, String avatar) {
    }

    @Autowired
    public AnonimizadorDeCuentas(SolicitudDeCierreRepository solicitudes,
                                 UsuarioRepository usuarios,
                                 PerfilUsuarioRepository perfiles,
                                 PreguntaSeguridadRepository preguntas,
                                 AvatarStorageService avatares,
                                 AuditoriaDeCuenta auditoria,
                                 PlatformTransactionManager transacciones) {
        this(solicitudes, usuarios, perfiles, preguntas, avatares, auditoria, transacciones,
                new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con cifrador y reloj explicitos: pruebas. */
    AnonimizadorDeCuentas(SolicitudDeCierreRepository solicitudes,
                          UsuarioRepository usuarios,
                          PerfilUsuarioRepository perfiles,
                          PreguntaSeguridadRepository preguntas,
                          AvatarStorageService avatares,
                          AuditoriaDeCuenta auditoria,
                          PlatformTransactionManager transacciones,
                          PasswordEncoder cifrador,
                          Clock reloj) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.preguntas = preguntas;
        this.avatares = avatares;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transacciones);
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    /**
     * Ejecuta la solicitud si sigue programada y ya vencio.
     *
     * @return {@code true} si la cuenta quedo anonimizada ahora
     */
    public boolean ejecutar(UUID solicitudId) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        // Optional y no null: la transacción devuelve siempre un valor, y que
        // no haya nada que hacer se dice con un Optional vacío (SonarCloud
        // java:S2583 leía el null como imposible).
        Optional<Hecho> resultado = Objects.requireNonNullElse(
                transaccion.execute(estado -> anonimizarDentro(solicitudId, ahora)), Optional.empty());
        if (resultado.isEmpty()) {
            return false;
        }
        Hecho hecho = resultado.get();
        // Ya confirmado: si algo de esto falla, la cuenta sigue anonimizada.
        if (hecho.avatar() != null) {
            try {
                avatares.borrarAvatar(hecho.avatar());
            } catch (RuntimeException fallo) {
                log.warn("Cuenta {} anonimizada, pero su avatar no se pudo borrar del disco: {}",
                        hecho.uid(), fallo.getMessage());
            }
        }
        try {
            auditoria.cuentaAnonimizada(hecho.uid());
        } catch (RuntimeException fallo) {
            log.warn("Cuenta {} anonimizada sin asiento en la auditoria: {}", hecho.uid(), fallo.getMessage());
        }
        // Sin plantilla de cierre en el servicio de correo, la confirmacion
        // queda aqui (bitacora JSON) y en la auditoria: ver el informe.
        log.info("CUENTA_ANONIMIZADA uid={} solicitud={} correoDeConfirmacion=NO_ENVIADO_SIN_PLANTILLA",
                hecho.uid(), solicitudId);
        return true;
    }

    private Optional<Hecho> anonimizarDentro(UUID solicitudId, LocalDateTime ahora) {
        Optional<SolicitudDeCierre> bloqueada = solicitudes.bloquear(solicitudId);
        if (bloqueada.isEmpty() || !bloqueada.get().vencida(ahora)) {
            return Optional.empty();
        }
        SolicitudDeCierre solicitud = bloqueada.get();
        UUID uid = solicitud.getUsuarioUid();

        Optional<Usuario> encontrada = usuarios.bloquearPorIdentificadorPublico(uid);
        if (encontrada.isEmpty()) {
            // No queda nada que anonimizar; se cierra la solicitud para no reintentarla.
            log.warn("Solicitud de cierre {} sin cuenta {}: se marca ejecutada", solicitudId, uid);
            solicitud.marcarEjecutada(ahora);
            solicitudes.save(solicitud);
            return Optional.empty();
        }
        Usuario cuenta = encontrada.get();
        String avatar = perfiles.findByIdentificadorPublicoConUsuario(uid)
                .map(PerfilUsuario::getAvatar)
                .orElse(null);

        Long id = cuenta.getId();
        solicitudes.borrarPerfilDe(id);
        preguntas.borrarDe(id);
        solicitudes.borrarDispositivosDe(id);
        solicitudes.borrarCodigosDe(id);
        // Segundo factor (V5): el secreto cifrado, los codigos de recuperacion y
        // los desafios del login en dos pasos.
        solicitudes.borrarSegundoFactorDe(id);
        solicitudes.borrarCodigosDeRecuperacionDe(id);
        solicitudes.borrarDesafiosDe(id);

        String alias = PREFIJO_ALIAS + HexFormat.of().formatHex(bytesAleatorios(6));
        cuenta.setApodo(alias);
        cuenta.setEmail(alias + "@" + DOMINIO_NO_ENTREGABLE);
        cuenta.setPassword(cifrador.encode(Base64.getEncoder().encodeToString(bytesAleatorios(32))));
        cuenta.setEstado(EstadoCuenta.ELIMINADO);
        cuenta.setVersionToken(cuenta.getVersionToken() + 1);
        cuenta.setIntentosFallidos(0);
        cuenta.setBloqueadoHasta(null);
        cuenta.setSuspendidoHasta(null);
        cuenta.setUltimoAcceso(null);
        usuarios.save(cuenta);

        solicitud.marcarEjecutada(ahora);
        solicitudes.save(solicitud);
        return Optional.of(new Hecho(uid, avatar));
    }

    private static byte[] bytesAleatorios(int cuantos) {
        byte[] bytes = new byte[cuantos];
        AZAR.nextBytes(bytes);
        return bytes;
    }
}

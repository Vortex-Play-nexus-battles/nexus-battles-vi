package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Los desafios del login en dos pasos — HU-AUT-007 CA-01 y CA-03.
 *
 * <p>{@code LoginService} pregunta aqui, con la contrasena ya comprobada, si
 * la cuenta necesita un segundo paso:
 * <ul>
 *   <li>tiene segundo factor activo -> desafio {@code VERIFICAR}: falta el codigo;</li>
 *   <li>no lo tiene pero su rol lo exige ({@code IDENTIDAD_2FA_OBLIGATORIO_ROLES})
 *       -> desafio {@code ENROLAR}: falta enrolarse (o 503 si el servicio no
 *       puede enrolar: sin clave no se emite una sesion que la politica prohibe);</li>
 *   <li>ninguna de las dos -> nada: el login sigue exactamente como antes.</li>
 * </ul>
 *
 * <p>No depende de {@code LoginService} (que si depende de esta clase): el
 * canje del desafio vive en {@link SegundoPasoDelLogin}, asi no hay ciclo.
 */
@Component
public class DesafiosDeAcceso {

    /** 256 bits: con esa entropia el resumen SHA-256 basta, no hace falta BCrypt. */
    private static final int BYTES_DEL_DESAFIO = 32;
    /** Lo que mide como mucho un valor que merece buscarse (el real mide 43). */
    static final int LONGITUD_MAXIMA = 128;

    private final DesafioDeAccesoRepository desafios;
    private final SegundoFactorRepository segundos;
    private final PoliticaDeSegundoFactor politica;
    private final CifradoDeSecretos cifrado;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    @Autowired
    public DesafiosDeAcceso(DesafioDeAccesoRepository desafios, SegundoFactorRepository segundos,
                            PoliticaDeSegundoFactor politica, CifradoDeSecretos cifrado) {
        this(desafios, segundos, politica, cifrado, Clock.systemDefaultZone());
    }

    /** Con reloj explicito: pruebas. */
    public DesafiosDeAcceso(DesafioDeAccesoRepository desafios, SegundoFactorRepository segundos,
                            PoliticaDeSegundoFactor politica, CifradoDeSecretos cifrado, Clock reloj) {
        this.desafios = desafios;
        this.segundos = segundos;
        this.politica = politica;
        this.cifrado = cifrado;
        this.reloj = reloj;
    }

    /**
     * Con la contrasena ya comprobada: el desafio que hace falta, o vacio si
     * la cuenta entra sin segundo paso.
     *
     * @throws SegundoFactorRechazadoException 503 si el rol exige segundo
     *         factor, la cuenta no lo tiene y el servicio no puede enrolarla
     */
    public Optional<DesafioEmitido> exigirSegundoPaso(Usuario usuario) {
        if (segundos.existsByUsuarioIdAndActivoTrue(usuario.getId())) {
            return Optional.of(emitir(usuario, Proposito.VERIFICAR));
        }
        String rol = usuario.getRol() == null ? null : usuario.getRol().getNombre();
        if (politica.esObligatorioPara(rol)) {
            if (!cifrado.disponible()) {
                throw new SegundoFactorRechazadoException(Motivo.NO_DISPONIBLE,
                        "Tu rol exige la verificación en dos pasos y ahora mismo no se puede activar. "
                                + "Avisa a quien administra la plataforma.");
            }
            return Optional.of(emitir(usuario, Proposito.ENROLAR));
        }
        return Optional.empty();
    }

    private DesafioEmitido emitir(Usuario usuario, Proposito proposito) {
        byte[] bytes = new byte[BYTES_DEL_DESAFIO];
        azar.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDateTime expira = ahora.plus(politica.vigenciaDelDesafio());
        // Los de antes que ya no sirven se van: la tabla no crece con cada intento.
        desafios.borrarGastados(usuario.getId(), ahora);
        desafios.save(new DesafioDeAcceso(usuario.getId(), resumen(valor), proposito, usuario.getVersionToken(),
                ahora, expira));
        return new DesafioEmitido(valor, reloj.instant().plus(politica.vigenciaDelDesafio()), proposito);
    }

    /**
     * El desafio vigente con ese valor y ese proposito, bloqueado hasta el
     * final de la transaccion de quien lo canjea.
     *
     * @throws SegundoFactorRechazadoException 401 {@code desafio-invalido}
     */
    public DesafioDeAcceso vigente(String valor, Proposito proposito) {
        if (valor == null || valor.isBlank() || valor.length() > LONGITUD_MAXIMA) {
            throw SegundoFactorRechazadoException.desafioInvalido();
        }
        DesafioDeAcceso desafio = desafios.bloquearPorResumen(resumen(valor.strip()))
                .orElseThrow(SegundoFactorRechazadoException::desafioInvalido);
        if (!desafio.vigente(LocalDateTime.now(reloj)) || desafio.getProposito() != proposito) {
            throw SegundoFactorRechazadoException.desafioInvalido();
        }
        return desafio;
    }

    /** Lo gasta: no vuelve a servir. */
    public void consumir(DesafioDeAcceso desafio) {
        desafio.usar(LocalDateTime.now(reloj));
        desafios.save(desafio);
    }

    /** SHA-256 en hexadecimal: lo unico del desafio que toca la base. */
    static String resumen(String valor) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("La JVM no ofrece SHA-256.", imposible);
        }
    }
}

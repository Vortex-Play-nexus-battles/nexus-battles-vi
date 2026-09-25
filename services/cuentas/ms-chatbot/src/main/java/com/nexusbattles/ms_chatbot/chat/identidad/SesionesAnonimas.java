package com.nexusbattles.ms_chatbot.chat.identidad;

import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

// B11 (ms-chatbot.yaml 2.0.0): emite y valida las sesiones de los visitantes.
//
// El identificador que recibe el navegador es 'anon_' + 256 bits de
// SecureRandom en base64url (43 caracteres): no se puede adivinar, y su forma
// no puede confundirse con un uid (un UUID). En la base solo queda su huella
// SHA-256.
//
// Validar es: forma correcta + huella conocida + no vencida. Cualquier otra
// cosa (un uid, un valor inventado por el cliente, una sesion vencida) se
// trata como si no hubiera sesion. Nunca da acceso a la conversacion de un
// usuario registrado: esa vive en la clave del uid, que ninguna sesion puede
// producir.
@Service
public class SesionesAnonimas {

    static final String PREFIJO = "anon_";
    static final Pattern FORMA = Pattern.compile("^anon_[A-Za-z0-9_-]{43}$");
    private static final int BYTES_ALEATORIOS = 32;

    private final SesionAnonimaRepository repositorio;
    private final LimitadorDeFrecuencia limitador;
    private final Clock reloj;
    private final Duration inactividadMaxima;
    private final SecureRandom aleatorio = new SecureRandom();

    public SesionesAnonimas(SesionAnonimaRepository repositorio, LimitadorDeFrecuencia limitador, Clock reloj,
                            @Value("${chatbot.sesion-anonima.horas-inactividad:24}") long horasDeInactividad) {
        this.repositorio = repositorio;
        this.limitador = limitador;
        this.reloj = reloj;
        this.inactividadMaxima = Duration.ofHours(horasDeInactividad);
    }

    /** Una sesion recien emitida: el identificador (solo lo conoce el navegador) y su sesion. */
    public record Emitida(String identificador, SesionAnonima sesion) {
    }

    // Emite una sesion nueva para un visitante. Limitada por origen y en total,
    // para que no se fabriquen sesiones en masa para esquivar el limite de
    // mensajes (429).
    @Transactional
    public Emitida emitir(String origen) {
        limitador.exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, origen);
        limitador.exigir(LimitadorDeFrecuencia.Regla.SESIONES_GLOBAL, "*");
        byte[] bytes = new byte[BYTES_ALEATORIOS];
        aleatorio.nextBytes(bytes);
        String identificador = PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SesionAnonima sesion = repositorio.save(new SesionAnonima(huella(identificador), ahora(), inactividadMaxima));
        return new Emitida(identificador, sesion);
    }

    // La sesion que corresponde al identificador, si el servidor la emitio y
    // sigue vigente. Vacio en cualquier otro caso.
    @Transactional(readOnly = true)
    public Optional<SesionAnonima> validar(String identificador) {
        if (identificador == null || !FORMA.matcher(identificador).matches()) {
            return Optional.empty();
        }
        Instant ahora = ahora();
        return repositorio.findByHuella(huella(identificador)).filter(sesion -> sesion.vigente(ahora));
    }

    // Cada mensaje del visitante alarga su sesion.
    @Transactional
    public void renovar(SesionAnonima sesion) {
        repositorio.findById(sesion.getId()).ifPresent(actual -> {
            actual.renovar(ahora(), inactividadMaxima);
            repositorio.save(actual);
        });
    }

    static String huella(String identificador) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(identificador.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("La JVM no trae SHA-256", imposible);
        }
    }

    private Instant ahora() {
        return reloj.instant();
    }
}

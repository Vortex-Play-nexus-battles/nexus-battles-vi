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
import java.util.UUID;
import java.util.regex.Pattern;

// B11 (ms-chatbot.yaml 1.2.0): las sesiones de los visitantes. Hay dos formas:
//
//   * EMITIDA por este servidor: 'anon_' + 256 bits de SecureRandom en
//     base64url (43 caracteres). La recomendada: no se puede adivinar.
//   * DECLARADA por el navegador: 'visitante-' + 10 a 64 caracteres
//     [A-Za-z0-9-]. Es la que ya manda el asistente de la interfaz (#708,
//     comun/cliente-chatbot.js): 'visitante-' + crypto.randomUUID(), o en un
//     contexto sin HTTPS su respaldo de tiempo y aleatorio en base 36. Se
//     acepta para no romperlo; queda registrada la primera vez que escribe.
//
// Ninguna de las dos se confunde con un uid (un UUID a secas), en la base
// solo queda su huella SHA-256 y la conversacion vive en el espacio de
// visitante ('anonimo:<id de la sesion>'): una sesion NUNCA abre la
// conversacion de un usuario registrado, que vive en la clave del uid.
//
// Validar es: una de las dos formas + huella registrada + no vencida.
// Cualquier otra cosa (un uid, un valor sin forma, una sesion vencida) se
// trata como si no hubiera sesion.
@Service
public class SesionesAnonimas {

    static final String PREFIJO = "anon_";
    static final Pattern FORMA = Pattern.compile("^anon_[A-Za-z0-9_-]{43}$");
    static final Pattern FORMA_DECLARADA = Pattern.compile("^visitante-[A-Za-z0-9-]{10,64}$");
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

    // Registra la sesion que declaro el navegador la primera vez que escribe
    // y la devuelve. Si ya esta registrada y vigente, es esa y no cuenta como
    // nueva. Si vencio, se aparta y empieza otra conversacion, igual que al
    // vencer una emitida. Una sesion nueva cuenta en los mismos limites que
    // emitir(): declarar una distinta para cada mensaje no esquiva el limite
    // de mensajes.
    @Transactional
    public SesionAnonima declarar(String identificador, String origen) {
        if (!esDeclarada(identificador)) {
            throw new IllegalArgumentException("El identificador no tiene la forma de una sesion declarada");
        }
        String huella = huella(identificador);
        Instant ahora = ahora();
        Optional<SesionAnonima> registrada = repositorio.findByHuella(huella);
        if (registrada.isPresent() && registrada.get().vigente(ahora)) {
            return registrada.get();
        }
        limitador.exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, origen);
        limitador.exigir(LimitadorDeFrecuencia.Regla.SESIONES_GLOBAL, "*");
        if (registrada.isPresent()) {
            repositorio.borrarVencida(huella, ahora);
        }
        // Sin carrera: dos primeros mensajes a la vez dejan una sola fila.
        repositorio.insertarSiNoExiste(UUID.randomUUID(), huella, ahora, ahora.plus(inactividadMaxima));
        return repositorio.findByHuella(huella)
            .orElseThrow(() -> new IllegalStateException("La sesion declarada no quedo registrada"));
    }

    // La sesion que corresponde al identificador, si tiene una de las dos
    // formas, esta registrada y sigue vigente. Vacio en cualquier otro caso.
    @Transactional(readOnly = true)
    public Optional<SesionAnonima> validar(String identificador) {
        if (!esEmitida(identificador) && !esDeclarada(identificador)) {
            return Optional.empty();
        }
        Instant ahora = ahora();
        return repositorio.findByHuella(huella(identificador)).filter(sesion -> sesion.vigente(ahora));
    }

    public static boolean esEmitida(String identificador) {
        return identificador != null && FORMA.matcher(identificador).matches();
    }

    public static boolean esDeclarada(String identificador) {
        return identificador != null && FORMA_DECLARADA.matcher(identificador).matches();
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

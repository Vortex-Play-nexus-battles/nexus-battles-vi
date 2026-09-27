package com.nexusbattles.plataforma.correo.envio;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Decide si un fallo de entrega merece otro intento.
 *
 * <p><b>Permanente</b> es lo que el tiempo no arregla:
 * <ul>
 *   <li>el servidor rechazo al destinatario con un 5xx (buzon inexistente,
 *       dominio que no acepta correo). Jakarta Mail los deja en
 *       {@link SendFailedException#getInvalidAddresses()}; los 4xx
 *       ("intentalo luego") van a {@code getValidUnsentAddresses()} y siguen
 *       siendo reintentables;</li>
 *   <li>una direccion que ni siquiera tiene forma de direccion
 *       ({@link AddressException});</li>
 *   <li>un mensaje que no se pudo componer ({@link MailParseException},
 *       {@link MailPreparationException}).</li>
 * </ul>
 *
 * <p>Todo lo demas es <b>transitorio</b>, a proposito tambien los 5xx que no
 * son del destinatario: una clave SMTP equivocada (535) o un remitente no
 * autorizado (553 en el MAIL FROM) son fallos de configuracion. Se arreglan
 * cambiando una variable de entorno, y cuando alguien lo haga, el correo tiene
 * que seguir en la cola para salir, no haberse dado por perdido.
 *
 * <p>Los fallos del SMTP no llegan como cadena de causas limpia: cuando el
 * servidor rechaza un mensaje, {@code JavaMailSenderImpl} lanza una
 * {@link MailSendException} SIN causa y con las excepciones de cada mensaje
 * en {@link MailSendException#getMessageExceptions()}. Por eso se recorren
 * las dos cosas.
 */
public final class ClasificadorDeFallos {

    /** Largo maximo del resumen: cabe en la columna ultimo_error y en una linea de bitacora. */
    static final int LARGO_MAXIMO = 300;

    /** Por si una cadena de excepciones apunta a si misma. */
    private static final int PROFUNDIDAD_MAXIMA = 32;

    private ClasificadorDeFallos() {}

    /** True si reintentar el envio no podria arreglarlo. */
    public static boolean esPermanente(Throwable error) {
        for (Throwable causa : cadena(error)) {
            if (causa instanceof AddressException
                    || causa instanceof MailParseException
                    || causa instanceof MailPreparationException) {
                return true;
            }
            if (causa instanceof SendFailedException rechazo
                    && rechazo.getInvalidAddresses() != null
                    && rechazo.getInvalidAddresses().length > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Una linea, sin trazas ni direcciones completas, para la bitacora y para
     * la columna {@code ultimo_error}.
     *
     * <p>Toma la causa mas profunda -la que dice que paso de verdad: "Connection
     * refused", "550 5.1.1 ... User unknown"- con el nombre de su clase delante.
     */
    public static String resumen(Throwable error) {
        if (error == null) {
            return "";
        }
        Throwable raiz = raiz(error);
        if (raiz instanceof MailSendException envio && envio.getMessageExceptions().length > 0) {
            raiz = raiz(envio.getMessageExceptions()[0]);
        }
        String mensaje = raiz.getMessage();
        String texto = raiz.getClass().getSimpleName() + (mensaje == null ? "" : ": " + mensaje);
        return sanear(texto);
    }

    /** Sin saltos de linea, sin direcciones completas y con largo acotado. */
    public static String sanear(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = Enmascarar.direccionesEn(texto.replaceAll("\\s+", " ").trim());
        return limpio.length() > LARGO_MAXIMO ? limpio.substring(0, LARGO_MAXIMO) : limpio;
    }

    private static Throwable raiz(Throwable error) {
        Throwable causa = error;
        int vueltas = 0;
        while (causa.getCause() != null && causa.getCause() != causa && vueltas++ < PROFUNDIDAD_MAXIMA) {
            causa = causa.getCause();
        }
        return causa;
    }

    /** El error, sus causas y las excepciones por mensaje, sin repetir ninguna. */
    private static Iterable<Throwable> cadena(Throwable error) {
        Set<Throwable> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pendientes = new ArrayDeque<>();
        if (error != null) {
            pendientes.add(error);
        }
        while (!pendientes.isEmpty() && vistas.size() < PROFUNDIDAD_MAXIMA) {
            Throwable actual = pendientes.poll();
            if (!vistas.add(actual)) {
                continue;
            }
            if (actual.getCause() != null) {
                pendientes.add(actual.getCause());
            }
            if (actual instanceof MessagingException mensajeria && mensajeria.getNextException() != null) {
                pendientes.add(mensajeria.getNextException());
            }
            if (actual instanceof MailSendException envio) {
                Collections.addAll(pendientes, envio.getMessageExceptions());
            }
        }
        return vistas;
    }
}

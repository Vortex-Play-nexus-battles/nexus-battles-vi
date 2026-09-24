package com.nexusbattles.plataforma.correo.envio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * A donde van los correos para direcciones reservadas ({@link DominiosReservados}).
 *
 * <p>Con el correo apuntando a un proveedor real esas direcciones no pueden
 * ir alli: el proveedor las aceptaria, no podria entregarlas y las devolveria
 * rebotadas. Tampoco pueden desaparecer sin mas, porque las pruebas de
 * extremo a extremo leen de un buzon de pruebas el codigo de recuperacion que
 * se les envia.
 *
 * <p>Por orden:
 * <ol>
 *   <li>Si hay buzon propio ({@code correo.buzon-de-pruebas.host}), van a el.
 *       En dev es el Mailpit del mismo compose.</li>
 *   <li>Si no, y el servidor principal ya es un buzon de pruebas (desarrollo
 *       local contra Mailpit), van al principal, como hasta ahora.</li>
 *   <li>Si no, no salen: el registro los anota como
 *       {@link EnvioRegistrado#OMITIDO}.</li>
 * </ol>
 */
@Component
public class BuzonDePruebas {

    /** Puertos de los recogedores de correo de desarrollo (Mailpit, MailHog). */
    private static final List<Integer> PUERTOS_DE_BUZON = List.of(1025, 1026, 8025);

    private static final String ESPERA_MS = "10000";

    private final Optional<JavaMailSender> propio;
    private final String descripcionPropio;
    private final boolean principalEsBuzon;
    private final String descripcionPrincipal;

    public BuzonDePruebas(
            @Value("${correo.buzon-de-pruebas.host:}") String host,
            @Value("${correo.buzon-de-pruebas.puerto:}") String puerto,
            @Value("${spring.mail.host:}") String hostPrincipal,
            @Value("${spring.mail.port:}") String puertoPrincipal) {
        String limpio = host == null ? "" : host.trim();
        int puertoPropio = puertoDe(puerto, 1025);
        int puertoDelPrincipal = puertoDe(puertoPrincipal, 0);
        if (limpio.isEmpty()) {
            this.propio = Optional.empty();
            this.descripcionPropio = "";
        } else {
            JavaMailSenderImpl impl = new JavaMailSenderImpl();
            impl.setHost(limpio);
            impl.setPort(puertoPropio);
            // Un buzon de pruebas no autentica ni cifra, y no hay motivo para
            // esperarle mas que al proveedor real.
            impl.getJavaMailProperties().put("mail.smtp.connectiontimeout", ESPERA_MS);
            impl.getJavaMailProperties().put("mail.smtp.timeout", ESPERA_MS);
            impl.getJavaMailProperties().put("mail.smtp.writetimeout", ESPERA_MS);
            this.propio = Optional.of(impl);
            this.descripcionPropio = limpio + ":" + puertoPropio;
        }
        this.principalEsBuzon = esPuertoDeBuzon(puertoDelPrincipal);
        this.descripcionPrincipal = (hostPrincipal == null ? "" : hostPrincipal.trim()) + ":" + puertoDelPrincipal;
    }

    /**
     * El puerto como numero, o {@code siFalta} si no hay uno valido.
     *
     * <p>Se lee como texto a proposito. Una variable vacia en el {@code .env}
     * ({@code SMTP_PORT=}, tal como viene en {@code .env.example}) llega como
     * cadena vacia, no como ausente, asi que el valor por omision del
     * marcador no se aplica; y una cadena vacia en un {@code int} de
     * {@code @Value} impide que el servicio arranque.
     */
    static int puertoDe(String texto, int siFalta) {
        if (texto == null || texto.isBlank()) {
            return siFalta;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return siFalta;
        }
    }

    /** True si el puerto es el de un recogedor de desarrollo, que no reenvia a nadie. */
    static boolean esPuertoDeBuzon(int puerto) {
        return PUERTOS_DE_BUZON.contains(puerto);
    }

    /**
     * Por donde sale un correo a una direccion reservada.
     *
     * @param principal el servidor por el que sale el resto del correo
     * @return el buzon de pruebas, o vacio si no hay ninguno y el correo no
     *         debe salir
     */
    public Optional<JavaMailSender> para(JavaMailSender principal) {
        if (propio.isPresent()) {
            return propio;
        }
        return principalEsBuzon ? Optional.of(principal) : Optional.empty();
    }

    /**
     * "host:puerto" del buzon al que se desvian las direcciones reservadas, o
     * vacio si no hay ninguno. Es lo que muestra la evidencia de entrega.
     */
    public String descripcion() {
        if (propio.isPresent()) {
            return descripcionPropio;
        }
        return principalEsBuzon ? descripcionPrincipal : "";
    }
}
package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.PlantillaCorreoService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class EnviadorCorreoService {

    private static final Logger BITACORA = LoggerFactory.getLogger(EnviadorCorreoService.class);

    /**
     * El logo viaja incrustado en el propio mensaje en vez de apuntar a una URL:
     * el proyecto aun no tiene alojamiento publico, y las imagenes en base64 las
     * bloquean Gmail y Outlook. Este identificador es el que la plantilla
     * referencia como {@code src="cid:logo-nexus"}.
     */
    static final String CID_LOGO = "logo-nexus";

    private static final Resource LOGO = new ClassPathResource("imagenes/logo-nexus.png");

    private final JavaMailSender mailSender;
    private final PlantillaCorreoService plantillaCorreoService;
    private final ConfiguracionDeCorreo configuracion;
    private final RegistroDeEnvios registro;
    private final Clock reloj;
    private final BuzonDePruebas buzonDePruebas;

    public EnviadorCorreoService(
            JavaMailSender mailSender,
            PlantillaCorreoService plantillaCorreoService,
            ConfiguracionDeCorreo configuracion,
            RegistroDeEnvios registro,
            Clock reloj,
            BuzonDePruebas buzonDePruebas) {
        this.mailSender = mailSender;
        this.plantillaCorreoService = plantillaCorreoService;
        this.configuracion = configuracion;
        this.registro = registro;
        this.reloj = reloj;
        this.buzonDePruebas = buzonDePruebas;
    }

    public void enviar(
            String destinatario, String asunto, String nombrePlantilla, Map<String, Object> variables) {
        Instant ahora = reloj.instant();

        // A donde va. Una direccion reservada para pruebas (RFC 2606) no
        // puede ir al proveedor real: la aceptaria, no podria entregarla y
        // la devolveria rebotada, gastando cuota y reputacion del remitente.
        JavaMailSender servidor = mailSender;
        String destino = EnvioRegistrado.PROVEEDOR;
        if (DominiosReservados.esReservado(destinatario)) {
            Optional<JavaMailSender> buzon = buzonDePruebas.para(mailSender);
            if (buzon.isEmpty()) {
                registro.anotar(EnvioRegistrado.omitido(ahora, destinatario, nombrePlantilla));
                BITACORA.info(
                        "Correo {} para {} omitido: dominio reservado y sin buzon de pruebas",
                        nombrePlantilla,
                        EnvioRegistrado.enmascarar(destinatario));
                return;
            }
            servidor = buzon.get();
            destino = EnvioRegistrado.BUZON_DE_PRUEBAS;
        }

        String html = plantillaCorreoService.renderizar(nombrePlantilla, variables);
        // El mensaje se crea con el mismo servidor que lo va a enviar: la
        // sesion de JavaMail va atada a el.
        MimeMessage mensaje = servidor.createMimeMessage();

        try {
            // `true` en el segundo argumento = multiparte: hace falta para el
            // logo incrustado Y para llevar las dos versiones del cuerpo.
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            if (configuracion.tieneRemitente()) {
                helper.setFrom(configuracion.remitente());
            }
            if (configuracion.tieneResponderA()) {
                helper.setReplyTo(configuracion.responderA());
            }
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            // Dos cuerpos, texto primero y HTML despues, que es el orden que
            // manda la RFC 2046 para multipart/alternative: el cliente muestra
            // la ultima parte que sabe representar. Un correo SOLO HTML es una
            // de las senales de correo no deseado que mas pesan, y ademas deja
            // sin nada a quien lee en texto plano.
            helper.setText(TextoPlanoDeCorreo.desdeHtml(html), html);
            // addInline va DESPUES de setText a proposito: MimeMessageHelper
            // exige ese orden para que la parte HTML quede antes que los
            // recursos incrustados. Al reves, algunos clientes no resuelven
            // el cid: y muestran el logo como adjunto suelto.
            helper.addInline(CID_LOGO, LOGO);
        } catch (MessagingException e) {
            registro.anotar(EnvioRegistrado.rechazado(
                    ahora, destinatario, nombrePlantilla, resumen(e), destino));
            throw new EnvioCorreoException("No se pudo construir el correo para " + destinatario, e);
        }

        try {
            servidor.send(mensaje);
        } catch (RuntimeException e) {
            // Lo que llega aqui es el rechazo del proveedor: autenticacion,
            // remitente no autorizado, TLS, limite de envio. Se anota antes de
            // relanzar, porque quien llama solo vera la excepcion y este es el
            // unico sitio donde se sabe a quien iba y por que fallo.
            registro.anotar(EnvioRegistrado.rechazado(
                    ahora, destinatario, nombrePlantilla, resumen(e), destino));
            BITACORA.warn(
                    "El servidor de correo rechazo el envio de {} a {}: {}",
                    nombrePlantilla,
                    EnvioRegistrado.enmascarar(destinatario),
                    resumen(e));
            throw e;
        }

        String identificador = identificadorDe(mensaje);
        registro.anotar(EnvioRegistrado.aceptado(
                ahora, destinatario, nombrePlantilla, identificador, destino));
        // Nivel INFO y con el destinatario enmascarado: esta linea es la que
        // permite responder "si, salio" sin tener que abrir la bandeja de
        // nadie, y sin dejar direcciones completas en la bitacora.
        BITACORA.info(
                "Correo {} aceptado por {} para {} (id {})",
                nombrePlantilla,
                destino,
                EnvioRegistrado.enmascarar(destinatario),
                identificador);
    }

    /**
     * El {@code Message-ID} que puso el servidor al enviar.
     *
     * <p>Es lo que permite cruzar este envio con los registros del proveedor.
     * Solo existe DESPUES de enviar: antes, el mensaje no tiene identidad.
     */
    private static String identificadorDe(MimeMessage mensaje) {
        try {
            String id = mensaje.getMessageID();
            return id == null ? "" : id;
        } catch (MessagingException e) {
            return "";
        }
    }

    /** Una linea, sin trazas ni credenciales, para la bitacora y el registro. */
    private static String resumen(Exception e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage();
        String texto = causa.getClass().getSimpleName() + (mensaje == null ? "" : ": " + mensaje);
        return texto.length() > 200 ? texto.substring(0, 200) : texto;
    }
}
package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.PlantillaCorreoService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Compone UN correo sobre la plantilla corporativa y se lo da al servidor SMTP.
 *
 * <p>Desde B1 no decide nada sobre reintentos ni guarda nada: devuelve un
 * {@link ResultadoDeEntrega} y la cola (TrabajadorDeEntrega) decide el estado
 * siguiente. Tampoco escribe en la bitacora: lo hace el trabajador, una linea
 * por intento con el identificador del envio, que es la que permite seguirlo.
 */
@Service
public class EnviadorCorreoService {

    /**
     * El logo viaja incrustado en el propio mensaje en vez de apuntar a una URL:
     * el proyecto aun no tiene alojamiento publico, y las imagenes en base64 las
     * bloquean Gmail y Outlook. Este identificador es el que la plantilla
     * referencia como {@code src="cid:logo-nexus"}.
     */
    static final String CID_LOGO = "logo-nexus";

    /** Motivo de un correo que no sale por ir a un dominio reservado. */
    static final String MOTIVO_DOMINIO_RESERVADO =
            "dominio reservado para pruebas (RFC 2606): no puede llegar a ninguna bandeja";

    private static final Resource LOGO = new ClassPathResource("imagenes/logo-nexus.png");

    private final JavaMailSender mailSender;
    private final PlantillaCorreoService plantillaCorreoService;
    private final ConfiguracionDeCorreo configuracion;
    private final BuzonDePruebas buzonDePruebas;

    public EnviadorCorreoService(
            JavaMailSender mailSender,
            PlantillaCorreoService plantillaCorreoService,
            ConfiguracionDeCorreo configuracion,
            BuzonDePruebas buzonDePruebas) {
        this.mailSender = mailSender;
        this.plantillaCorreoService = plantillaCorreoService;
        this.configuracion = configuracion;
        this.buzonDePruebas = buzonDePruebas;
    }

    /**
     * Un intento de entrega. Nunca lanza.
     *
     * @param rutaPlantilla plantilla Thymeleaf, p. ej. {@code email/bienvenida}
     */
    public ResultadoDeEntrega enviar(
            String destinatario, String asunto, String rutaPlantilla, Map<String, Object> variables) {

        // A donde va. Una direccion reservada para pruebas (RFC 2606) no
        // puede ir al proveedor real: la aceptaria, no podria entregarla y
        // la devolveria rebotada, gastando cuota y reputacion del remitente.
        JavaMailSender servidor = mailSender;
        DestinoDeEntrega destino = DestinoDeEntrega.PROVEEDOR;
        if (DominiosReservados.esReservado(destinatario)) {
            Optional<JavaMailSender> buzon = buzonDePruebas.para(mailSender);
            if (buzon.isEmpty()) {
                return ResultadoDeEntrega.omitido(MOTIVO_DOMINIO_RESERVADO);
            }
            servidor = buzon.get();
            destino = DestinoDeEntrega.BUZON_DE_PRUEBAS;
        }

        MimeMessage mensaje;
        try {
            mensaje = componer(servidor, destinatario, asunto, rutaPlantilla, variables);
        } catch (MessagingException | RuntimeException e) {
            // Un correo que no se puede componer (plantilla desconocida,
            // direccion sin forma de direccion) no se arregla esperando.
            return ResultadoDeEntrega.fallido(true, "no se pudo componer el correo: " + ClasificadorDeFallos.resumen(e));
        }

        try {
            servidor.send(mensaje);
        } catch (RuntimeException e) {
            // Lo que llega aqui es el rechazo o la ausencia del servidor:
            // autenticacion, remitente no autorizado, TLS, limite de envio,
            // destinatario inexistente, conexion rechazada.
            return ResultadoDeEntrega.fallido(e);
        }
        return ResultadoDeEntrega.entregado(destino, identificadorDe(mensaje));
    }

    private MimeMessage componer(
            JavaMailSender servidor,
            String destinatario,
            String asunto,
            String rutaPlantilla,
            Map<String, Object> variables) throws MessagingException {
        String html = plantillaCorreoService.renderizar(rutaPlantilla, variables);
        // El mensaje se crea con el mismo servidor que lo va a enviar: la
        // sesion de JavaMail va atada a el.
        MimeMessage mensaje = servidor.createMimeMessage();
        // `true` en el segundo argumento = multiparte: hace falta para el
        // logo incrustado Y para llevar las dos versiones del cuerpo.
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
        helper.setFrom(configuracion.remitente());
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
        return mensaje;
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
}

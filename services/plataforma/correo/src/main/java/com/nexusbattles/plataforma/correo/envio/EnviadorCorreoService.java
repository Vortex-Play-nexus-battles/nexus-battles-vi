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

@Service
public class EnviadorCorreoService {

    /**
     * El logo viaja incrustado en el propio mensaje en vez de apuntar a una URL:
     * el proyecto aún no tiene alojamiento público, y las imágenes en base64 las
     * bloquean Gmail y Outlook. Este identificador es el que la plantilla
     * referencia como {@code src="cid:logo-nexus"}.
     */
    static final String CID_LOGO = "logo-nexus";

    private static final Resource LOGO = new ClassPathResource("imagenes/logo-nexus.png");

    private final JavaMailSender mailSender;
    private final PlantillaCorreoService plantillaCorreoService;

    public EnviadorCorreoService(JavaMailSender mailSender, PlantillaCorreoService plantillaCorreoService) {
        this.mailSender = mailSender;
        this.plantillaCorreoService = plantillaCorreoService;
    }

    public void enviar(String destinatario, String asunto, String nombrePlantilla, Map<String, Object> variables) {
        String html = plantillaCorreoService.renderizar(nombrePlantilla, variables);
        MimeMessage mensaje = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            // addInline va DESPUÉS de setText a propósito: MimeMessageHelper
            // exige ese orden para que la parte HTML quede antes que los
            // recursos incrustados. Al revés, algunos clientes no resuelven
            // el cid: y muestran el logo como adjunto suelto.
            helper.addInline(CID_LOGO, LOGO);
        } catch (MessagingException e) {
            throw new EnvioCorreoException("No se pudo construir el correo para " + destinatario, e);
        }

        mailSender.send(mensaje);
    }
}

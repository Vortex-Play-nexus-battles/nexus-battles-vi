package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.PlantillaCorreoService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EnviadorCorreoService {

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
        } catch (MessagingException e) {
            throw new EnvioCorreoException("No se pudo construir el correo para " + destinatario, e);
        }

        mailSender.send(mensaje);
    }
}

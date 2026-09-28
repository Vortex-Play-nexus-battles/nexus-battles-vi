package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// B11: la unica transaccion de enviar un mensaje. Guarda juntos la pregunta y
// la respuesta ya generada; nada de HTTP aqui dentro (antes, las consultas a
// inventario, subastas y notificaciones corrian con la transaccion abierta y
// retenian una conexion de la base mientras esperaban).
@Component
public class RegistroDeConversaciones {

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final SesionesAnonimas sesiones;

    public RegistroDeConversaciones(ConversacionRepository conversacionRepository,
                                    MensajeRepository mensajeRepository, SesionesAnonimas sesiones) {
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.sesiones = sesiones;
    }

    @Transactional
    public Mensaje guardarIntercambio(IdentidadDelChat identidad, String contenido, String adjuntoUrl,
                                      Instant recibido, String textoRespuesta, ResultadoMotor resultado,
                                      int tiempoRespuestaMs) {
        Conversacion conversacion = obtenerOCrear(identidad);
        conversacion.registrarActividad();
        mensajeRepository.save(new Mensaje(conversacion, Remitente.USUARIO, contenido, adjuntoUrl, recibido));

        Mensaje respuestaBot = new Mensaje(conversacion, Remitente.BOT, textoRespuesta, null);
        respuestaBot.registrarDatosDeRespuesta(resultado.temaClave(), resultado.categoria(),
            resultado.requiereEscalamiento(), tiempoRespuestaMs);
        mensajeRepository.save(respuestaBot);

        if (identidad.sesion() != null) {
            sesiones.renovar(identidad.sesion());
        }
        return respuestaBot;
    }

    // La conversacion de esta identidad, o una nueva. Defensa en profundidad:
    // una clave de usuario nunca puede devolver la conversacion de un
    // visitante ni al reves (ademas de los espacios de claves separados y la
    // restriccion de la base, V5).
    private Conversacion obtenerOCrear(IdentidadDelChat identidad) {
        Conversacion conversacion = conversacionRepository.findByIdentificadorSesion(identidad.claveDeConversacion())
            .orElseGet(() -> conversacionRepository.save(
                new Conversacion(identidad.claveDeConversacion(), identidad.autenticado())));
        if (conversacion.isAutenticado() != identidad.autenticado()) {
            throw new IllegalStateException("La clave de la conversacion no corresponde al tipo de identidad");
        }
        return conversacion;
    }
}

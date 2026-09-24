package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final MotorRespuestas motorRespuestas;
    private final MotorConsultasAsistidas motorConsultasAsistidas;
    private final BrechaConocimientoService brechaConocimientoService;

    public ChatService(ConversacionRepository conversacionRepository, MensajeRepository mensajeRepository,
                       MotorRespuestas motorRespuestas, MotorConsultasAsistidas motorConsultasAsistidas,
                       BrechaConocimientoService brechaConocimientoService) {
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.motorRespuestas = motorRespuestas;
        this.motorConsultasAsistidas = motorConsultasAsistidas;
        this.brechaConocimientoService = brechaConocimientoService;
    }

    @Transactional
    public Mensaje enviarMensaje(String identificadorSesion, boolean autenticado, String contenido,
                                 String adjuntoUrl, String tokenBearer) {
        // HU-CHA-012: el "tiempo de respuesta" de las analiticas se mide desde
        // que llega la pregunta hasta que la respuesta esta lista para guardar.
        long inicio = System.nanoTime();

        Conversacion conversacion = obtenerOCrearConversacion(identificadorSesion, autenticado);
        conversacion.registrarActividad();
        Mensaje mensajeUsuario = new Mensaje(conversacion, Remitente.USUARIO, contenido, adjuntoUrl);
        mensajeRepository.save(mensajeUsuario);

        ResultadoMotor resultado = generarRespuesta(identificadorSesion, autenticado, contenido, tokenBearer);
        if (resultado.requiereEscalamiento()) {
            registrarPreguntaNoCubierta(contenido);
        }
        String textoRespuesta = construirTextoRespuesta(resultado);

        Mensaje respuestaBot = new Mensaje(conversacion, Remitente.BOT, textoRespuesta, null);
        respuestaBot.registrarDatosDeRespuesta(resultado.temaClave(), resultado.categoria(),
            resultado.requiereEscalamiento(), milisegundosDesde(inicio));
        mensajeRepository.save(respuestaBot);
        return respuestaBot;
    }

    // HU-CHA-008: si el usuario esta autenticado y trae token, se intenta
    // primero el motor de consultas asistidas (datos en vivo: inventario,
    // subastas, notificaciones). Si esa intencion no aplica (Optional
    // vacio), se sigue exactamente igual que en HU-CHA-004 con
    // MotorRespuestas. Un visitante nunca pasa por el primer motor.
    private ResultadoMotor generarRespuesta(String identificadorSesion, boolean autenticado, String contenido,
                                            String tokenBearer) {
        if (autenticado && tokenBearer != null) {
            return motorConsultasAsistidas.generarRespuesta(contenido, tokenBearer, identificadorSesion)
                .orElseGet(() -> motorRespuestas.generarRespuesta(contenido));
        }
        return motorRespuestas.generarRespuesta(contenido);
    }

    // HU-CHA-011: una pregunta que MotorRespuestas no entendio y escalo es,
    // literalmente, una "pregunta no cubierta": se agrupa como brecha de
    // conocimiento. Con esto el mensaje de escalamiento ("esta pregunta
    // quedo registrada para mejorar mis respuestas") deja de ser una promesa
    // sin respaldo. Si el registro falla, el chat responde igual: la
    // respuesta al usuario importa mas que el contador de brechas.
    private void registrarPreguntaNoCubierta(String contenido) {
        try {
            brechaConocimientoService.registrarEscalamiento(contenido);
        } catch (RuntimeException excepcion) {
            log.warn("No se pudo registrar la brecha de conocimiento de una pregunta escalada", excepcion);
        }
    }

    private String construirTextoRespuesta(ResultadoMotor resultado) {
        if (!resultado.requiereEscalamiento() || resultado.temasSugeridos().isEmpty()) {
            return resultado.texto();
        }
        return resultado.texto()
            + " Preguntas relacionadas que podrían ayudarte: "
            + String.join(" | ", resultado.temasSugeridos())
            + ".";
    }

    private static int milisegundosDesde(long inicioNanos) {
        long milisegundos = (System.nanoTime() - inicioNanos) / 1_000_000;
        return (int) Math.min(milisegundos, Integer.MAX_VALUE);
    }

    @Transactional(readOnly = true)
    public List<Mensaje> obtenerHistorial(String identificadorSesion) {
        return conversacionRepository.findByIdentificadorSesion(identificadorSesion)
            .map(c -> mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(c.getId()))
            .orElseGet(List::of);
    }

    @Transactional
    public void limpiarHistorial(String identificadorSesion) {
        conversacionRepository.findByIdentificadorSesion(identificadorSesion)
            .ifPresent(c -> mensajeRepository.deleteByConversacionId(c.getId()));
    }

    private Conversacion obtenerOCrearConversacion(String identificadorSesion, boolean autenticado) {
        return conversacionRepository.findByIdentificadorSesion(identificadorSesion)
            .orElseGet(() -> conversacionRepository.save(new Conversacion(identificadorSesion, autenticado)));
    }
}

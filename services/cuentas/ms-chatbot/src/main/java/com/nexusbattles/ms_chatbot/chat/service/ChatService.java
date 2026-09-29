package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasService;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-CHA-001/004/008/011 con el endurecimiento de B11 (ms-chatbot.yaml 1.2.0):
//
//   1. Limite de frecuencia por usuario o sesion (7.4.8), antes de nada.
//   2. El texto pasa por la lista negra (7.4.8) antes de procesarlo o
//      guardarlo: un mensaje bloqueado no deja rastro.
//   3. La respuesta se genera SIN transaccion abierta: las consultas en vivo
//      (inventario, subastas, notificaciones, finanzas, torneos) son HTTP con
//      tiempo de espera, y no retienen una conexion de la base mientras tanto.
//   4. Pregunta y respuesta se guardan juntas en una transaccion corta
//      (RegistroDeConversaciones).
//
// La identidad llega ya resuelta y verificada (ResolutorDeIdentidad): el
// servicio nunca decide quien es alguien a partir de una cabecera.
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final MotorRespuestas motorRespuestas;
    private final MotorConsultasAsistidas motorConsultasAsistidas;
    private final BrechaConocimientoService brechaConocimientoService;
    private final LimitadorDeFrecuencia limitador;
    private final ModeracionDeContenido moderacion;
    private final RegistroDeConversaciones registro;
    private final PreferenciasService preferenciasService;

    public ChatService(ConversacionRepository conversacionRepository, MensajeRepository mensajeRepository,
                       MotorRespuestas motorRespuestas, MotorConsultasAsistidas motorConsultasAsistidas,
                       BrechaConocimientoService brechaConocimientoService, LimitadorDeFrecuencia limitador,
                       ModeracionDeContenido moderacion, RegistroDeConversaciones registro,
                       PreferenciasService preferenciasService) {
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.motorRespuestas = motorRespuestas;
        this.motorConsultasAsistidas = motorConsultasAsistidas;
        this.brechaConocimientoService = brechaConocimientoService;
        this.limitador = limitador;
        this.moderacion = moderacion;
        this.registro = registro;
        this.preferenciasService = preferenciasService;
    }

    public Mensaje enviarMensaje(IdentidadDelChat identidad, String contenido, String adjuntoUrl) {
        return enviarMensaje(identidad, contenido, adjuntoUrl, null);
    }

    // ms-chatbot.yaml 1.3.4: `vista` es la seccion donde esta el jugador; la
    // usa MotorRespuestas para la respuesta contextual. Puede ser null.
    public Mensaje enviarMensaje(IdentidadDelChat identidad, String contenido, String adjuntoUrl,
                                 VistaDelChat vista) {
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, identidad.claveDeLimite());
        moderacion.verificar(contenido);

        // HU-CHA-012: el "tiempo de respuesta" de las analiticas se mide desde
        // que llega la pregunta hasta que la respuesta esta lista para guardar.
        Instant recibido = Instant.now();
        long inicio = System.nanoTime();

        ResultadoMotor resultado = generarRespuesta(identidad, contenido, vista);
        if (resultado.requiereEscalamiento()) {
            registrarPreguntaNoCubierta(contenido);
        }
        String textoRespuesta = construirTextoRespuesta(resultado);
        int tiempo = milisegundosDesde(inicio);
        try {
            return registro.guardarIntercambio(identidad, contenido, adjuntoUrl, recibido, textoRespuesta, resultado, tiempo);
        } catch (DataIntegrityViolationException carrera) {
            // Dos primeros mensajes simultaneos de la misma identidad crean la
            // conversacion a la vez y uno choca con el indice unico: se guarda
            // de nuevo en la conversacion que gano.
            return registro.guardarIntercambio(identidad, contenido, adjuntoUrl, recibido, textoRespuesta, resultado, tiempo);
        }
    }

    // HU-CHA-008: si el usuario esta autenticado y trae token, se intenta
    // primero el motor de consultas asistidas (datos en vivo: inventario,
    // subastas, notificaciones, movimientos de creditos, torneos). Si esa
    // intencion no aplica (Optional vacio), se sigue exactamente igual que en
    // HU-CHA-004 con MotorRespuestas. Un visitante nunca pasa por el primer
    // motor.
    private ResultadoMotor generarRespuesta(IdentidadDelChat identidad, String contenido, VistaDelChat vista) {
        if (identidad.autenticado() && identidad.tokenCrudo() != null) {
            return motorConsultasAsistidas.generarRespuesta(contenido, identidad.tokenCrudo(), identidad.uid())
                .orElseGet(() -> responderConLaBase(identidad, contenido, vista));
        }
        return responderConLaBase(identidad, contenido, vista);
    }

    // 1.3.6: con preferencias propias (idioma o nivel de detalle), la base
    // responde con ellas; con las de por defecto, exactamente como antes.
    private ResultadoMotor responderConLaBase(IdentidadDelChat identidad, String contenido, VistaDelChat vista) {
        PreferenciasDeRespuesta preferencias = preferenciasDe(identidad);
        if (!preferencias.sonPorDefecto()) {
            return motorRespuestas.generarRespuesta(contenido, vista, preferencias);
        }
        return vista == null
            ? motorRespuestas.generarRespuesta(contenido)
            : motorRespuestas.generarRespuesta(contenido, vista);
    }

    // Si no se pueden leer las preferencias, se responde con las de por
    // defecto: contestar importa mas que el formato de la respuesta.
    private PreferenciasDeRespuesta preferenciasDe(IdentidadDelChat identidad) {
        try {
            PreferenciasDeRespuesta guardadas = preferenciasService.de(identidad);
            return guardadas == null ? PreferenciasDeRespuesta.POR_DEFECTO : guardadas;
        } catch (RuntimeException excepcion) {
            log.warn("No se pudieron leer las preferencias del chat; se responde con las de por defecto", excepcion);
            return PreferenciasDeRespuesta.POR_DEFECTO;
        }
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
    public List<Mensaje> obtenerHistorial(IdentidadDelChat identidad) {
        return conversacionRepository.findByIdentificadorSesion(identidad.claveDeConversacion())
            .map(c -> mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(c.getId()))
            .orElseGet(List::of);
    }

    // ms-chatbot.yaml 1.3.5: una pagina del historial. Sin cursor, los
    // 'limite' mensajes mas recientes; con cursor, los 'limite' anteriores a
    // el. Siempre en orden cronologico, como el historial completo. Un cursor
    // de otra conversacion (o ya borrado) da lista vacia: no revela nada.
    @Transactional(readOnly = true)
    public List<Mensaje> obtenerHistorial(IdentidadDelChat identidad, UUID antesDe, int limite) {
        return conversacionRepository.findByIdentificadorSesion(identidad.claveDeConversacion())
            .map(c -> paginaDelHistorial(c.getId(), antesDe, limite))
            .orElseGet(List::of);
    }

    private List<Mensaje> paginaDelHistorial(UUID conversacionId, UUID antesDe, int limite) {
        PageRequest pagina = PageRequest.of(0, limite);
        List<Mensaje> recientesPrimero;
        if (antesDe == null) {
            recientesPrimero = mensajeRepository.findByConversacionIdOrderByFechaEnvioDescIdDesc(conversacionId, pagina);
        } else {
            Optional<Mensaje> cursor = mensajeRepository.findByIdAndConversacionId(antesDe, conversacionId);
            if (cursor.isEmpty()) {
                return List.of();
            }
            recientesPrimero = mensajeRepository.buscarAnteriores(conversacionId, cursor.get().getFechaEnvio(),
                antesDe, pagina);
        }
        List<Mensaje> cronologico = new ArrayList<>(recientesPrimero);
        Collections.reverse(cronologico);
        return cronologico;
    }

    @Transactional
    public void limpiarHistorial(IdentidadDelChat identidad) {
        conversacionRepository.findByIdentificadorSesion(identidad.claveDeConversacion())
            .ifPresent(c -> mensajeRepository.deleteByConversacionId(c.getId()));
    }
}

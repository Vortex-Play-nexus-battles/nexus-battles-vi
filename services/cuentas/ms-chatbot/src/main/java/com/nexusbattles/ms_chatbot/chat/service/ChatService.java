package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.catalogo.ConsultaDeCatalogo;
import com.nexusbattles.ms_chatbot.chat.consultas.ConsultaDeSubastas;
import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.consultas.NavegacionPublica;
import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.enriquecido.SolicitudAbierta;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.entrada.RevisionDeEntrada;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasService;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteService;
import com.nexusbattles.ms_chatbot.chat.texto.IdiomaDelTexto;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-CHA-001/004/008/011 con el endurecimiento de B11 (ms-chatbot.yaml 1.2.0)
// y el de la revision final del 7.4.8 (1.4.0):
//
//   1. Limite de frecuencia por usuario o sesion y por direccion de origen
//      (7.4.8), antes de nada.
//   2. RevisionDeEntrada: sin caracteres de control, sin intentos de
//      inyeccion de codigo (422) y con los datos sensibles tapados. De aqui
//      en adelante solo circula el texto redactado: la lista negra, los
//      motores, la brecha de conocimiento y la base nunca ven el original.
//   3. El texto redactado pasa por la lista negra (7.4.8) antes de procesarlo
//      o guardarlo: un mensaje bloqueado no deja rastro.
//   4. La respuesta se genera SIN transaccion abierta: las consultas en vivo
//      (inventario, subastas, notificaciones, finanzas, torneos) son HTTP con
//      tiempo de espera, y no retienen una conexion de la base mientras tanto.
//   5. Pregunta y respuesta se guardan juntas en una transaccion corta
//      (RegistroDeConversaciones).
//
// La identidad llega ya resuelta y verificada (ResolutorDeIdentidad): el
// servicio nunca decide quien es alguien a partir de una cabecera.
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // 1.4.0 (7.4.8): si la revision tapo algo, el jugador lo sabe, y sabe que
    // no se guardo. Va delante de la respuesta, que sigue siendo la de siempre.
    static final String AVISO_DATO_TAPADO = "Por tu seguridad tapé un dato sensible de tu mensaje "
        + "(una contraseña, una tarjeta o un token) y no lo guardé; nunca lo compartas por el chat. ";
    static final String AVISO_DATO_TAPADO_EN = "For your safety I hid a sensitive detail in your message "
        + "(a password, a card or a token) and didn't store it; never share it in the chat. ";

    // 1.5.0 — lo que se anade a una consulta escalada, en el idioma de la respuesta.
    static final String RELACIONADAS = " Preguntas relacionadas que podrían ayudarte: ";
    static final String RELACIONADAS_EN = " Related questions that might help: ";

    // 1.5.0 — «no me sirvio» (Seguimiento.NO_SIRVIO). Desde 1.12.0 la
    // solicitud de soporte se abre sola (RF-CHA-007) y se dice despues.
    static final String NO_SIRVIO = "Siento que mi respuesta no te haya servido. Puedes preguntarme de otra forma.";
    static final String NO_SIRVIO_EN = "I'm sorry my answer didn't help. You can ask me in another way.";

    // 1.12.0 (7.4.3, RF-CHA-007 «generar automaticamente un ticket de
    // soporte»): lo que se anade a una respuesta que no resolvio nada.
    static final String SOLICITUD_ABIERTA = " Abrí una solicitud de soporte con esta conversación: una persona del "
        + "equipo te responderá aquí, en «Soporte» › «Tus solicitudes».";
    static final String SOLICITUD_ABIERTA_EN = " I opened a support request with this conversation: a person from "
        + "the team will reply here, in «Soporte» › «Tus solicitudes» (Support › Your requests).";
    static final String SOLICITUD_ABIERTA_VISITANTE = " Abrí una solicitud de soporte con esta conversación: una "
        + "persona del equipo te responderá aquí, en «Soporte» › «Tus solicitudes», mientras sigas en esta sesión de "
        + "visitante.";
    static final String SOLICITUD_ABIERTA_VISITANTE_EN = " I opened a support request with this conversation: a "
        + "person from the team will reply here, in «Soporte» › «Tus solicitudes» (Support › Your requests), while "
        + "you stay in this visitor session.";
    static final String SOLICITUD_SUMADA = " Sumé esta pregunta a tu solicitud abierta en «Soporte»: soporte la verá "
        + "con el resto de la conversación.";
    static final String SOLICITUD_SUMADA_EN = " I added this question to your open request in «Soporte» (Support): "
        + "support will see it with the rest of the conversation.";
    // RF-CHA-007, excepciones: si no se puede crear, se dice y se ofrece otro canal.
    static final String SOLICITUD_FALLIDA = " No pude abrir la solicitud de soporte ahora: pídela en unos minutos "
        + "desde «Soporte» o busca tu duda en el centro de ayuda.";
    static final String SOLICITUD_FALLIDA_EN = " I couldn't open the support request right now: ask for it in a few "
        + "minutes from «Soporte» (Support) or look your question up in the help center.";

    // 1.5.0 — un seguimiento («mas detalle», «en ingles») cuando la respuesta
    // anterior no salio de un tema de la base (una consulta en vivo, un
    // escalamiento) o ese tema ya no esta en produccion.
    // 1.11.0 (7.4.4, 7.4.8): lo que se le dice a un visitante que pide datos
    // de su cuenta. No se consulta nada y no es una pregunta sin respuesta.
    static final String SIN_SESION = "Para eso necesito que inicies sesión: con tu sesión iniciada puedo consultar "
        + "en tiempo real tu inventario, tus misiones en curso, tus subastas y pujas, tus notificaciones y tus "
        + "movimientos de créditos. Sin sesión no puedo ver ningún dato personal.";
    static final String SIN_SESION_EN = "For that I need you to sign in: with your session I can check, in real "
        + "time, your inventory, your missions in progress, your auctions and bids, your notifications and your "
        + "credit movements. Without a session I can't see any personal data.";

    static final String SIN_TEMA_ANTERIOR = "Para eso necesito la pregunta completa: escríbemela otra vez y te "
        + "respondo como me pides.";
    static final String SIN_TEMA_ANTERIOR_EN = "For that I need the whole question: please write it again and "
        + "I'll answer the way you asked.";

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final MotorRespuestas motorRespuestas;
    private final MotorConsultasAsistidas motorConsultasAsistidas;
    private final ConsultaDeCatalogo consultaDeCatalogo;
    private final ConsultaDeSubastas consultaDeSubastas;
    private final NavegacionPublica navegacionPublica;
    private final BrechaConocimientoService brechaConocimientoService;
    private final LimitadorDeFrecuencia limitador;
    private final ModeracionDeContenido moderacion;
    private final RegistroDeConversaciones registro;
    private final PreferenciasService preferenciasService;
    private final RevisionDeEntrada revision;
    private final TicketSoporteService soporte;

    public ChatService(ConversacionRepository conversacionRepository, MensajeRepository mensajeRepository,
                       MotorRespuestas motorRespuestas, MotorConsultasAsistidas motorConsultasAsistidas,
                       BrechaConocimientoService brechaConocimientoService, LimitadorDeFrecuencia limitador,
                       ModeracionDeContenido moderacion, RegistroDeConversaciones registro,
                       PreferenciasService preferenciasService, RevisionDeEntrada revision,
                       ConsultaDeCatalogo consultaDeCatalogo, ConsultaDeSubastas consultaDeSubastas,
                       NavegacionPublica navegacionPublica, TicketSoporteService soporte) {
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.motorRespuestas = motorRespuestas;
        this.motorConsultasAsistidas = motorConsultasAsistidas;
        this.brechaConocimientoService = brechaConocimientoService;
        this.limitador = limitador;
        this.moderacion = moderacion;
        this.registro = registro;
        this.preferenciasService = preferenciasService;
        this.revision = revision;
        this.consultaDeCatalogo = consultaDeCatalogo;
        this.consultaDeSubastas = consultaDeSubastas;
        this.navegacionPublica = navegacionPublica;
        this.soporte = soporte;
    }

    public Mensaje enviarMensaje(IdentidadDelChat identidad, String contenido, String adjuntoUrl) {
        return enviarMensaje(identidad, contenido, adjuntoUrl, null);
    }

    // ms-chatbot.yaml 1.3.4: `vista` es la seccion donde esta el jugador; la
    // usa MotorRespuestas para la respuesta contextual. Puede ser null.
    public Mensaje enviarMensaje(IdentidadDelChat identidad, String contenido, String adjuntoUrl,
                                 VistaDelChat vista) {
        return enviarMensaje(identidad, contenido, adjuntoUrl, vista, null);
    }

    // 1.4.0: `origen` es la direccion del cliente (X-Real-IP del borde). Con
    // ella se limita tambien cuanto escribe una misma direccion, aunque reparta
    // sus mensajes entre muchas sesiones de visitante. Null en pruebas y en
    // llamadas internas.
    public Mensaje enviarMensaje(IdentidadDelChat identidad, String contenido, String adjuntoUrl,
                                 VistaDelChat vista, String origen) {
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, identidad.claveDeLimite());
        if (origen != null && !origen.isBlank()) {
            limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES_POR_ORIGEN, origen);
        }
        RevisionDeEntrada.Revisada revisada = revision.revisar(contenido);
        String pregunta = revisada.texto();
        if (pregunta == null || pregunta.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje esta vacio.");
        }
        moderacion.verificar(pregunta);

        // HU-CHA-012: el "tiempo de respuesta" de las analiticas se mide desde
        // que llega la pregunta hasta que la respuesta esta lista para guardar.
        Instant recibido = Instant.now();
        long inicio = System.nanoTime();

        Generada generada = generarRespuesta(identidad, pregunta, vista, origen);
        ResultadoMotor resultado = generada.resultado();
        // «no me sirvio» se cuenta como escalamiento (se ofrece soporte), pero
        // no es una pregunta sin cubrir: no va a las brechas de conocimiento.
        if (resultado.requiereEscalamiento() && !generada.deSeguimiento()) {
            registrarPreguntaNoCubierta(pregunta);
        }
        String textoRespuesta = construirTextoRespuesta(resultado);
        if (revisada.seTapoAlgo()) {
            textoRespuesta = (resultado.ingles() ? AVISO_DATO_TAPADO_EN : AVISO_DATO_TAPADO) + textoRespuesta;
        }
        int tiempo = milisegundosDesde(inicio);
        Mensaje guardada;
        try {
            guardada = registro.guardarIntercambio(identidad, pregunta, adjuntoUrl, recibido, textoRespuesta, resultado,
                tiempo);
        } catch (DataIntegrityViolationException carrera) {
            // Dos primeros mensajes simultaneos de la misma identidad crean la
            // conversacion a la vez y uno choca con el indice unico: se guarda
            // de nuevo en la conversacion que gano.
            guardada = registro.guardarIntercambio(identidad, pregunta, adjuntoUrl, recibido, textoRespuesta, resultado,
                tiempo);
        }
        return resultado.requiereEscalamiento() ? conSolicitud(identidad, vista, guardada, resultado.ingles()) : guardada;
    }

    // 1.12.0 (7.4.3, RF-CHA-007, segunda auditoria del 7.4): cuando no pudo
    // responder, el asistente abre la solicitud de soporte con la
    // conversacion, o suma la pregunta a la que ya estaba abierta, y lo dice
    // al final de su respuesta (con la solicitud en lo enriquecido, para que
    // la ventana enseñe un boton a ella). Va despues de guardar el
    // intercambio: la solicitud lleva esta pregunta en su contexto. Si no se
    // puede abrir, la respuesta lo dice y ofrece otro camino; nunca falla la
    // respuesta por eso.
    private Mensaje conSolicitud(IdentidadDelChat identidad, VistaDelChat vista, Mensaje respuesta, boolean ingles) {
        String anadido;
        RespuestaEnriquecida enriquecido = respuesta.getEnriquecido();
        try {
            Optional<TicketSoporteService.SolicitudAutomatica> solicitud = soporte.alNoResolver(identidad, vista);
            if (solicitud.isEmpty()) {
                return respuesta;
            }
            TicketSoporte ticket = solicitud.get().ticket();
            boolean nueva = solicitud.get().nueva();
            anadido = nueva
                ? textoDeSolicitudAbierta(identidad.autenticado(), ingles)
                : (ingles ? SOLICITUD_SUMADA_EN : SOLICITUD_SUMADA);
            SolicitudAbierta abierta = new SolicitudAbierta(ticket.getId(), ticket.getAsunto(),
                ticket.getEstado().name(), nueva);
            enriquecido = (enriquecido == null
                ? new RespuestaEnriquecida(List.of(), List.of(), List.of(), List.of(), true)
                : enriquecido).conSolicitud(abierta);
        } catch (RuntimeException noSePudo) {
            log.warn("No se pudo abrir la solicitud de soporte de una pregunta sin respuesta", noSePudo);
            anadido = ingles ? SOLICITUD_FALLIDA_EN : SOLICITUD_FALLIDA;
        }
        return registro.completarRespuesta(respuesta, respuesta.getContenido() + anadido, enriquecido);
    }

    private static String textoDeSolicitudAbierta(boolean jugador, boolean ingles) {
        if (jugador) {
            return ingles ? SOLICITUD_ABIERTA_EN : SOLICITUD_ABIERTA;
        }
        return ingles ? SOLICITUD_ABIERTA_VISITANTE_EN : SOLICITUD_ABIERTA_VISITANTE;
    }

    // Lo que respondio el bot y si fue un seguimiento de la respuesta anterior.
    private record Generada(ResultadoMotor resultado, boolean deSeguimiento) {
    }

    // HU-CHA-008: si el usuario esta autenticado y trae token, se intenta
    // primero el motor de consultas asistidas (datos en vivo: inventario,
    // subastas, notificaciones, movimientos de creditos, torneos). Si esa
    // intencion no aplica (Optional vacio), se sigue exactamente igual que en
    // HU-CHA-004 con MotorRespuestas. Un visitante nunca pasa por el primer
    // motor.
    //
    // 1.5.0: antes que nada, un seguimiento de la respuesta anterior («mas
    // detalle», «en ingles», «no me sirvio»), para visitantes y jugadores. Las
    // preferencias se leen una sola vez por mensaje.
    // 1.18.0: `origen` va al asiento de auditoria de cada consulta a sus datos.
    private Generada generarRespuesta(IdentidadDelChat identidad, String contenido, VistaDelChat vista,
                                      String origen) {
        PreferenciasDeRespuesta preferencias = preferenciasDe(identidad);
        Optional<ResultadoMotor> seguimiento = Seguimiento.de(contenido)
            .flatMap(tipo -> responderSeguimiento(identidad, contenido, tipo, preferencias));
        if (seguimiento.isPresent()) {
            return new Generada(seguimiento.get(), true);
        }
        boolean conSesion = identidad.autenticado() && identidad.tokenCrudo() != null;
        if (conSesion) {
            Optional<ResultadoMotor> enVivo = motorConsultasAsistidas.generarRespuesta(contenido,
                identidad.tokenCrudo(), identidad.uid(), preferencias.idioma(), origen);
            if (enVivo.isPresent()) {
                return new Generada(enVivo.get(), false);
            }
        } else if (motorConsultasAsistidas.pideDatosDeSuCuenta(contenido)) {
            // 1.11.0: un visitante que pide algo de su cuenta. Hasta aqui caia
            // en la base, que no tiene ese tema, y acababa en «no te entendi»
            // y como pregunta sin respuesta.
            return new Generada(sinSesion(contenido, preferencias), false);
        }
        // 1.11.0: «llevame a la tienda», «ir al centro de ayuda»... para todos.
        Optional<ResultadoMotor> navegacion = navegacionPublica.responder(contenido, conSesion, preferencias.idioma());
        if (navegacion.isPresent()) {
            return new Generada(navegacion.get(), false);
        }
        // 1.16.0 (7.4.4): lo que hay ahora en la subasta (dato publico), para
        // visitantes y jugadores. Lo suyo ya lo respondio el motor de arriba.
        Optional<ResultadoMotor> subasta = consultaDeSubastas.responder(contenido, preferencias.idioma());
        if (subasta.isPresent()) {
            return new Generada(subasta.get(), false);
        }
        // 1.10.0: el catalogo de hoy (dato publico), para visitantes y jugadores.
        Optional<ResultadoMotor> catalogo = consultaDeCatalogo.responder(contenido, preferencias.idioma());
        if (catalogo.isPresent()) {
            return new Generada(catalogo.get(), false);
        }
        return new Generada(responderConLaBase(contenido, vista, preferencias), false);
    }

    // 1.11.0: hace falta una sesion; con el enlace para entrar y el de crear
    // cuenta. Resuelve (TEMA): es la respuesta correcta para un visitante.
    private static ResultadoMotor sinSesion(String contenido, PreferenciasDeRespuesta preferencias) {
        boolean ingles = switch (preferencias.idioma()) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> IdiomaDelTexto.esIngles(NormalizadorTexto.normalizar(contenido));
        };
        RespuestaEnriquecida enlaces = new RespuestaEnriquecida(List.of(),
            List.of(Enriquecedor.enlaceA("login", ingles), Enriquecedor.enlaceA("registro", ingles)),
            List.of(), List.of(), false);
        return new ResultadoMotor(ingles ? SIN_SESION_EN : SIN_SESION, Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA,
            false, List.of(), null, enlaces, ingles, ResultadoDeRespuesta.TEMA);
    }

    // La ultima respuesta del bot en esta conversacion, otra vez, con lo que
    // pide el seguimiento. Sin respuesta anterior (primer mensaje, o tras
    // limpiar el historial) no hay seguimiento y la frase sigue el camino
    // normal. Si la anterior no salio de un tema de la base, se pide la
    // pregunta completa; si no le sirvio, se le ofrece soporte igual.
    private Optional<ResultadoMotor> responderSeguimiento(IdentidadDelChat identidad, String contenido,
                                                          Seguimiento tipo, PreferenciasDeRespuesta preferencias) {
        Optional<Mensaje> anterior = mensajeRepository
            .findFirstByConversacionIdentificadorSesionAndRemitenteOrderByFechaEnvioDesc(
                identidad.claveDeConversacion(), Remitente.BOT);
        if (anterior.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> temaAnterior = anterior.map(Mensaje::getTemaClave);
        boolean preguntaEnIngles = IdiomaDelTexto.esIngles(NormalizadorTexto.normalizar(contenido));
        boolean ingles = switch (tipo) {
            case EN_INGLES -> true;
            case EN_ESPANOL -> false;
            default -> switch (preferencias.idioma()) {
                case ES -> false;
                case EN -> true;
                case AUTOMATICO -> preguntaEnIngles;
            };
        };
        if (tipo == Seguimiento.NO_SIRVIO) {
            String texto = ingles ? NO_SIRVIO_EN : NO_SIRVIO;
            // 1.7.0: se ofrece soporte humano y no cuenta como resuelta.
            return Optional.of(temaAnterior.flatMap(clave -> motorRespuestas.relacionadosCon(clave, texto, ingles))
                .orElseGet(() -> new ResultadoMotor(texto, null, TipoRespuesta.DIRECTA, true, List.of(), null,
                    Enriquecedor.soloSoporte(ingles), ingles))
                .conResultado(ResultadoDeRespuesta.NO_SIRVIO));
        }
        PreferenciasDeRespuesta pedidas = switch (tipo) {
            case MAS_DETALLE -> new PreferenciasDeRespuesta(preferencias.idioma(), NivelDeDetalle.DETALLADO);
            case EN_INGLES -> new PreferenciasDeRespuesta(IdiomaPreferido.EN, preferencias.nivelDetalle());
            case EN_ESPANOL -> new PreferenciasDeRespuesta(IdiomaPreferido.ES, preferencias.nivelDetalle());
            default -> preferencias;
        };
        // 1.7.0: «escribe la pregunta completa» no resuelve nada (PIDE_REFORMULAR).
        return Optional.of(temaAnterior.flatMap(clave -> motorRespuestas.responderTema(clave, pedidas, preguntaEnIngles))
            .orElseGet(() -> ResultadoMotor.deTema(ingles ? SIN_TEMA_ANTERIOR_EN : SIN_TEMA_ANTERIOR, null,
                TipoRespuesta.DIRECTA).enIngles(ingles).conResultado(ResultadoDeRespuesta.PIDE_REFORMULAR)));
    }

    // 1.3.6: con preferencias propias (idioma o nivel de detalle), la base
    // responde con ellas; con las de por defecto, exactamente como antes.
    private ResultadoMotor responderConLaBase(String contenido, VistaDelChat vista,
                                              PreferenciasDeRespuesta preferencias) {
        if (!preferencias.sonPorDefecto()) {
            return motorRespuestas.generarRespuesta(contenido, vista, preferencias);
        }
        return vista == null
            ? motorRespuestas.generarRespuesta(contenido)
            : motorRespuestas.generarRespuesta(contenido, vista);
    }

    // Si no se pueden leer las preferencias, se responde con las de por
    // defecto: contestar importa mas que el formato de la respuesta.
    // 1.15.0: las efectivas, con el nivel automatico ya traducido al de su
    // experiencia (D-CHA-13): el motor nunca recibe AUTOMATICO.
    private PreferenciasDeRespuesta preferenciasDe(IdentidadDelChat identidad) {
        try {
            PreferenciasDeRespuesta guardadas = preferenciasService.efectivasDe(identidad);
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
            + (resultado.ingles() ? RELACIONADAS_EN : RELACIONADAS)
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

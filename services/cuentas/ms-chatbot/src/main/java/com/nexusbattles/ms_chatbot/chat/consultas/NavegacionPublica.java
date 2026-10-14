package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.texto.DistanciaDeEdicion;
import com.nexusbattles.ms_chatbot.chat.texto.IdiomaDelTexto;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 1.11.0 (7.4.4 «navegación asistida: guiar al usuario a secciones específicas
 * del sitio»): «llévame a la tienda», «ir al centro de ayuda», «take me to
 * the auctions»… para cualquiera, también un visitante.
 *
 * <p>Hasta aquí la navegación solo existía para un jugador con sesión y solo
 * hacia sus secciones privadas (Mi inventario, Mis subastas, notificaciones,
 * perfil, misiones y torneos, en {@link MotorConsultasAsistidas}). Un
 * visitante que pedía ir a la tienda o al centro de ayuda recibía un tema
 * general sin enlace. Aquí van las secciones del menú que no son datos de
 * nadie: la tienda, las subastas y los torneos (sus listados), el centro de
 * ayuda, el inicio, el chat general, «Jugar online», el registro y la
 * entrada.
 *
 * <p>Los destinos son vistas de la matriz de acceso del frontend; las que
 * piden sesión, a un visitante se le dice que entre (con el enlace a
 * «Entrar»), no se le da un enlace que la ventana no le pintaría. Nada de esto
 * consulta otro servicio.
 */
@Service
public class NavegacionPublica {

    private record Seccion(String destino, boolean pideSesion, String nombreEs, String nombreEn,
                           List<String> frases) {
    }

    // Lo que se dice antes del sitio: «llevame a», «ir a», «abre»...
    private static final List<String> VERBOS = List.of(
        "llevame a", "llevame al", "llevarme a", "llevarme al", "ir a", "ir al", "quiero ir a", "quiero ir al",
        "abre", "abrir", "abreme", "vamos a", "vamos al", "mandame a", "mandame al", "volver a", "volver al",
        "regresar a", "regresar al", "dirigeme a", "dirigeme al",
        "take me to", "go to", "open", "bring me to", "back to", "i want to go to");

    private static final List<Seccion> SECCIONES = List.of(
        new Seccion("tienda", true, "la «Tienda»", "the «Tienda» (Store)",
            List.of("la tienda", "tienda", "the store", "the shop", "store", "shop")),
        new Seccion("subastas", false, "la «Subasta»", "the «Subasta» (Auction)",
            List.of("la subasta", "las subastas", "subastas", "the auction", "the auctions", "auctions")),
        new Seccion("torneos", false, "el «Torneo»", "the «Torneo» (Tournament)",
            List.of("los torneos", "el torneo", "torneos", "the tournaments", "the tournament", "tournaments")),
        new Seccion(Enriquecedor.DESTINO_AYUDA, false, "el centro de ayuda", "the help center",
            List.of("el centro de ayuda", "centro de ayuda", "la ayuda", "ayuda", "the help center", "help center",
                "help")),
        new Seccion("chat", true, "el «Chat» general", "the general «Chat»",
            List.of("el chat general", "chat general", "el chat", "the general chat", "the chat")),
        new Seccion("batallas", true, "«Jugar online»", "«Jugar online» (Play online)",
            List.of("jugar online", "jugar", "las batallas", "batallas", "play online", "play", "battles")),
        new Seccion("registro", false, "«Crear cuenta»", "«Crear cuenta» (Create account)",
            List.of("crear una cuenta", "crear cuenta", "el registro", "registrarme", "registro", "sign up",
                "create an account", "register")),
        new Seccion("login", false, "la entrada", "the sign-in page",
            List.of("iniciar sesion", "el inicio de sesion", "inicio de sesion", "el login", "login", "entrar",
                "sign in", "log in", "the login")));

    // El inicio es distinto con o sin sesion: la portada publica o el inicio
    // del jugador. Va aparte porque su destino depende de quien pregunta.
    private static final List<String> INICIO = List.of(
        "el inicio", "inicio", "la portada", "portada", "la pagina principal", "pagina principal",
        "the home page", "home page", "home", "the start page");

    /**
     * La sección a la que pide ir, con su enlace; vacío si no pide ir a
     * ninguna de estas.
     *
     * @param mensajeUsuario lo que escribió
     * @param conSesion      si quien pregunta tiene sesión de jugador
     * @param idioma         su preferencia (AUTOMÁTICO: el de la pregunta)
     */
    public Optional<ResultadoMotor> responder(String mensajeUsuario, boolean conSesion, IdiomaPreferido idioma) {
        if (mensajeUsuario == null || mensajeUsuario.isBlank()) {
            return Optional.empty();
        }
        String mensaje = NormalizadorTexto.normalizar(mensajeUsuario);
        boolean ingles = switch (idioma == null ? IdiomaPreferido.AUTOMATICO : idioma) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> IdiomaDelTexto.esIngles(mensaje);
        };
        if (pideIrA(mensaje, INICIO)) {
            return Optional.of(irA(conSesion ? "home" : "portada", ingles ? "the home page" : "el inicio", ingles));
        }
        for (Seccion seccion : SECCIONES) {
            if (pideIrA(mensaje, seccion.frases())) {
                String nombre = ingles ? seccion.nombreEn() : seccion.nombreEs();
                if (seccion.pideSesion() && !conSesion) {
                    return Optional.of(necesitaSesion(nombre, ingles));
                }
                return Optional.of(irA(seccion.destino(), nombre, ingles));
            }
        }
        return Optional.empty();
    }

    // El mensaje entero (sin lo que se dice por cortesia) es un verbo de ir
    // seguido del sitio: «llevame a la tienda», «go to the shop», con un error
    // de tecleo por palabra como mucho. «¿Qué se compra en la tienda?» no lo
    // es: eso lo responde la base.
    private static boolean pideIrA(String mensaje, List<String> sitios) {
        String limpio = sinCortesia(mensaje);
        for (String sitio : sitios) {
            for (String verbo : VERBOS) {
                if (esLaFrase(limpio, verbo + " " + sitio)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean esLaFrase(String mensaje, String frase) {
        String[] escritas = mensaje.split(" ");
        String[] esperadas = frase.split(" ");
        if (escritas.length != esperadas.length) {
            return false;
        }
        for (int i = 0; i < esperadas.length; i++) {
            if (!DistanciaDeEdicion.coinciden(esperadas[i], escritas[i])) {
                return false;
            }
        }
        return true;
    }

    private static final List<String> CORTESIA_AL_PRINCIPIO = List.of(
        "por favor ", "porfa ", "please ", "puedes ", "podrias ", "can you ", "could you ", "quisiera ", "me ");
    private static final List<String> CORTESIA_AL_FINAL = List.of(
        " por favor", " porfa", " please", " ahora", " now");

    private static String sinCortesia(String mensaje) {
        String limpio = mensaje.strip();
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (String inicio : CORTESIA_AL_PRINCIPIO) {
                if (limpio.startsWith(inicio)) {
                    limpio = limpio.substring(inicio.length()).strip();
                    cambio = true;
                }
            }
            for (String fin : CORTESIA_AL_FINAL) {
                if (limpio.endsWith(fin)) {
                    limpio = limpio.substring(0, limpio.length() - fin.length()).strip();
                    cambio = true;
                }
            }
        }
        return limpio;
    }

    private static ResultadoMotor irA(String destino, String nombre, boolean ingles) {
        String texto = ingles ? "Here you go: " + nombre + "." : "Aquí tienes " + nombre + ".";
        return ResultadoMotor.deTema(texto, null, TipoRespuesta.DIRECTA)
            .conEnriquecido(Enriquecedor.conEnlace(destino, ingles))
            .enIngles(ingles)
            .conResultado(ResultadoDeRespuesta.NAVEGACION);
    }

    private static ResultadoMotor necesitaSesion(String nombre, boolean ingles) {
        String texto = ingles
            ? "To open " + nombre + " you need to sign in. If you don't have an account yet, you can create one."
            : "Para abrir " + nombre + " necesitas iniciar sesión. Si todavía no tienes cuenta, puedes crear una.";
        RespuestaEnriquecida enlaces = new RespuestaEnriquecida(List.of(),
            List.of(Enriquecedor.enlaceA("login", ingles), Enriquecedor.enlaceA("registro", ingles)),
            List.of(), List.of(), false);
        return ResultadoMotor.deTema(texto, null, TipoRespuesta.DIRECTA)
            .conEnriquecido(enlaces)
            .enIngles(ingles)
            .conResultado(ResultadoDeRespuesta.NAVEGACION);
    }
}

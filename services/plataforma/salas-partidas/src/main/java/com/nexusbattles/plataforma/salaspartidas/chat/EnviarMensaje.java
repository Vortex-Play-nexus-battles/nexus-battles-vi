package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido.Veredicto;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;

import java.time.Clock;
import java.util.UUID;

/**
 * Caso de uso del chat (HU-JUE-015). Clase de Java corriente, cableada en
 * ConfiguracionDelChat, como los casos de uso de salas.
 *
 * <p>El orden de las comprobaciones es el de la historia: primero la persona
 * (CA-03, sancion de silencio), luego el contenido (CA-03, lista negra), y
 * solo lo que pasa las dos queda en el historial y sale al canal (CA-01).
 * Nada se entrega sin verificar: si el filtro no contesta, el mensaje se
 * bloquea y se le dice al autor que reintente, que es la postcondicion de
 * RF-COM-007 aplicada al chat.
 *
 * <p>El contenido es todo lo que se publica: el texto y, si se comparte un
 * logro (CA-02), su mision y su titulo. Cada uno se verifica por separado,
 * porque unidos en un solo texto el final de uno y el principio del otro
 * podrian formar un termino que ninguno contiene (HU-COM-007).
 *
 * <p>Auditoria de DEV del 30-sep: antes de todo eso, el texto se depura y se
 * mira que sea un mensaje y no un dibujo de simbolos o una inundacion
 * ({@link PoliticaDeTexto}), y el autor no puede escribir mas deprisa de lo
 * que admite {@link LimiteDeEnvios} (429). Las dos van antes que las consultas
 * a otros servicios: rechazar lo evidente no debe costar una llamada de red.
 */
public class EnviarMensaje {

    public static final int LARGO_MAXIMO = 500;

    private final HistorialDeChat historial;
    private final FiltroDeContenido filtro;
    private final SancionesDelJugador sanciones;
    private final PublicadorDeChat publicador;
    private final Clock reloj;
    private final PoliticaDeTexto.Limites limites;
    private final LimiteDeEnvios limite;

    /** Sin limite de frecuencia y con los limites de texto por omision: lo usan las pruebas. */
    public EnviarMensaje(HistorialDeChat historial, FiltroDeContenido filtro,
            SancionesDelJugador sanciones, PublicadorDeChat publicador, Clock reloj) {
        this(historial, filtro, sanciones, publicador, reloj,
                PoliticaDeTexto.Limites.POR_OMISION, LimiteDeEnvios.SIN_LIMITE);
    }

    public EnviarMensaje(HistorialDeChat historial, FiltroDeContenido filtro,
            SancionesDelJugador sanciones, PublicadorDeChat publicador, Clock reloj,
            PoliticaDeTexto.Limites limites, LimiteDeEnvios limite) {
        this.historial = historial;
        this.filtro = filtro;
        this.sanciones = sanciones;
        this.publicador = publicador;
        this.reloj = reloj;
        this.limites = limites;
        this.limite = limite;
    }

    public MensajeDeChat enviar(Canal canal, Autor autor, String texto, LogroCompartido logro) {
        String limpio = validar(texto, limites);
        limite.registrar(autor.id()).ifPresent(espera -> {
            throw new DemasiadosMensajes(espera);
        });
        if (sanciones.tieneSancionActiva(autor.id())) {
            throw new JugadorSilenciado();
        }
        verificarContenido(limpio, canal);
        if (logro != null) {
            verificarContenido(logro.mision(), canal);
            verificarContenido(logro.titulo(), canal);
        }
        MensajeDeChat mensaje = new MensajeDeChat(UUID.randomUUID(), canal, autor,
                logro == null ? Tipo.MENSAJE : Tipo.LOGRO, limpio, logro, reloj.instant());
        historial.guardar(mensaje);
        publicador.publicar(mensaje);
        return mensaje;
    }

    /**
     * Un texto que se va a publicar. Solo LIMPIO pasa: SENALADO se bloquea, y
     * cualquier otra respuesta —SIN_VERIFICAR, o ninguna— no se da por limpia.
     * Un campo vacio del logro no tiene nada que verificar.
     */
    private void verificarContenido(String contenido, Canal canal) {
        if (contenido == null || contenido.isBlank()) {
            return;
        }
        Veredicto veredicto = filtro.verificar(contenido, canal);
        if (veredicto == Veredicto.LIMPIO) {
            return;
        }
        if (veredicto == Veredicto.SENALADO) {
            throw new ContenidoBloqueado();
        }
        throw new FiltroNoDisponible();
    }

    private static String validar(String texto, PoliticaDeTexto.Limites limites) {
        String limpio = PoliticaDeTexto.depurar(texto);
        if (limpio.isEmpty()) {
            throw new MensajeInvalido("El mensaje no puede estar vacío.");
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new MensajeInvalido("El mensaje no puede superar " + LARGO_MAXIMO + " caracteres.");
        }
        PoliticaDeTexto.problema(limpio, limites).ifPresent(explicacion -> {
            throw new MensajeInvalido(explicacion);
        });
        return limpio;
    }
}

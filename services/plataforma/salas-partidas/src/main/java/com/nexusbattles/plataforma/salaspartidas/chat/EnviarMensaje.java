package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido.Veredicto;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;

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
 */
public class EnviarMensaje {

    public static final int LARGO_MAXIMO = 500;

    private final HistorialDeChat historial;
    private final FiltroDeContenido filtro;
    private final SancionesDelJugador sanciones;
    private final PublicadorDeChat publicador;
    private final Clock reloj;

    public EnviarMensaje(HistorialDeChat historial, FiltroDeContenido filtro,
            SancionesDelJugador sanciones, PublicadorDeChat publicador, Clock reloj) {
        this.historial = historial;
        this.filtro = filtro;
        this.sanciones = sanciones;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    public MensajeDeChat enviar(Canal canal, Autor autor, String texto, LogroCompartido logro) {
        String limpio = validar(texto);
        if (sanciones.estaSilenciado(autor.id())) {
            throw new JugadorSilenciado();
        }
        Veredicto veredicto = filtro.verificar(limpio);
        if (veredicto == Veredicto.SENALADO) {
            throw new ContenidoBloqueado();
        }
        if (veredicto == Veredicto.SIN_VERIFICAR) {
            throw new FiltroNoDisponible();
        }
        MensajeDeChat mensaje = new MensajeDeChat(UUID.randomUUID(), canal, autor,
                logro == null ? Tipo.MENSAJE : Tipo.LOGRO, limpio, logro, reloj.instant());
        historial.guardar(mensaje);
        publicador.publicar(mensaje);
        return mensaje;
    }

    private static String validar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new MensajeInvalido("El mensaje no puede estar vacio.");
        }
        String limpio = texto.strip();
        if (limpio.length() > LARGO_MAXIMO) {
            throw new MensajeInvalido("El mensaje no puede superar " + LARGO_MAXIMO + " caracteres.");
        }
        return limpio;
    }
}

package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.PoliticaDeTexto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores.CuentaDeJugador;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores.DirectorioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.RepositorioDeMensajesDirectos.Guardado;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesNoDisponibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Enviar un mensaje privado — B6 (FEEDBACK DEL PROFESOR, no requisito del
 * documento: el 7.6 solo pide chat en las salas y en la vista general).
 *
 * <p><b>Una sola clase para las dos vias.</b> El {@code @MessageMapping} de
 * STOMP y el {@code POST} de respaldo por REST llaman a este mismo metodo: las
 * reglas no pueden divergir porque no estan escritas dos veces.
 *
 * <p><b>Las reglas del chat, mas las de un mensaje entre dos.</b> Se
 * reutilizan las de {@link EnviarMensaje} (HU-JUE-015) —mismo largo maximo,
 * mismo recorte, la misma puerta de sancion y la misma lista negra, ambas con
 * fallo cerrado— y se anaden las propias de escribirle a una persona:
 *
 * <ol>
 *   <li>el texto tiene de 1 a 500 caracteres, recortado
 *       ({@link MotivoDeRechazo#TEXTO_INVALIDO});</li>
 *   <li>el destinatario no es uno mismo ({@link MotivoDeRechazo#DESTINATARIO_PROPIO});</li>
 *   <li>un reintento con el mismo {@code idCliente} devuelve el mensaje ya
 *       guardado y no cuenta como otro envio (ni pasa otra vez por
 *       moderacion);</li>
 *   <li>el remitente no va demasiado rapido ({@link MotivoDeRechazo#DEMASIADO_RAPIDO});
 *       el limite va antes que las consultas a otros servicios para que
 *       insistir no sirva para martillearlos;</li>
 *   <li>el remitente no tiene una sancion activa ({@link MotivoDeRechazo#SANCIONADO});</li>
 *   <li>el destinatario existe y su cuenta esta activa
 *       ({@link MotivoDeRechazo#DESTINATARIO_INEXISTENTE});</li>
 *   <li>el texto pasa la lista negra con contexto {@code MENSAJE_PRIVADO}
 *       ({@link MotivoDeRechazo#TEXTO_NO_PERMITIDO}).</li>
 * </ol>
 *
 * <p>Si la sancion, el directorio o la lista negra no responden, el mensaje
 * NO se entrega ({@link MotivoDeRechazo#MODERACION_NO_DISPONIBLE}): es la misma
 * decision D-14 del chat, porque una caida no puede ser la via para esquivar
 * una sancion o el filtro.
 *
 * <p>Solo lo que pasa todo se guarda, se entrega al destinatario y al
 * remitente (sus otras pestanas) y, si el destinatario no esta escuchando, se
 * le avisa en su bandeja: una vez por racha de no leidos del mismo remitente,
 * para que diez mensajes seguidos no sean diez avisos. La racha se identifica
 * por su primer no leido, no por «este es el primero»: si ese primero llego
 * mientras escuchaba y se fue sin leerlo, lo que siguiera no avisaria nunca.
 * Cada mensaje sin escuchar pide el aviso con el id de la racha y
 * notificaciones descarta los repetidos (409).
 */
public class EnviarMensajeDirecto {

    private static final Logger log = LoggerFactory.getLogger(EnviarMensajeDirecto.class);

    /** El mismo largo que el chat (HU-JUE-015): un mensaje privado es un mensaje del chat. */
    public static final int LARGO_MAXIMO = EnviarMensaje.LARGO_MAXIMO;

    /** {@code maxLength} de {@code idCliente} en el AsyncAPI y en el OpenAPI. */
    public static final int LARGO_MAXIMO_ID_CLIENTE = 64;

    private final RepositorioDeMensajesDirectos repositorio;
    private final SancionesDelJugador sanciones;
    private final DirectorioDeJugadores directorio;
    private final FiltroDeMensajesPrivados filtro;
    private final LimiteDeFrecuencia limite;
    private final EntregaDeMensajesDirectos entrega;
    private final AvisoDeMensajeDirecto aviso;
    private final Clock reloj;
    private final PoliticaDeTexto.Limites limitesDeTexto;

    public EnviarMensajeDirecto(RepositorioDeMensajesDirectos repositorio, SancionesDelJugador sanciones,
                                DirectorioDeJugadores directorio, FiltroDeMensajesPrivados filtro,
                                LimiteDeFrecuencia limite, EntregaDeMensajesDirectos entrega,
                                AvisoDeMensajeDirecto aviso, Clock reloj) {
        this(repositorio, sanciones, directorio, filtro, limite, entrega, aviso, reloj,
                PoliticaDeTexto.Limites.POR_OMISION);
    }

    /**
     * @param limitesDeTexto los del chat ({@link PoliticaDeTexto}, D-37): un
     *     mensaje privado tampoco puede ser un dibujo de simbolos ni una
     *     inundacion de lineas (auditoria de DEV del 30-sep)
     */
    public EnviarMensajeDirecto(RepositorioDeMensajesDirectos repositorio, SancionesDelJugador sanciones,
                                DirectorioDeJugadores directorio, FiltroDeMensajesPrivados filtro,
                                LimiteDeFrecuencia limite, EntregaDeMensajesDirectos entrega,
                                AvisoDeMensajeDirecto aviso, Clock reloj,
                                PoliticaDeTexto.Limites limitesDeTexto) {
        this.limitesDeTexto = Objects.requireNonNull(limitesDeTexto);
        this.repositorio = Objects.requireNonNull(repositorio);
        this.sanciones = Objects.requireNonNull(sanciones);
        this.directorio = Objects.requireNonNull(directorio);
        this.filtro = Objects.requireNonNull(filtro);
        this.limite = Objects.requireNonNull(limite);
        this.entrega = Objects.requireNonNull(entrega);
        this.aviso = Objects.requireNonNull(aviso);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * @param remitente    quien escribe, sacado del token por el adaptador
     * @param destinatario uid de quien recibe
     * @param texto        lo escrito, sin recortar
     * @param idCliente    el que puso el cliente, o {@code null}
     * @return el mensaje guardado (el nuevo, o el ya existente en un reintento)
     * @throws MensajeDirectoRechazado con el motivo del contrato si no se entrega
     */
    public MensajeDirecto enviar(Remitente remitente, UUID destinatario, String texto, String idCliente) {
        Objects.requireNonNull(remitente, "Un mensaje sin remitente no se puede atribuir.");
        String id = idClienteValido(idCliente);
        if (destinatario == null) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_INEXISTENTE, id);
        }
        if (remitente.id().equals(destinatario)) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_PROPIO, id);
        }
        String limpio = textoValido(texto, id);

        if (id != null) {
            Optional<MensajeDirecto> yaGuardado = repositorio.buscarPorIdCliente(remitente.id(), id);
            if (yaGuardado.isPresent()) {
                reenviarEco(yaGuardado.get());
                return yaGuardado.get();
            }
        }

        limite.registrar(remitente.id()).ifPresent(espera -> {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DEMASIADO_RAPIDO, id, espera);
        });

        comprobarSancion(remitente, id);
        CuentaDeJugador cuenta = cuentaActiva(destinatario, id);
        comprobarTexto(limpio, id);

        MensajeDirecto nuevo = new MensajeDirecto(UUID.randomUUID(),
                Conversacion.entre(remitente.id(), destinatario), remitente.id(), remitente.apodo(),
                destinatario, cuenta.apodo(), limpio,
                // Microsegundos: lo que guarda PostgreSQL. Asi la fecha del eco
                // y la del historial son la misma, y `antesDe` no se come nada.
                reloj.instant().truncatedTo(ChronoUnit.MICROS), null, id);

        Guardado guardado = repositorio.guardar(nuevo);
        if (!guardado.nuevo()) {
            // Dos envios con el mismo idCliente se cruzaron: gano el otro.
            reenviarEco(guardado.mensaje());
            return guardado.mensaje();
        }

        MensajeDirecto mensaje = guardado.mensaje();
        entregar(mensaje);
        avisarSiNoEscucha(mensaje);
        return mensaje;
    }

    private static String idClienteValido(String idCliente) {
        if (idCliente == null || idCliente.isBlank()) {
            return null;
        }
        if (idCliente.length() > LARGO_MAXIMO_ID_CLIENTE) {
            // No se devuelve: no es el identificador de ningun envio valido.
            throw new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_INVALIDO, null);
        }
        return idCliente;
    }

    private String textoValido(String texto, String idCliente) {
        // Lo mismo que el chat (PoliticaDeTexto): se guarda el texto depurado
        // —NFKC, sin invisibles— y un dibujo de simbolos o una inundacion de
        // lineas no es un mensaje (auditoria de DEV del 30-sep).
        String limpio = PoliticaDeTexto.depurar(texto);
        if (limpio.isEmpty() || limpio.length() > LARGO_MAXIMO
                || PoliticaDeTexto.problema(limpio, limitesDeTexto).isPresent()) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_INVALIDO, idCliente);
        }
        return limpio;
    }

    private void comprobarSancion(Remitente remitente, String idCliente) {
        boolean sancionado;
        try {
            sancionado = sanciones.tieneSancionActiva(remitente.id());
        } catch (SancionesNoDisponibles caida) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.MODERACION_NO_DISPONIBLE, idCliente);
        }
        if (sancionado) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.SANCIONADO, idCliente);
        }
    }

    private CuentaDeJugador cuentaActiva(UUID destinatario, String idCliente) {
        try {
            return directorio.buscar(destinatario)
                    .filter(CuentaDeJugador::activa)
                    .orElseThrow(() -> new MensajeDirectoRechazado(
                            MotivoDeRechazo.DESTINATARIO_INEXISTENTE, idCliente));
        } catch (DirectorioNoDisponible caida) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.MODERACION_NO_DISPONIBLE, idCliente);
        }
    }

    private void comprobarTexto(String limpio, String idCliente) {
        switch (filtro.verificar(limpio)) {
            case ENTREGABLE -> {
                // pasa
            }
            case BLOQUEADO -> throw new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_NO_PERMITIDO, idCliente);
            default -> throw new MensajeDirectoRechazado(MotivoDeRechazo.MODERACION_NO_DISPONIBLE, idCliente);
        }
    }

    /**
     * El mensaje ya esta guardado: si el broker fallara al entregarlo, se
     * pierde la entrega en vivo, no el mensaje, que espera en el historial.
     * Por eso no se propaga: el remitente recibiria un error por algo que si
     * quedo hecho.
     */
    private void entregar(MensajeDirecto mensaje) {
        try {
            entrega.entregar(mensaje);
        } catch (RuntimeException fallo) {
            log.warn("El mensaje privado {} se guardo pero no se pudo entregar en vivo: {}",
                    mensaje.id(), fallo.getMessage());
        }
    }

    private void reenviarEco(MensajeDirecto mensaje) {
        try {
            entrega.reenviarEco(mensaje);
        } catch (RuntimeException fallo) {
            log.warn("No se pudo reenviar el eco del mensaje privado {}: {}", mensaje.id(), fallo.getMessage());
        }
    }

    /**
     * Aviso en la bandeja solo si no esta escuchando, con el id de la racha
     * de no leidos de este remitente: uno por racha, aunque la racha la haya
     * abierto un mensaje que llego mientras escuchaba.
     */
    private void avisarSiNoEscucha(MensajeDirecto mensaje) {
        try {
            if (entrega.estaEscuchando(mensaje.destinatario())) {
                return;
            }
            UUID racha = repositorio.primerNoLeido(mensaje.destinatario(), mensaje.remitente())
                    .map(MensajeDirecto::id)
                    .orElse(mensaje.id());
            aviso.avisar(mensaje, racha);
        } catch (RuntimeException fallo) {
            log.warn("No se pudo decidir el aviso del mensaje privado {}: {}", mensaje.id(), fallo.getMessage());
        }
    }
}

package com.nexusbattles.ms_chatbot.chat.preferencias;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.springframework.stereotype.Component;

/**
 * ms-chatbot.yaml 1.15.0 (§7.4.5 «adaptar el nivel de detalle según la
 * experiencia del usuario»; RF-CHA-010: «recupera el perfil conversacional y
 * ajusta el nivel de detalle»): traduce el nivel AUTOMATICO a uno de verdad.
 *
 * <p>La experiencia es la que tiene con el asistente, medida solo con su
 * propia conversación: cuántas preguntas suyas resolvió ya (un tema de la
 * base, una consulta en vivo o una navegación; las mismas que cuentan en la
 * tasa de resolución, D-CHA-07). No se guarda nada nuevo, no se mira otro
 * servicio y no se perfila a nadie (D-CHA-13, convención provisional):
 *
 * <ul>
 *   <li>menos de {@value #HASTA_QUE_EMPIEZA}: está empezando, así que la
 *       respuesta va completa y con los temas relacionados (DETALLADO);</li>
 *   <li>hasta {@value #DESDE_QUE_CONOCE}: la respuesta completa (NORMAL);</li>
 *   <li>desde {@value #DESDE_QUE_CONOCE}: ya conoce el asistente, así que va
 *       la primera oración (BREVE) con «Más detalle» a un clic; los temas
 *       paso a paso no se recortan.</li>
 * </ul>
 *
 * <p>Sin historial (quien empieza, o quien lo borró: RF-CHA-010 «historial
 * eliminado, que obliga a operar sin personalización») cuenta cero, como
 * cualquiera que llega. Un nivel elegido a mano siempre manda.
 */
@Component
public class ExperienciaDelChat {

    /** Preguntas resueltas por debajo de las cuales se considera que empieza. */
    public static final int HASTA_QUE_EMPIEZA = 5;

    /** Preguntas resueltas desde las que se considera que ya conoce el asistente. */
    public static final int DESDE_QUE_CONOCE = 30;

    private final MensajeRepository mensajes;

    public ExperienciaDelChat(MensajeRepository mensajes) {
        this.mensajes = mensajes;
    }

    /** Las preferencias con las que se responde: AUTOMATICO pasa al nivel de su experiencia. */
    public PreferenciasDeRespuesta efectivas(IdentidadDelChat identidad, PreferenciasDeRespuesta preferencias) {
        if (preferencias.nivelDetalle() != NivelDeDetalle.AUTOMATICO) {
            return preferencias;
        }
        return preferencias.conNivel(nivelPara(resueltas(identidad)));
    }

    /** Cuántas preguntas suyas resolvió ya el asistente; cero sin identidad. */
    public long resueltas(IdentidadDelChat identidad) {
        if (identidad == null) {
            return 0;
        }
        return mensajes.countByConversacionIdentificadorSesionAndRemitenteAndResultadoIn(
            identidad.claveDeConversacion(), Remitente.BOT, ResultadoDeRespuesta.queResuelven());
    }

    /** D-CHA-13: el nivel que corresponde a esa experiencia. */
    public static NivelDeDetalle nivelPara(long resueltas) {
        if (resueltas < HASTA_QUE_EMPIEZA) {
            return NivelDeDetalle.DETALLADO;
        }
        return resueltas < DESDE_QUE_CONOCE ? NivelDeDetalle.NORMAL : NivelDeDetalle.BREVE;
    }
}

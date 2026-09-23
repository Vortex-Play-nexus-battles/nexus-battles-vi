package com.nexusbattles.ms_subastas.subastas.realtime;

import com.nexusbattles.ms_subastas.subastas.dto.SubastaResumenResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * HU-SUB-011. Escucha SubastaActualizadaEvent y lo transmite por STOMP al
 * canal compartido del listado. Un solo componente hace esta traduccion
 * (evento interno -> mensaje STOMP) para que quien publique el evento
 * (PujaApplicationService) no necesite conocer SimpMessagingTemplate ni
 * el nombre del canal.
 *
 * @TransactionalEventListener(AFTER_COMMIT), no @EventListener: hallazgo
 * de Andres -- el evento se publica dentro de la transaccion de
 * PujaApplicationService, que mantiene el lock pesimista de la subasta
 * (SELECT ... FOR UPDATE) hasta el commit. Con @EventListener sincrono,
 * convertAndSend() se ejecutaba CON el lock tomado, serializando a todos
 * los postores de esa subasta detras del envio al broker -- el mismo tipo
 * de riesgo que el README documenta para la llamada a ms-finanzas.
 * AFTER_COMMIT saca el envio de la ventana del lock, y de paso evita
 * transmitir un estado que despues hace rollback.
 *
 * fallbackExecution = true es necesario, no opcional: sin el, Spring
 * descarta en silencio el evento si se publica SIN una transaccion activa
 * -- que es exactamente el caso de SubastaRealtimePublisherIT, que publica
 * el evento directamente, fuera de un metodo @Transactional. Con
 * fallbackExecution, ese caso se ejecuta de inmediato (como un
 * @EventListener normal); el caso real de produccion (dentro de la
 * transaccion de PujaApplicationService) espera al commit.
 */
@Component
public class SubastaRealtimePublisher {

    /**
     * El unico destino de este servicio, y el unico publico.
     *
     * <p>R9.6b lo hace visible: {@code PoliticaDelCanalDeSubastas} necesita
     * saber cual es el destino publico para dejar entrar a un visitante ahi y
     * solo ahi. Tenerlo en dos sitios seria la forma de que un dia dejaran de
     * coincidir y el canal publico se cerrara —o, peor, se abriera otro—.
     */
    public static final String CANAL_LISTADO = "/topic/subastas/listado";

    private final SimpMessagingTemplate mensajeria;

    public SubastaRealtimePublisher(SimpMessagingTemplate mensajeria) {
        this.mensajeria = mensajeria;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alActualizarSubasta(SubastaActualizadaEvent evento) {
        SubastaResumenResponse actualizado = SubastaResumenResponse.desde(evento.getSubasta());
        mensajeria.convertAndSend(CANAL_LISTADO, actualizado);
    }
}

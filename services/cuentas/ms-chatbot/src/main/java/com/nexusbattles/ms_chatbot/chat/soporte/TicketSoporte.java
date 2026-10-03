package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0 (7.4.3, RF-CHA-007, RF-ADM-004): un ticket de soporte
// humano. Lo abre un jugador con sesion (uid del token) y lo atiende un
// administrador. Sin email, nombre ni telefono. El mensaje y el contexto
// llegan aqui YA REDACTADOS: esta clase no ve nunca el texto original.
@Entity
@Table(name = "tickets_soporte")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class TicketSoporte {

    /** Cuantos mensajes de la conversacion se copian como contexto (contrato: maxItems 20). */
    public static final int MAXIMO_DE_CONTEXTO = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String uid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Categoria categoria;

    @Column(nullable = false, length = 150)
    private String asunto;

    @Column(nullable = false, length = 2000)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTicket estado;

    @Column(length = 2000)
    private String respuesta;

    @Column(name = "asignado_a", length = 64)
    private String asignadoA;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tickets_soporte_contexto", joinColumns = @JoinColumn(name = "ticket_id"))
    @OrderColumn(name = "orden")
    private List<MensajeDeContexto> contexto = new ArrayList<>();

    private TicketSoporte(String uid, Categoria categoria, String asunto, String mensaje,
                          List<MensajeDeContexto> contexto, Instant ahora) {
        this.uid = uid;
        this.categoria = categoria;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.estado = EstadoTicket.ABIERTO;
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
        List<MensajeDeContexto> recibido = contexto == null ? List.of() : contexto;
        // Se quedan los MAS RECIENTES: el final de la conversacion es lo que
        // explica por que el jugador pidio ayuda.
        int desde = Math.max(0, recibido.size() - MAXIMO_DE_CONTEXTO);
        this.contexto.addAll(recibido.subList(desde, recibido.size()));
    }

    public static TicketSoporte abrir(String uid, Categoria categoria, String asunto, String mensaje,
                                      List<MensajeDeContexto> contexto, Instant ahora) {
        return new TicketSoporte(uid, categoria, asunto, mensaje, contexto, ahora);
    }

    /**
     * Aplica lo que manda el administrador (PATCH). Solo cambia lo que no es
     * null; {@code desasignar} en true borra la asignacion.
     *
     * @throws TransicionNoPermitidaException si el estado actual no lo permite,
     *         o si se pide RESUELTO sin respuesta.
     */
    public void atender(EstadoTicket nuevoEstado, String nuevaRespuesta, String nuevoAsignado,
                        boolean desasignar, Instant ahora) {
        if (estado == EstadoTicket.CERRADO) {
            throw new TransicionNoPermitidaException("El ticket esta cerrado y ya no se puede cambiar.");
        }
        if (nuevoEstado != null && nuevoEstado != estado && !estado.puedePasarA(nuevoEstado)) {
            throw new TransicionNoPermitidaException(
                "Un ticket " + estado + " no puede pasar a " + nuevoEstado + ".");
        }
        String respuestaFinal = nuevaRespuesta != null ? nuevaRespuesta.strip() : respuesta;
        EstadoTicket estadoFinal = nuevoEstado != null ? nuevoEstado : estado;
        if (estadoFinal == EstadoTicket.RESUELTO && (respuestaFinal == null || respuestaFinal.isBlank())) {
            throw new TransicionNoPermitidaException("Para resolver el ticket hace falta una respuesta para el jugador.");
        }
        this.estado = estadoFinal;
        this.respuesta = respuestaFinal == null || respuestaFinal.isBlank() ? null : respuestaFinal;
        if (desasignar) {
            this.asignadoA = null;
        } else if (nuevoAsignado != null && !nuevoAsignado.isBlank()) {
            this.asignadoA = nuevoAsignado.strip();
        }
        this.actualizadoEn = ahora;
    }
}

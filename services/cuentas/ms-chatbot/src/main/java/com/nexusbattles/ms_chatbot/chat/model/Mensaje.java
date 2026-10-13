package com.nexusbattles.ms_chatbot.chat.model;

import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecidaConverter;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-001: un mensaje individual dentro de la ventana de chat.
// 'adjuntoUrl' cubre el criterio "adjuntar capturas" -- el tamano maximo del
// adjunto sigue sin definir (pregunta pendiente al cliente, SRS RF-CHA-002),
// asi que por ahora solo se guarda la URL de lo que ya se subio a otro lado.
@Entity
@Table(name = "mensajes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Remitente remitente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "adjunto_url", length = 500)
    private String adjuntoUrl;

    @Column(name = "fecha_envio", nullable = false)
    private Instant fechaEnvio;

    // HU-CHA-012 (analiticas, V4). Solo los llenan las respuestas del BOT;
    // quedan en null en los mensajes del usuario y en los anteriores a V4.
    @Column(name = "tema_clave", length = 80)
    private String temaClave;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Categoria categoria;

    @Column
    private Boolean escalado;

    @Column(name = "tiempo_respuesta_ms")
    private Integer tiempoRespuestaMs;

    // ms-chatbot.yaml 1.3.4 (V7): pasos, enlaces, tarjetas, respuestas rapidas
    // y oferta de soporte humano de una respuesta del bot. null si no hay.
    @Convert(converter = RespuestaEnriquecidaConverter.class)
    @Column(columnDefinition = "TEXT")
    private RespuestaEnriquecida enriquecido;

    public Mensaje(Conversacion conversacion, Remitente remitente, String contenido, String adjuntoUrl) {
        this(conversacion, remitente, contenido, adjuntoUrl, Instant.now());
    }

    // B11: el mensaje del usuario se guarda despues de generar la respuesta
    // (ninguna llamada HTTP dentro de la transaccion), pero con la hora en que
    // llego, para que el historial siga en orden: pregunta y luego respuesta.
    public Mensaje(Conversacion conversacion, Remitente remitente, String contenido, String adjuntoUrl,
                   Instant fechaEnvio) {
        this.conversacion = conversacion;
        this.remitente = remitente;
        this.contenido = contenido;
        this.adjuntoUrl = adjuntoUrl;
        this.fechaEnvio = fechaEnvio;
    }

    // HU-CHA-012: lo que las analiticas necesitan saber de una respuesta del
    // bot: que tema la respondio (null si se escalo o fue una consulta
    // asistida), si se escalo y cuanto tardo en generarse.
    public void registrarDatosDeRespuesta(String temaClave, Categoria categoria, boolean escalado,
                                          int tiempoRespuestaMs) {
        this.temaClave = temaClave;
        this.categoria = categoria;
        this.escalado = escalado;
        this.tiempoRespuestaMs = tiempoRespuestaMs;
    }

    // 1.3.4: la respuesta enriquecida del bot; una vacia no se guarda.
    public void registrarEnriquecido(RespuestaEnriquecida nuevo) {
        this.enriquecido = nuevo == null || nuevo.estaVacia() ? null : nuevo;
    }
}

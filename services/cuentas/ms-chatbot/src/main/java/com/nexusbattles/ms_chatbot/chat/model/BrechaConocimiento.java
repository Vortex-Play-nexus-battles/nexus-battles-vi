package com.nexusbattles.ms_chatbot.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-011: una pregunta no cubierta, agrupada por su texto normalizado
// (sin tildes, mayusculas ni signos), para alimentar despues la base de
// conocimiento (HU-CHA-004). Dos fuentes, cada una con su contador:
//   - contadorNoUtil: la respuesta a esta pregunta se califico "no util"
//     (el bot respondio, pero mal).
//   - contadorEscalamiento: MotorRespuestas no entendio la pregunta y la
//     escalo (el bot no supo responder).
// No tiene relacion con Mensaje ni con la sesion a proposito: es un
// agregado anonimo que sobrevive aunque el usuario borre su historial.
//
// ms-chatbot.yaml 1.7.0 (V13): el administrador la ve en el panel y la marca
// ATENDIDA (un tema ya la cubre) o DESCARTADA (ruido), y queda quien y
// cuando. Si una ATENDIDA se vuelve a preguntar, vuelve sola a PENDIENTE y
// suma una reapertura: el tema que la cubria no basta (7.4.5 «analisis de
// patrones de consulta»). Una DESCARTADA sigue DESCARTADA aunque se repita.
//
// @DynamicUpdate: cada UPDATE escribe solo las columnas que cambiaron. Asi el
// administrador que marca el estado y el jugador cuya pregunta suma un
// escalamiento a la vez no se pisan uno al otro la columna que no tocaron.
@Entity
@Table(name = "brechas_conocimiento")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class BrechaConocimiento {

    // Coincide con VARCHAR(64) de estado_cambiado_por (V13).
    public static final int LONGITUD_MAXIMA_DE_QUIEN = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "texto_normalizado", nullable = false, unique = true, length = 500)
    private String textoNormalizado;

    // Primera version tal como la escribio un usuario, para que quien revise
    // las brechas lea una pregunta natural y no el texto normalizado.
    @Column(name = "ejemplo_pregunta", nullable = false, columnDefinition = "TEXT")
    private String ejemploPregunta;

    @Column(name = "contador_no_util", nullable = false)
    private int contadorNoUtil;

    @Column(name = "contador_escalamiento", nullable = false)
    private int contadorEscalamiento;

    @Column(name = "fecha_primera_ocurrencia", nullable = false)
    private Instant fechaPrimeraOcurrencia;

    @Column(name = "fecha_ultima_ocurrencia", nullable = false)
    private Instant fechaUltimaOcurrencia;

    // 1.7.0 (V13).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoBrecha estado = EstadoBrecha.PENDIENTE;

    @Column(name = "estado_cambiado_por", length = LONGITUD_MAXIMA_DE_QUIEN)
    private String estadoCambiadoPor;

    @Column(name = "estado_cambiado_en")
    private Instant estadoCambiadoEn;

    @Column(nullable = false)
    private int reaperturas;

    public BrechaConocimiento(String textoNormalizado, String ejemploPregunta) {
        Instant ahora = Instant.now();
        this.textoNormalizado = textoNormalizado;
        this.ejemploPregunta = ejemploPregunta;
        this.contadorNoUtil = 0;
        this.contadorEscalamiento = 0;
        this.fechaPrimeraOcurrencia = ahora;
        this.fechaUltimaOcurrencia = ahora;
        this.estado = EstadoBrecha.PENDIENTE;
    }

    public void registrarNoUtil() {
        this.contadorNoUtil++;
        ocurrio(Instant.now());
    }

    public void registrarEscalamiento() {
        this.contadorEscalamiento++;
        ocurrio(Instant.now());
    }

    /** 1.7.0: escalamientos + «no útil», lo que ordena el listado del panel. */
    public int getTotal() {
        return contadorEscalamiento + contadorNoUtil;
    }

    /**
     * 1.7.0: el estado que le pone un administrador. {@code quien} es el uid
     * de su token (lo resuelve el controlador, nunca el cuerpo). Ponerle el
     * estado que ya tiene no cambia nada: ni quién ni cuándo.
     *
     * @return true si cambió
     */
    public boolean cambiarEstado(EstadoBrecha nuevo, String quien, Instant cuando) {
        if (nuevo == null || nuevo == estado) {
            return false;
        }
        this.estado = nuevo;
        this.estadoCambiadoPor = recortar(quien);
        this.estadoCambiadoEn = cuando;
        return true;
    }

    // Una ATENDIDA que se vuelve a preguntar vuelve a PENDIENTE: el tema que
    // la cubria no basta. Sin quien (no la cambio nadie), con la hora.
    private void ocurrio(Instant ahora) {
        this.fechaUltimaOcurrencia = ahora;
        if (estado == EstadoBrecha.ATENDIDA) {
            this.estado = EstadoBrecha.PENDIENTE;
            this.estadoCambiadoPor = null;
            this.estadoCambiadoEn = ahora;
            this.reaperturas++;
        }
    }

    private static String recortar(String quien) {
        if (quien == null || quien.isBlank()) {
            return null;
        }
        String limpio = quien.strip();
        return limpio.length() > LONGITUD_MAXIMA_DE_QUIEN ? limpio.substring(0, LONGITUD_MAXIMA_DE_QUIEN) : limpio;
    }
}

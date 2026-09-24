package com.nexusbattles.ms_chatbot.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@Entity
@Table(name = "brechas_conocimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class BrechaConocimiento {

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

    public BrechaConocimiento(String textoNormalizado, String ejemploPregunta) {
        Instant ahora = Instant.now();
        this.textoNormalizado = textoNormalizado;
        this.ejemploPregunta = ejemploPregunta;
        this.contadorNoUtil = 0;
        this.contadorEscalamiento = 0;
        this.fechaPrimeraOcurrencia = ahora;
        this.fechaUltimaOcurrencia = ahora;
    }

    public void registrarNoUtil() {
        this.contadorNoUtil++;
        this.fechaUltimaOcurrencia = Instant.now();
    }

    public void registrarEscalamiento() {
        this.contadorEscalamiento++;
        this.fechaUltimaOcurrencia = Instant.now();
    }
}

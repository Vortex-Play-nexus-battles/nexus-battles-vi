package com.nexusbattles.ms_chatbot.chat.motor.model;

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

import java.time.Instant;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-014): una version de la base de conocimiento. Es el
// "modelo" del chatbot: un motor de reglas no se entrena, pero si se
// versiona, se evalua antes de desplegarse y se puede revertir.
//
// Ciclo de vida:
//   BORRADOR  --ponerEnProduccion-->  PRODUCCION  --retirar-->  RETIRADA
//   RETIRADA  --ponerEnProduccion-->  PRODUCCION   (revertir)
@Entity
@Table(name = "versiones_base_conocimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class VersionBaseConocimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVersion estado;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    // Ultima vez que esta version ENTRO a produccion (al desplegarse o al
    // restaurarse con una reversion). Revertir usa esta fecha para saber cual
    // retirada estuvo en produccion justo antes de la actual.
    @Column(name = "fecha_despliegue")
    private Instant fechaDespliegue;

    // Toda version nace como candidata (BORRADOR): nunca se crea una
    // directamente en produccion, siempre pasa por la evaluacion.
    public static VersionBaseConocimiento nuevaCandidata(int numero, String descripcion) {
        VersionBaseConocimiento version = new VersionBaseConocimiento();
        version.numero = numero;
        version.estado = EstadoVersion.BORRADOR;
        version.descripcion = descripcion;
        version.fechaCreacion = Instant.now();
        return version;
    }

    // Desplegar una candidata o restaurar una retirada.
    public void ponerEnProduccion(Instant ahora) {
        if (estado == EstadoVersion.PRODUCCION) {
            throw new IllegalStateException("La version " + numero + " ya esta en produccion.");
        }
        estado = EstadoVersion.PRODUCCION;
        fechaDespliegue = ahora;
    }

    // Sacar de produccion la version vigente. Sus temas se conservan para
    // poder revertir a ella despues.
    public void retirar() {
        if (estado != EstadoVersion.PRODUCCION) {
            throw new IllegalStateException(
                "Solo se retira la version en produccion; la " + numero + " esta en " + estado + ".");
        }
        estado = EstadoVersion.RETIRADA;
    }
}

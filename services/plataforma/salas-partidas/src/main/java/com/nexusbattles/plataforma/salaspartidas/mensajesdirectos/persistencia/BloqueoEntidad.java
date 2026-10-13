package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Fila de bloqueos_mensajes_directos (V15). Solo la usa RepositorioBloqueosJpa. */
@Entity
@Table(name = "bloqueos_mensajes_directos")
@IdClass(BloqueoEntidad.Clave.class)
class BloqueoEntidad {

    @Id
    @Column(name = "id_bloqueador", nullable = false)
    private UUID bloqueador;

    @Id
    @Column(name = "id_bloqueado", nullable = false)
    private UUID bloqueado;

    @Column(name = "bloqueado_en", nullable = false)
    private Instant bloqueadoEn;

    protected BloqueoEntidad() {
    }

    BloqueoEntidad(UUID bloqueador, UUID bloqueado, Instant bloqueadoEn) {
        this.bloqueador = bloqueador;
        this.bloqueado = bloqueado;
        this.bloqueadoEn = bloqueadoEn;
    }

    UUID bloqueador() {
        return bloqueador;
    }

    UUID bloqueado() {
        return bloqueado;
    }

    Instant bloqueadoEn() {
        return bloqueadoEn;
    }

    /** La clave de la fila: quien bloquea y a quien. */
    static class Clave implements Serializable {

        private UUID bloqueador;
        private UUID bloqueado;

        protected Clave() {
        }

        Clave(UUID bloqueador, UUID bloqueado) {
            this.bloqueador = bloqueador;
            this.bloqueado = bloqueado;
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Clave clave
                    && Objects.equals(bloqueador, clave.bloqueador)
                    && Objects.equals(bloqueado, clave.bloqueado);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bloqueador, bloqueado);
        }
    }
}

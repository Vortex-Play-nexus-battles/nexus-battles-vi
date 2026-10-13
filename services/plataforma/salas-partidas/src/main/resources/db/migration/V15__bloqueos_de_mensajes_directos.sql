-- Auditoria de DEV del 30-sep · bloquear a un jugador en los mensajes privados.
--
-- La interfaz ofrecia «Bloquear» (UXC-6) y el servicio no lo tenia: decision
-- del PO pendiente. Las reglas que se aplican son las que la propia interfaz
-- prometia al confirmar, anotadas como provisionales (D-40): mientras dure,
-- ninguno de los dos se escribe por privado; el historial se conserva; solo
-- afecta a los mensajes privados.
--
-- Regla 8: por Flyway, versionado; V1 a V14 no se tocan. Regla 7: solo
-- salas-partidas escribe aqui. Aditiva pura.

CREATE TABLE bloqueos_mensajes_directos (
    id_bloqueador  UUID         NOT NULL,
    id_bloqueado   UUID         NOT NULL,
    bloqueado_en   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_bloqueos_mensajes_directos PRIMARY KEY (id_bloqueador, id_bloqueado),
    CONSTRAINT ck_bloqueos_mensajes_directos_no_a_si_mismo CHECK (id_bloqueador <> id_bloqueado)
);

COMMENT ON TABLE bloqueos_mensajes_directos IS
    'D-40 (provisional): quien bloqueo a quien en los mensajes privados. Con una fila en cualquier sentido, ninguno de los dos se escribe por privado.';

-- «Quien me bloqueo»: para pintar NO_ADMITE en la lista de conversaciones.
CREATE INDEX ix_bloqueos_mensajes_directos_bloqueado
    ON bloqueos_mensajes_directos (id_bloqueado);

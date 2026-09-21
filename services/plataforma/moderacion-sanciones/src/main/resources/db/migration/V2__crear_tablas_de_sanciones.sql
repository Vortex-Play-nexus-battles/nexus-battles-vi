-- HU-USR-004/005/006/007 y HU-NOT-005 (RF-USR-004..007) — Sanciones,
-- apelaciones y avisos pendientes de entrega.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1 (lista negra) no se toca. Esquema moderacion_sanciones, como V1.
--
-- Una fila por sancion; nunca se borra (CA-04 de HU-USR-006: el asiento es
-- permanente). Revertir o reducir es actualizar, no borrar. La vigencia solo
-- aplica a la suspension; una advertencia no restringe nada y un baneo no
-- vence.
CREATE TABLE sanciones (
    id              uuid PRIMARY KEY,
    usuario_id      uuid NOT NULL,
    tipo            varchar(20) NOT NULL,
    motivo          varchar(1000) NOT NULL,
    politica        varchar(200),
    comentario_id   varchar(64),
    emitida_por     uuid NOT NULL,
    rol_emisor      varchar(30) NOT NULL,
    emitida_en      timestamptz NOT NULL,
    vigente_hasta   timestamptz,
    revertida_en    timestamptz,
    revertida_por   uuid,
    motivo_reversion varchar(1000),
    CONSTRAINT ck_sanciones_tipo CHECK (tipo IN ('ADVERTENCIA', 'SUSPENSION', 'BANEO')),
    CONSTRAINT ck_sanciones_vigencia CHECK (
        (tipo = 'SUSPENSION' AND vigente_hasta IS NOT NULL)
        OR (tipo <> 'SUSPENSION' AND vigente_hasta IS NULL)
    )
);

COMMENT ON TABLE sanciones IS
    'Historial disciplinario (HU-USR-004/005/006). Una fila por sancion; revertir o reducir actualiza, nunca borra.';

-- La consulta de sancion activa (chat, comentarios, subastas) busca por
-- usuario las no revertidas: es la consulta caliente del servicio.
CREATE INDEX ix_sanciones_usuario ON sanciones (usuario_id, emitida_en DESC);
CREATE INDEX ix_sanciones_activas ON sanciones (usuario_id) WHERE revertida_en IS NULL AND tipo <> 'ADVERTENCIA';

-- Apelaciones (HU-USR-007): una abierta por sancion como maximo (indice
-- parcial); la decision queda con su motivacion.
CREATE TABLE apelaciones (
    id              uuid PRIMARY KEY,
    sancion_id      uuid NOT NULL REFERENCES sanciones (id),
    usuario_id      uuid NOT NULL,
    argumento       varchar(2000) NOT NULL,
    creada_en       timestamptz NOT NULL,
    estado          varchar(20) NOT NULL,
    decision_motivo varchar(1000),
    resuelta_por    uuid,
    resuelta_en     timestamptz,
    nueva_vigencia  timestamptz,
    CONSTRAINT ck_apelaciones_estado CHECK (estado IN ('PENDIENTE', 'MANTENIDA', 'REDUCIDA', 'REVERTIDA'))
);

CREATE UNIQUE INDEX ux_apelaciones_abierta_por_sancion ON apelaciones (sancion_id) WHERE estado = 'PENDIENTE';
CREATE INDEX ix_apelaciones_pendientes ON apelaciones (creada_en) WHERE estado = 'PENDIENTE';

-- Avisos al modulo de notificaciones (HU-NOT-005, CA-04): si el canal no
-- responde se registra el intento y se reintenta; nunca se pierde. El id es
-- el del evento y viaja al modulo de notificaciones, que lo usa para no
-- duplicar en el reintento.
CREATE TABLE avisos_pendientes (
    id              uuid PRIMARY KEY,
    usuario_id      uuid NOT NULL,
    tipo            varchar(40) NOT NULL,
    titulo          varchar(200) NOT NULL,
    cuerpo          varchar(2000) NOT NULL,
    creado_en       timestamptz NOT NULL,
    entregado_en    timestamptz,
    intentos        integer NOT NULL DEFAULT 0,
    ultimo_error    varchar(500)
);

CREATE INDEX ix_avisos_por_entregar ON avisos_pendientes (creado_en) WHERE entregado_en IS NULL;

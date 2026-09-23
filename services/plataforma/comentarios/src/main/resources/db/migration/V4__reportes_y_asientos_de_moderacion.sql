-- R10.1 — el flujo de moderacion pasa a existir de extremo a extremo.
--
-- ## El defecto que cierra
--
-- `comentarios.estado` ya admitia EN_REVISION desde V1, y el filtro automatico
-- de RF-COM-007 metia comentarios ahi. Lo que no existia era la salida: ni
-- tabla de reportes, ni cola, ni asiento de quien resolvio. Un comentario que
-- entraba en revision se quedaba invisible para siempre, sin que nadie pudiera
-- aprobarlo ni retirarlo. El estado estaba; el camino de vuelta, no.
--
-- ## Migracion nueva, nunca editar V1..V3
--
-- Esas versiones ya estan aplicadas en el servidor de desarrollo y cambiarlas
-- romperia su checksum (misma razon que documenta V3).

-- ---------------------------------------------------------------- reportes
-- RF-COM-006. Un reporte es de UNA persona sobre UN comentario.
CREATE TABLE IF NOT EXISTS comentario_reportes (
    id              VARCHAR(36)  PRIMARY KEY,
    comentario_id   VARCHAR(36)  NOT NULL REFERENCES comentarios (id),
    reportante_id   VARCHAR(64)  NOT NULL,
    categoria       VARCHAR(32)  NOT NULL,
    descripcion     VARCHAR(500),
    fecha           TIMESTAMPTZ  NOT NULL
);

-- "Reporte duplicado del mismo usuario sobre el mismo comentario" es una de
-- las excepciones que nombra CA-03 de la ficha. Se impide en la base, no solo
-- en el servicio: dos peticiones simultaneas del mismo usuario cargan cada una
-- un estado que no ve a la otra, y sin este indice las dos pasarian. Es la
-- misma red de seguridad que V2 puso para la calificacion unica.
CREATE UNIQUE INDEX IF NOT EXISTS uk_reporte_unico_por_usuario
    ON comentario_reportes (comentario_id, reportante_id);

-- La cola ordena por numero de reportes; sin este indice, cada apertura de la
-- cola recorreria la tabla entera.
CREATE INDEX IF NOT EXISTS idx_reportes_por_comentario
    ON comentario_reportes (comentario_id);

-- El limite diario por usuario se cuenta sobre (reportante, fecha).
CREATE INDEX IF NOT EXISTS idx_reportes_por_reportante
    ON comentario_reportes (reportante_id, fecha);

-- ------------------------------------------------------ asientos de moderacion
-- RF-COM-008: "registrando en cada accion el usuario que la realizo, la fecha
-- y hora, el motivo y los estados anterior y nuevo del comentario". Las cinco
-- cosas son columnas, no un texto libre: un historico que hay que interpretar
-- leyendo frases no es trazabilidad.
CREATE TABLE IF NOT EXISTS comentario_moderacion (
    id               VARCHAR(36)  PRIMARY KEY,
    comentario_id    VARCHAR(36)  NOT NULL REFERENCES comentarios (id),
    moderador_id     VARCHAR(64)  NOT NULL,
    apodo_moderador  VARCHAR(100) NOT NULL,
    accion           VARCHAR(20)  NOT NULL,
    motivo           VARCHAR(500) NOT NULL,
    estado_anterior  VARCHAR(20)  NOT NULL,
    estado_nuevo     VARCHAR(20)  NOT NULL,
    fecha            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_moderacion_por_comentario
    ON comentario_moderacion (comentario_id, fecha);

-- ------------------------------------------------------------------ estado
-- El CHECK que faltaba. `estado` era un VARCHAR(20) sin restriccion: cualquier
-- cadena entraba. Ahora que hay cuatro estados y transiciones que dependen de
-- ellos, un valor fuera del juego seria un comentario que el dominio no sabe
-- resolver — y volveria al mismo defecto de origen, un comentario sin salida.
--
-- OCULTO es el estado nuevo: RF-COM-008 obliga a distinguirlo de ELIMINADO
-- ("Ocultar debe preservar el registro en base de datos"). Ninguna fila
-- existente lo tiene, asi que el CHECK entra sin tocar datos.
ALTER TABLE comentarios
    DROP CONSTRAINT IF EXISTS ck_comentarios_estado;

ALTER TABLE comentarios
    ADD CONSTRAINT ck_comentarios_estado
    CHECK (estado IN ('PUBLICADO', 'EN_REVISION', 'OCULTO', 'ELIMINADO'));

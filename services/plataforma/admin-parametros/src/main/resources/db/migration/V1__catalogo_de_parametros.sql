-- Parametros del sistema (RF-ADM-001): catalogo con valor vigente, cambio
-- programado y versiones. Esquema propio `admin_parametros`.

CREATE TABLE parametros (
    clave              VARCHAR(80)  PRIMARY KEY,
    descripcion        VARCHAR(300) NOT NULL,
    tipo               VARCHAR(10)  NOT NULL,
    valor              VARCHAR(200),
    unidad             VARCHAR(30),
    minimo             NUMERIC(18,4),
    maximo             NUMERIC(18,4),
    opciones           VARCHAR(300),
    inalterable        BOOLEAN      NOT NULL DEFAULT FALSE,
    origen             VARCHAR(120) NOT NULL,
    version            INTEGER      NOT NULL DEFAULT 1,
    actualizado_por    UUID,
    actualizado_en     TIMESTAMPTZ,
    valor_programado   VARCHAR(200),
    vigente_desde      TIMESTAMPTZ,
    orden              INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE versiones (
    id             BIGSERIAL    PRIMARY KEY,
    clave          VARCHAR(80)  NOT NULL REFERENCES parametros (clave),
    version        INTEGER      NOT NULL,
    valor_anterior VARCHAR(200),
    valor_nuevo    VARCHAR(200),
    motivo         VARCHAR(500) NOT NULL,
    cambiado_por   UUID         NOT NULL,
    cambiado_en    TIMESTAMPTZ  NOT NULL,
    vigente_desde  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT versiones_unicas UNIQUE (clave, version)
);

CREATE INDEX versiones_clave_idx ON versiones (clave, version DESC);

-- Catalogo inicial. Editables: lo que docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md
-- dejo configurable y lo que ms-subastas declara «configurable desde
-- admin-parametros». Inalterables: valores fijados por el Project Charter.
-- valor NULL = el PO no ha decidido; el consumidor aplica su omision y lo dice.
INSERT INTO parametros (clave, descripcion, tipo, valor, unidad, minimo, maximo, opciones, inalterable, origen, orden) VALUES
('sanciones.suspension.minima-horas', 'Duracion minima de una suspension temporal', 'ENTERO', '1', 'horas', 1, 720, NULL, FALSE, 'D-20 / HU-USR-005', 10),
('sanciones.suspension.maxima-dias', 'Duracion maxima de una suspension temporal', 'ENTERO', '30', 'dias', 1, 365, NULL, FALSE, 'D-20 / HU-USR-005', 11),
('sanciones.apelacion.plazo-dias', 'Dias desde la sancion para poder apelarla', 'ENTERO', '30', 'dias', 1, 365, NULL, FALSE, 'HU-USR-007 (plazo provisional)', 12),
('metricas.umbral-sanciones-por-dia', 'Sanciones en un dia a partir de las que se alerta «alta frecuencia»; vacio = sin alerta', 'ENTERO', NULL, 'sanciones/dia', 1, 10000, NULL, FALSE, 'D-25 / HU-MET-001', 13),
('torneos.costo-inscripcion-por-defecto', 'Creditos que propone el panel al crear un torneo', 'ENTERO', '0', 'creditos', 0, 100000, NULL, FALSE, 'D-23 / HU-TOR-002', 20),
('chat.historial.tamano', 'Mensajes del historial que se cargan al entrar a un chat', 'ENTERO', '50', 'mensajes', 1, 500, NULL, FALSE, 'D-16 / HU-JUE-015', 30),
('chat.mensajes-por-minuto', 'Limite de mensajes por minuto y jugador; vacio = sin limite', 'ENTERO', NULL, 'mensajes/min', 1, 600, NULL, FALSE, 'D-16 / HU-JUE-015', 31),
('salas.apuestas.si-gana-la-maquina', 'Que pasa con la apuesta de los humanos cuando gana la maquina', 'TEXTO', 'LIBERAR', NULL, NULL, NULL, 'LIBERAR|CONSUMIR', FALSE, 'D-02 / HU-JUE-014', 40),
('subastas.incremento-minimo', 'Incremento minimo entre pujas; vacio = pendiente del PO', 'DECIMAL', NULL, 'creditos', 0.01, 1000000, NULL, FALSE, 'RF-SUB-002 (ms-subastas)', 50),
('subastas.max-subastas-activas-por-jugador', 'Subastas activas que un jugador puede tener a la vez', 'ENTERO', '10', 'subastas', 1, 1000, NULL, FALSE, 'RF-SUB-004 (ms-subastas)', 51),
('subastas.max-pujas-activas-por-jugador', 'Pujas activas que un jugador puede tener a la vez', 'ENTERO', '50', 'pujas', 1, 10000, NULL, FALSE, 'RF-SUB-004 (ms-subastas)', 52),
('subastas.intervalo-minimo-segundos', 'Segundos minimos entre dos pujas del mismo jugador', 'ENTERO', '5', 'segundos', 0, 3600, NULL, FALSE, 'RF-SUB-004 (ms-subastas)', 53),
('torneos.dias-entre-torneos', 'Un torneo cada 91 dias', 'ENTERO', '91', 'dias', NULL, NULL, NULL, TRUE, 'Charter / RF-TOR-001', 90),
('torneos.cupos', 'Equipos por torneo', 'ENTERO', '8', 'equipos', NULL, NULL, NULL, TRUE, 'Charter / RF-TOR-004', 91),
('torneos.integrantes-por-equipo', 'Jugadores por equipo de torneo', 'ENTERO', '2', 'jugadores', NULL, NULL, NULL, TRUE, 'Charter / RF-TOR-003', 92),
('partidas.creditos.victoria-uno-a-uno', 'Creditos por ganar una partida 1 contra 1', 'ENTERO', '2', 'creditos', NULL, NULL, NULL, TRUE, 'Charter / RF-JUE-012', 93),
('partidas.creditos.victoria-grupal', 'Creditos por ganar una partida grupal', 'ENTERO', '4', 'creditos', NULL, NULL, NULL, TRUE, 'Charter / RF-JUE-012', 94),
('partidas.creditos.participacion', 'Creditos por participar en una partida', 'ENTERO', '1', 'creditos', NULL, NULL, NULL, TRUE, 'Charter / RF-JUE-012', 95),
('salas.participantes-maximo', 'Participantes maximos por batalla', 'ENTERO', '6', 'jugadores', NULL, NULL, NULL, TRUE, 'Charter / RF-JUE-001', 96),
('plataforma.cpu-autoescalado-porcentaje', 'Uso de procesador que dispara el autoescalado', 'ENTERO', '75', '%', NULL, NULL, NULL, TRUE, 'Charter / RNF-ESC-001', 97),
('plataforma.latencia-objetivo-ms', 'Latencia extremo a extremo objetivo', 'ENTERO', '500', 'ms', NULL, NULL, NULL, TRUE, 'Charter / RNF-REN-001', 98),
('plataforma.disponibilidad-objetivo-porcentaje', 'Disponibilidad objetivo mensual', 'DECIMAL', '99.95', '%', NULL, NULL, NULL, TRUE, 'Charter / RNF-DIS-001', 99);

-- ms-chatbot.yaml 1.14.0 (§7.4.6 «Opcion de adjuntar capturas de pantalla para
-- soporte tecnico»): las capturas de una solicitud de soporte.
--
-- Se guardan en la base del servicio, como el resto de la solicitud: el host
-- de DEV no tiene almacenamiento de objetos y una solicitud tiene como mucho
-- tres capturas de 2 MB (D-CHA-12). Lo que se guarda ya paso el examen
-- (ExaminadorDeCapturas): PNG o JPEG bien formado, sin metadatos (ni EXIF,
-- ni texto incrustado, ni ubicacion). Se borran con su solicitud.
--
-- Solo la ven quien abrio la solicitud y un administrador; nunca se sirven
-- sin pasar por el servicio.

CREATE TABLE tickets_soporte_capturas (
                                        id         UUID PRIMARY KEY,
                                        ticket_id  UUID NOT NULL REFERENCES tickets_soporte (id) ON DELETE CASCADE,
                                        tipo       VARCHAR(20) NOT NULL,
                                        tamano     INTEGER NOT NULL,
                                        ancho      INTEGER NOT NULL,
                                        alto       INTEGER NOT NULL,
                                        contenido  BYTEA NOT NULL,
                                        subida_en  TIMESTAMP WITH TIME ZONE NOT NULL,
                                        CONSTRAINT chk_capturas_tipo CHECK (tipo IN ('image/png', 'image/jpeg')),
                                        CONSTRAINT chk_capturas_tamano CHECK (tamano > 0 AND tamano <= 2097152),
                                        CONSTRAINT chk_capturas_medidas CHECK (ancho BETWEEN 1 AND 4096 AND alto BETWEEN 1 AND 4096)
);

CREATE INDEX ix_capturas_ticket ON tickets_soporte_capturas (ticket_id, subida_en);

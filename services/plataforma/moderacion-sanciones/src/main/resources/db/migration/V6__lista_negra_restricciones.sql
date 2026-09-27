-- HU-ADM-002 — restricciones de la lista negra normalizada (B2).
--
-- V4 creo las columnas y V5 (Java) calculo la forma normalizada y el modo de
-- las filas anteriores. Ya no queda ninguna fila sin forma, asi que se fijan
-- las reglas en la base, no solo en el servicio:
--
--   * la forma normalizada es obligatoria y UNICA: «Spider-Man» y «spiderman»
--     son el mismo termino, y el segundo alta responde 409;
--   * modo y creado_en obligatorios (creado_en con valor por omision, igual
--     que fecha_creacion en V1);
--   * categoria y modo solo admiten los valores del contrato.
ALTER TABLE terminos_prohibidos ALTER COLUMN normalizado SET NOT NULL;
ALTER TABLE terminos_prohibidos ALTER COLUMN modo SET NOT NULL;
ALTER TABLE terminos_prohibidos ALTER COLUMN creado_en SET DEFAULT now();
ALTER TABLE terminos_prohibidos ALTER COLUMN creado_en SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_terminos_prohibidos_normalizado
    ON terminos_prohibidos (normalizado);

ALTER TABLE terminos_prohibidos
    ADD CONSTRAINT ck_terminos_prohibidos_categoria
    CHECK (categoria IN ('OFENSIVO', 'MARCA', 'CELEBRIDAD', 'POLITICO', 'DIRIGENTE', 'OTRO'));

ALTER TABLE terminos_prohibidos
    ADD CONSTRAINT ck_terminos_prohibidos_modo
    CHECK (modo IN ('SUBCADENA', 'PALABRA'));

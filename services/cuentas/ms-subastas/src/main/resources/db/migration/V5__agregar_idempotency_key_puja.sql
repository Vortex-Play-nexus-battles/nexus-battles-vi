-- HU-SUB-004: la cabecera Idempotency-Key ya evitaba reservar los creditos dos
-- veces, porque ms-finanzas reconoce la clave. Lo que NO evitaba era que un
-- reintento del cliente —el caso normal cuando la respuesta se pierde por red—
-- se evaluara como una puja nueva: llegaba con el precio ya subido por su
-- propia puja anterior y se rechazaba con OFERTA_INSUFICIENTE, de modo que el
-- jugador nunca llegaba a saber que su puja SI habia entrado.
--
-- Guardando la clave junto a la puja, un reintento devuelve la puja original.

ALTER TABLE pujas ADD COLUMN idempotency_key VARCHAR(128);

-- Parcial y no una constraint UNIQUE normal: las pujas anteriores a esta
-- migracion tienen la columna nula y en PostgreSQL varios NULL no chocan entre
-- si, pero dejarlo explicito documenta que solo las pujas con clave compiten.
CREATE UNIQUE INDEX uq_pujas_idempotency_key
    ON pujas (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ms-chatbot.yaml 1.6.0 (7.4.10 «Gestion de la base de conocimiento»,
-- RF-CHA-013).
--
-- ---------------------------------------------------------------------------
-- 1. «Asociar respuestas con multiples intenciones»
-- ---------------------------------------------------------------------------
-- Un tema conserva su `categoria` (la intencion principal: la que cuentan las
-- analiticas y la que se guarda con cada respuesta) y puede atender hasta
-- cinco intenciones mas con la misma respuesta. Una fila por intencion
-- adicional.
--
--   * ON DELETE CASCADE: eliminar un tema, importar (que vacia la candidata)
--     o descartar la candidata no deja filas huerfanas.
--   * Cada copia de un tema (una por version) tiene sus propias filas: crear
--     la candidata copia tambien las intenciones (TemaConocimiento.copiarA),
--     y desplegarla o revertir no mueve nada, porque las filas van con el tema.
--   * La clave primaria impide repetir una intencion en el mismo tema y el
--     CHECK, una que no existe. «Como mucho cinco» y «nunca la principal» los
--     valida el servicio (400): un CHECK no puede leer la categoria del tema.
--
-- La migracion V11 (aplicada) copio temas con SQL propio antes de que esta
-- tabla existiera: no hay intenciones que arrastrar.
CREATE TABLE intenciones_adicionales_de_tema (
    tema_id   UUID        NOT NULL REFERENCES temas_conocimiento (id) ON DELETE CASCADE,
    categoria VARCHAR(40) NOT NULL,
    CONSTRAINT pk_intenciones_adicionales_de_tema PRIMARY KEY (tema_id, categoria),
    CONSTRAINT chk_intenciones_adicionales_categoria CHECK (categoria IN (
        'PRODUCTO', 'MECANICA_JUEGO', 'MODALIDAD_JUEGO', 'CUENTA_Y_REGISTRO', 'SUBASTA_Y_COMERCIO',
        'SOPORTE_TECNICO', 'POLITICAS_Y_TERMINOS', 'FAQ_GENERAL'))
);

-- ---------------------------------------------------------------------------
-- 2. Transiciones de version sin carreras
-- ---------------------------------------------------------------------------
-- Desplegar, la publicacion programada, revertir, crear o descartar la
-- candidata y editarla podian cruzarse: dos despliegues a la vez, o un
-- despliegue y una reversion, terminaban en un 500 por el indice parcial
-- uk_versiones_una_en_produccion de V4, o en una version escrita con lo que
-- otro ya habia cambiado.
--
-- Desde 1.6.0 cada transicion empieza bloqueando la fila de la version que va
-- a cambiar (SELECT ... FOR NO KEY UPDATE, BloqueoDeVersiones) y la que llega
-- tarde falla limpia con 409 CAMBIO_CONCURRENTE. `revision` es ademas la marca
-- de concurrencia optimista (JPA @Version): todo UPDATE de una version lleva
-- `WHERE revision = :leida`, asi que una escritura hecha con una lectura vieja
-- no encuentra la fila y tampoco pisa el cambio del otro.
--
-- Se llama `revision` y no `version` porque la fila ya ES una version (su
-- numero esta en `numero`).
ALTER TABLE versiones_base_conocimiento
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN versiones_base_conocimiento.revision IS
    'Marca de concurrencia optimista (JPA @Version). ms-chatbot.yaml 1.6.0.';

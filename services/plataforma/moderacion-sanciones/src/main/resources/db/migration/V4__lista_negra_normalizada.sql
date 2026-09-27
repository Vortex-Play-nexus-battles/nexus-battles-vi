-- HU-ADM-002, 7.1.1 y 7.3.2 del documento del curso — lista negra normalizada
-- (B2, BACKEND-04; contracts/openapi/moderacion-lista-negra.yaml 2.0.x).
--
-- Regla 8 de plataforma: migracion nueva y ADITIVA. V1 y V3 no se tocan, no se
-- borra ninguna fila ni ninguna columna (fecha_creacion se queda: la sigue
-- rellenando su valor por omision).
--
-- Por que: la tabla solo guardaba el termino tal como se escribio y el servicio
-- comparaba con `contains` tras quitar tildes; «spider-man», «spider man» o
-- «sp1derman» pasaban aunque «spiderman» estuviera en la lista. Desde aqui cada
-- termino guarda:
--
--   normalizado     su forma compacta, calculada por la MISMA funcion que se
--                   aplica al texto (NormalizadorDeTexto). Es la identidad del
--                   termino: V6 la hace unica.
--   categoria       OFENSIVO, MARCA, CELEBRIDAD, POLITICO, DIRIGENTE u OTRO
--                   (7.1.1). Las filas que ya existan quedan en OTRO.
--   modo            SUBCADENA o PALABRA (como casa con un texto).
--   activo          se puede apagar un termino sin borrarlo.
--   creado_por      apodo de quien lo dio de alta ('semilla' para V7).
--   creado_en       fecha de alta con zona horaria.
--   actualizado_en  ultima edicion.
--
-- `normalizado` y `modo` nacen sin NOT NULL: las filas que ya existan las
-- rellena V5, una migracion Java que usa NormalizadorDeTexto. Reescribir la
-- normalizacion en SQL daria dos algoritmos que tarde o temprano no
-- coincidirian, y la forma guardada dejaria de ser comparable con la del
-- texto. V6 pone las restricciones cuando ya no queda ninguna fila sin forma.
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS normalizado varchar(255);
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS categoria varchar(20) NOT NULL DEFAULT 'OTRO';
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS modo varchar(20);
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS creado_por varchar(100);
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS creado_en timestamptz;
ALTER TABLE terminos_prohibidos ADD COLUMN IF NOT EXISTS actualizado_en timestamptz;

-- La fecha de alta ya existia, sin zona (LocalDateTime de la JVM, que corre en
-- UTC en todos los entornos: Clock.systemUTC() e imagenes sin TZ). Se conserva.
UPDATE terminos_prohibidos
   SET creado_en = fecha_creacion AT TIME ZONE 'UTC'
 WHERE creado_en IS NULL;

COMMENT ON COLUMN terminos_prohibidos.normalizado IS
    'Forma compacta (NormalizadorDeTexto): con la que se compara y la que no se puede repetir.';
COMMENT ON COLUMN terminos_prohibidos.categoria IS
    'Categoria del 7.1.1: OFENSIVO, MARCA, CELEBRIDAD, POLITICO, DIRIGENTE u OTRO.';
COMMENT ON COLUMN terminos_prohibidos.modo IS
    'SUBCADENA: dentro del texto compacto. PALABRA: palabra entera o texto entero.';

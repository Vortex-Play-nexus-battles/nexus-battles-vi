-- ============================================================================
--  SEMILLA PROVISIONAL DE DESARROLLO — PENDIENTE DE APROBACION DEL PRODUCT OWNER
-- ============================================================================
--
--  Decision del PO pendiente: CONTENIDO DE LA LISTA NEGRA. Ningun documento del
--  curso da la lista; el 7.1.1 solo dice que el apodo «no podra contener
--  palabras ofensivas o nombres respetados o reconocidos politicos,
--  celebridades, dirigentes, o marcas registradas entre otros», y el 7.3.2 que
--  la lista negra es «actualizable».
--
--  Esto NO es esa lista. Es un conjunto minimo y prudente para que el entorno
--  de desarrollo no arranque con la tabla vacia —asi fue como «spiderman» paso
--  como apodo delante del profesor: la tabla estaba vacia en DEV— y para que la
--  demostracion muestre cada categoria del 7.1.1 con un ejemplo evidente. No
--  pretende ser una lista juridica de marcas ni de personas.
--
--  La lista se administra EN CALIENTE con la API (moderacion-lista-negra.yaml,
--  /api/v1/lista-negra/terminos, rol MODERADOR o superior) o desde la vista
--  «Lista negra» de la consola: alta, edicion, activar/desactivar y baja, sin
--  desplegar. El PO puede borrar o apagar cualquiera de estos terminos por ahi;
--  esta migracion no los vuelve a crear (Flyway la aplica una sola vez) y es
--  idempotente (ON CONFLICT DO NOTHING): si un termino ya existia con la misma
--  forma normalizada, se queda el que habia.
--
--  Modo de cada termino: el de omision (SUBCADENA desde 5 letras
--  normalizadas, PALABRA por debajo) salvo dos, que van en PALABRA a
--  proposito porque en modo SUBCADENA aparecerian entre palabras corrientes
--  al juntar el texto:
--    * messi   — «el mes siguiente» se compacta en «elmessiguiente»;
--    * stalin  — «esta linea» y «esta lindo» se compactan en «estalinea» y
--                «estalindo».
--  En PALABRA siguen cayendo «Messi», «Messi10», «m3ss1» o «Stalin1945».
--
--  `normalizado` es la forma que calcula NormalizadorDeTexto para cada
--  termino (minusculas, sin tildes ni separadores); ListaNegraSemillaIT
--  comprueba que coincide, para que ninguna fila de esta semilla quede con una
--  forma que el servicio no calcularia.
-- ============================================================================
INSERT INTO terminos_prohibidos (termino, normalizado, categoria, modo, activo, creado_por, creado_en)
VALUES
    -- El caso del profesor: personaje y marca registrada.
    ('spiderman',  'spiderman',  'MARCA',      'SUBCADENA', true, 'semilla', now()),
    -- Marcas y personajes registrados muy conocidos.
    ('batman',     'batman',     'MARCA',      'SUBCADENA', true, 'semilla', now()),
    ('superman',   'superman',   'MARCA',      'SUBCADENA', true, 'semilla', now()),
    ('marvel',     'marvel',     'MARCA',      'SUBCADENA', true, 'semilla', now()),
    ('pokemon',    'pokemon',    'MARCA',      'SUBCADENA', true, 'semilla', now()),
    ('nintendo',   'nintendo',   'MARCA',      'SUBCADENA', true, 'semilla', now()),
    ('coca-cola',  'cocacola',   'MARCA',      'SUBCADENA', true, 'semilla', now()),
    -- Celebridades mundialmente conocidas.
    ('messi',      'messi',      'CELEBRIDAD', 'PALABRA',   true, 'semilla', now()),
    ('shakira',    'shakira',    'CELEBRIDAD', 'SUBCADENA', true, 'semilla', now()),
    -- Dirigentes y politicos historicos inequivocos.
    ('hitler',     'hitler',     'DIRIGENTE',  'SUBCADENA', true, 'semilla', now()),
    ('stalin',     'stalin',     'DIRIGENTE',  'PALABRA',   true, 'semilla', now()),
    ('mussolini',  'mussolini',  'POLITICO',   'SUBCADENA', true, 'semilla', now()),
    -- Insultos y obscenidades evidentes en espanol. Los cortos van en PALABRA:
    -- «computadora», «disputa», «vehiculo» o «calculo» no pueden caer.
    ('puta',       'puta',       'OFENSIVO',   'PALABRA',   true, 'semilla', now()),
    ('puto',       'puto',       'OFENSIVO',   'PALABRA',   true, 'semilla', now()),
    ('culo',       'culo',       'OFENSIVO',   'PALABRA',   true, 'semilla', now()),
    ('mierda',     'mierda',     'OFENSIVO',   'SUBCADENA', true, 'semilla', now()),
    ('cabrón',     'cabron',     'OFENSIVO',   'SUBCADENA', true, 'semilla', now()),
    ('pendejo',    'pendejo',    'OFENSIVO',   'SUBCADENA', true, 'semilla', now()),
    ('malparido',  'malparido',  'OFENSIVO',   'SUBCADENA', true, 'semilla', now()),
    ('hijueputa',  'hijueputa',  'OFENSIVO',   'SUBCADENA', true, 'semilla', now()),
    ('imbécil',    'imbecil',    'OFENSIVO',   'SUBCADENA', true, 'semilla', now()),
    ('gilipollas', 'gilipollas', 'OFENSIVO',   'SUBCADENA', true, 'semilla', now())
ON CONFLICT DO NOTHING;

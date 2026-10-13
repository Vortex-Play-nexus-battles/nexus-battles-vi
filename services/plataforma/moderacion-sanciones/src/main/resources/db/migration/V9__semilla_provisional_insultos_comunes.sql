-- ============================================================================
--  AMPLIACION DE LA SEMILLA PROVISIONAL — PENDIENTE DE APROBACION DEL PO
-- ============================================================================
--
--  Auditoria de DEV del 30-sep (informe de Santiago, 7.3.3): «el chat general
--  dejo pasar una groseria». Se comprobo contra /lista-negra/verificar en DEV
--  el 2-oct: «gonorrea», «marica», «idiota» o «pendeja» respondian PERMITIR
--  porque la semilla V7 solo trae diez insultos. La pregunta sigue abierta en
--  #786 (pregunta 6, «¿Cual es la lista real de terminos?») y en D-38: esto
--  NO es esa lista, es el minimo para que lo mas corriente del registro
--  colombiano no pase mientras el PO decide.
--
--  Mismas reglas que V7:
--    * se administra en caliente desde la consola («Lista negra») o la API; el
--      PO puede apagar o borrar cualquiera y Flyway no la vuelve a crear;
--    * ON CONFLICT DO NOTHING: si alguien ya dio de alta la misma forma, se
--      queda la suya;
--    * `normalizado` es lo que calcula NormalizadorDeTexto (ListaNegraIT lo
--      comprueba para cada fila de la semilla).
--
--  Modo: PALABRA para los que en SUBCADENA aparecerian entre palabras
--  corrientes al compactar el texto («a ver gas» contiene «verga»,
--  «superrapido» contiene «perra») o que son palabras comunes de otra cosa;
--  SUBCADENA solo para formas largas y compuestas que no aparecen asi por
--  azar. El plural y el genero los resuelve el detector para la categoria
--  OFENSIVO (ver DetectorDeTerminos); por eso «estupida» va aparte de
--  «estupido» —en PALABRA no se cambia el genero—.
--
--  Fuera a proposito: «hp» (tambien una marca de computadores), «coño» (su
--  forma normalizada es «cono»), «polla» (en Colombia es una apuesta) y
--  «chimba» (en Colombia es un elogio).
-- ============================================================================
INSERT INTO terminos_prohibidos (termino, normalizado, categoria, modo, activo, creado_por, creado_en)
VALUES
    ('marica',       'marica',      'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('maricón',      'maricon',     'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('gonorrea',     'gonorrea',    'OFENSIVO', 'SUBCADENA', true, 'semilla', now()),
    ('hpta',         'hpta',        'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('hijo de puta', 'hijodeputa',  'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('verga',        'verga',       'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('careverga',    'careverga',   'OFENSIVO', 'SUBCADENA', true, 'semilla', now()),
    ('perra',        'perra',       'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('zorra',        'zorra',       'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('pirobo',       'pirobo',      'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('malnacido',    'malnacido',   'OFENSIVO', 'SUBCADENA', true, 'semilla', now()),
    ('mamaguevo',    'mamaguevo',   'OFENSIVO', 'SUBCADENA', true, 'semilla', now()),
    ('mamahuevo',    'mamahuevo',   'OFENSIVO', 'SUBCADENA', true, 'semilla', now()),
    ('huevón',       'huevon',      'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('güevón',       'guevon',      'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('idiota',       'idiota',      'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('estúpido',     'estupido',    'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('estúpida',     'estupida',    'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('fuck',         'fuck',        'OFENSIVO', 'PALABRA',   true, 'semilla', now()),
    ('bitch',        'bitch',       'OFENSIVO', 'PALABRA',   true, 'semilla', now())
ON CONFLICT DO NOTHING;

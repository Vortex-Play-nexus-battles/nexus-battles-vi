-- R17 — con que empieza un jugador nuevo (RF-ADM-001: parametros administrables).
--
-- Regla de producto de R17: todo jugador nuevo empieza con creditos y con un
-- heroe equipado, para poder jugar su primera partida sin que nadie le
-- prepare la cuenta a mano. ms-identidad hace el alta y lee estos dos valores
-- de aqui (GET /parametros/{clave}/valor).
--
-- Los dos nacen SIN VALOR a proposito: la cifra de creditos y el kit son
-- decision del PO (PEN-04 sin cifra; D-28 y D-29 en
-- docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md). Mientras sigan vacios,
-- ms-identidad aplica el respaldo PROVISIONAL de desarrollo que le da cada
-- despliegue por variable de entorno (JUGADOR_CREDITOS_INICIALES,
-- JUGADOR_KIT_INICIAL) y deja anotado en el alta que salio de ahi. En cuanto
-- un administrador fije el valor desde el panel, manda el parametro, sin
-- desplegar nada.
INSERT INTO parametros (clave, descripcion, tipo, valor, unidad, minimo, maximo, opciones, inalterable, origen, orden) VALUES
('jugador.creditos-iniciales',
 'Creditos con los que empieza un jugador nuevo; vacio = pendiente del PO (se aplica el respaldo de DEV)',
 'ENTERO', NULL, 'creditos', 1, 1000000, NULL, FALSE, 'D-28 / R17 (PEN-04 sin cifra)', 60),
('jugador.kit-inicial',
 'Productos del kit inicial, separados por comas: un heroe y al menos un arma, armadura o item; vacio = pendiente del PO',
 'TEXTO', NULL, NULL, NULL, NULL, NULL, FALSE, 'D-29 / R17 (HU-SAL-003 exige heroe equipado)', 61);

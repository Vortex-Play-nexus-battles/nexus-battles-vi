-- D-43 (auditoria del 4-oct, cambio autorizado n.º 1) — incremento minimo
-- entre pujas: 5 creditos.
--
-- Hasta aqui `subastas.incremento-minimo` nacia SIN VALOR (V1: «vacio =
-- pendiente del PO») y ms-subastas no dejaba publicar
-- (503 INCREMENTO_MINIMO_NO_CONFIGURADO): la tienda de subastas decia
-- «DECISION PO pendiente». La decision esta tomada: 5 creditos. Se fija aqui,
-- en el catalogo, y no en el codigo: ms-subastas lo lee de admin-parametros
-- (ReglasDesdeParametros) y cada subasta guarda el que regia al publicarse; la
-- interfaz lo muestra tal cual lo dice GET /subastas/reglas.
--
-- Queda registrado en `versiones` como cualquier cambio desde el panel, con su
-- motivo. Si en un entorno ya valia 5, no se toca ni se anota nada: la
-- migracion es la misma en todos los entornos y no inventa una version de mas.
-- Un administrador puede cambiarlo despues desde el panel (PUT /parametros),
-- con motivo y version, como siempre.

INSERT INTO versiones (clave, version, valor_anterior, valor_nuevo, motivo, cambiado_por, cambiado_en, vigente_desde)
SELECT clave,
       version + 1,
       valor,
       '5',
       'D-43: incremento minimo entre pujas de 5 creditos (auditoria del 4-oct). Migracion V5.',
       '00000000-0000-0000-0000-000000000000',
       now(),
       now()
FROM parametros
WHERE clave = 'subastas.incremento-minimo'
  AND (valor IS NULL OR valor <> '5');

UPDATE parametros
SET valor           = '5',
    descripcion     = 'Incremento minimo entre pujas, en creditos (D-43: 5)',
    origen          = 'D-43 / RF-SUB-002 (ms-subastas)',
    version         = version + 1,
    actualizado_por = '00000000-0000-0000-0000-000000000000',
    actualizado_en  = now()
WHERE clave = 'subastas.incremento-minimo'
  AND (valor IS NULL OR valor <> '5');

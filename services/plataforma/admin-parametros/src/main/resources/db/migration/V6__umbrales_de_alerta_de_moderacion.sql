-- SA-4 (§7.3.8 «Alertas de actividad sospechosa o alta frecuencia de
-- reportes», RF-MET-001) — los dos umbrales del tablero de moderacion pasan a
-- leerse de este catalogo (metricas-plataforma, UmbralesDeModeracion), con su
-- variable de entorno solo como respaldo si el catalogo no responde.
--
-- ## 1. Sanciones por dia: la decision del PO (D-25), escrita donde se lee
--
-- El PO fijo el umbral el 2026-10-05: un dia con 3 sanciones o mas es alta
-- frecuencia. Hasta aqui esa cifra solo vivia en el despliegue
-- (METRICAS_UMBRAL_SANCIONES_POR_DIA=3 en docker-compose.deploy.yml) y el
-- catalogo seguia diciendo «sin valor»: el panel de Parametros contradecia a
-- la alerta que si se evaluaba, y cambiarlo desde el panel no hacia nada.
--
-- Se escribe aqui con su version y su motivo, como cualquier cambio desde el
-- panel (mismo patron que V5). Solo si sigue vacio: si en un entorno un
-- administrador ya fijo otra cifra desde el panel, esa decision esta
-- versionada y auditada y no se pisa.
INSERT INTO versiones (clave, version, valor_anterior, valor_nuevo, motivo, cambiado_por, cambiado_en, vigente_desde)
SELECT clave,
       version + 1,
       valor,
       '3',
       'D-25: el PO fijo el 2026-10-05 la alerta de alta frecuencia en 3 sanciones o mas por dia. Migracion V6.',
       '00000000-0000-0000-0000-000000000000',
       now(),
       now()
FROM parametros
WHERE clave = 'metricas.umbral-sanciones-por-dia'
  AND valor IS NULL;

UPDATE parametros
SET valor           = '3',
    descripcion     = 'Sanciones en un dia a partir de las que se alerta «alta frecuencia» (D-25: 3 o mas); vacio = sin alerta',
    origen          = 'D-25 (PO, 2026-10-05) / RF-MET-001',
    version         = version + 1,
    actualizado_por = '00000000-0000-0000-0000-000000000000',
    actualizado_en  = now()
WHERE clave = 'metricas.umbral-sanciones-por-dia'
  AND valor IS NULL;

-- ## 2. Reportes por dia: nace SIN VALOR, porque la cifra es del PO
--
-- La otra mitad de la alerta de §7.3.8. Desde SA-4 comentarios publica la
-- frecuencia de reportes (comentarios.yaml 1.15.0) y el tablero la muestra
-- siempre; lo que ningun documento da es a partir de cuantos reportes en un
-- dia hay que alertar (D-25, mitad de reportes, sigue abierta). Sin valor se
-- publican los conteos y no se evalua ninguna alerta de reportes; en cuanto un
-- administrador escriba la cifra del PO en el panel, el tablero empieza a
-- alertar, sin desplegar nada. El minimo y el maximo solo acotan lo que se
-- puede escribir; no son una propuesta de valor.
INSERT INTO parametros (clave, descripcion, tipo, valor, unidad, minimo, maximo, opciones, inalterable, origen, orden) VALUES
('metricas.umbral-reportes-por-dia',
 'Reportes de comentarios en un dia a partir de los que se alerta «alta frecuencia»; vacio = sin alerta (pendiente del PO)',
 'ENTERO', NULL, 'reportes/dia', 1, 100000, NULL, FALSE, 'D-25 / RF-MET-001 (§7.3.8)', 14);

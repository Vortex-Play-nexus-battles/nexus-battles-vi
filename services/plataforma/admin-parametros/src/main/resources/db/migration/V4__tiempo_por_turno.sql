-- B7 (BACKEND-09) — tiempo por turno de las partidas (RF-ADM-001: parametros
-- administrables).
--
-- El documento del curso dice que el juego se desarrolla «por turnos, con una
-- duracion equitativa para todos los participantes» (seccion 6.1.3), pero no
-- fija cuantos segundos. salas-partidas lee este parametro en cada turno
-- (salas-partidas.yaml 1.7.0, turnoActual.segundosRestantes; canal 1.5.0,
-- turnoCambiado.segundosParaJugar) y, al agotarse, pasa el turno sin accion.
--
-- Nace SIN VALOR a proposito: es decision del PO (D-B7-14 en
-- docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md). Sin valor no hay limite, que
-- es como se jugaba hasta B7. El minimo y el maximo solo acotan lo que se
-- puede escribir desde el panel; no son una propuesta de valor.
INSERT INTO parametros (clave, descripcion, tipo, valor, unidad, minimo, maximo, opciones, inalterable, origen, orden) VALUES
('salas.partidas.segundos-por-turno',
 'Segundos que tiene cada participante para jugar su turno; vacio = sin limite (pendiente del PO)',
 'ENTERO', NULL, 'segundos', 5, 3600, NULL, FALSE, 'D-B7-14 / seccion 6.1.3 (duracion equitativa del turno)', 41);

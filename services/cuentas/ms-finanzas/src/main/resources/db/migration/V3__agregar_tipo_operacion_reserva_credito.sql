-- V3__agregar_tipo_operacion_reserva_credito.sql
-- Distingue el tipo de operación registrada en reserva_credito, para que
-- reversar() pueda identificar y aceptar solo débitos reales (DEBITO),
-- sin confundirlos con reservas de puja activas (RESERVA) ni con créditos
-- ya otorgados (CREDITO). Ver CreditoService.reversar() para el uso.

ALTER TABLE reserva_credito
  ADD COLUMN tipo_operacion VARCHAR(32);

-- Backfill: toda fila creada antes de esta migración vino de reservar(),
-- que era el único flujo que escribía en esta tabla en ese momento.
UPDATE reserva_credito SET tipo_operacion = 'RESERVA' WHERE tipo_operacion IS NULL;

ALTER TABLE reserva_credito
  ALTER COLUMN tipo_operacion SET NOT NULL;

ALTER TABLE reserva_credito
  ADD CONSTRAINT chk_tipo_operacion CHECK (tipo_operacion IN ('RESERVA', 'DEBITO', 'CREDITO'));

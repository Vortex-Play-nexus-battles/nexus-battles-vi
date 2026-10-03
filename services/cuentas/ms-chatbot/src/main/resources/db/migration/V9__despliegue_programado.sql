-- ms-chatbot.yaml 1.3.8 (7.4.10 «programar actualizaciones»): la candidata
-- se puede dejar programada para publicarse sola en una fecha y hora. Al
-- llegar, se evalua igual que un despliegue manual y solo se publica si no
-- acierta menos que produccion.
--
--   despliegue_programado_en    cuando publicarla; null si no esta programada.
--   programacion_rechazada_en   cuando se intento publicarla y no paso la
--                               evaluacion (o faltaban casos); null si no.
ALTER TABLE versiones_base_conocimiento
  ADD COLUMN despliegue_programado_en  TIMESTAMP WITH TIME ZONE,
  ADD COLUMN programacion_rechazada_en TIMESTAMP WITH TIME ZONE;

-- Solo una candidata puede estar programada.
ALTER TABLE versiones_base_conocimiento
  ADD CONSTRAINT chk_versiones_programada_solo_borrador
    CHECK (despliegue_programado_en IS NULL OR estado = 'BORRADOR');

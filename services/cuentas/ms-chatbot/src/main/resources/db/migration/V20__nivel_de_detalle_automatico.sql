-- ms-chatbot.yaml 1.15.0 (7.4.5 «adaptar el nivel de detalle segun la
-- experiencia del usuario», RF-CHA-010): el nivel de detalle AUTOMATICO, el
-- de quien nunca eligio uno. Se traduce al responder con lo que la persona
-- ya pregunto (D-CHA-13); aqui solo se admite guardarlo.
--
-- Las filas que ya existen no cambian: su nivel lo eligio (o lo guardo) esa
-- persona, y no hay forma de distinguir un NORMAL elegido de uno que vino con
-- el idioma. Quien no tiene fila pasa a tener AUTOMATICO sin escribir nada.

ALTER TABLE preferencias_chat DROP CONSTRAINT chk_preferencias_chat_nivel_detalle;

ALTER TABLE preferencias_chat ADD CONSTRAINT chk_preferencias_chat_nivel_detalle
  CHECK (nivel_detalle IN ('AUTOMATICO', 'BREVE', 'NORMAL', 'DETALLADO'));

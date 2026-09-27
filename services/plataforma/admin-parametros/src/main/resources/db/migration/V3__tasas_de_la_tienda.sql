-- B5 (BACKEND-07) — tasas de cambio de la tienda (RF-ADM-001: parametros
-- administrables; 7.5: el precio en la moneda de la ubicacion del cliente).
--
-- ms-ecommerce convierte de COP a USD y EUR con estas dos tasas (TasasDeCambio:
-- pesos colombianos por 1 USD y por 1 EUR) y las lee de este catalogo, nunca
-- del codigo. Cuanto vale un dolar o un euro no lo fija el documento: es
-- decision del PO (D-32 en docs/gobierno/DECISIONES-PENDIENTES-DEL-PO.md).
--
-- Nacen SIN VALOR a proposito. Sin valor, la tienda ofrece solo COP (la vitrina
-- quita esa moneda de monedasDisponibles y pedirla responde 422): no se inventa
-- una tasa. El minimo y el maximo solo acotan lo que se puede escribir desde el
-- panel (PUT /parametros/{clave}, con motivo y version); no son una propuesta.
INSERT INTO parametros (clave, descripcion, tipo, valor, unidad, minimo, maximo, opciones, inalterable, origen, orden) VALUES
('tienda.tasa-cop-usd',
 'Pesos colombianos por 1 USD para mostrar y cobrar en dolares; vacio = solo COP (pendiente del PO)',
 'DECIMAL', NULL, 'COP/USD', 1, 100000, NULL, FALSE, 'D-32 / seccion 7.5 (moneda de la ubicacion)', 60),
('tienda.tasa-cop-eur',
 'Pesos colombianos por 1 EUR para mostrar y cobrar en euros; vacio = solo COP (pendiente del PO)',
 'DECIMAL', NULL, 'COP/EUR', 1, 100000, NULL, FALSE, 'D-32 / seccion 7.5 (moneda de la ubicacion)', 61);

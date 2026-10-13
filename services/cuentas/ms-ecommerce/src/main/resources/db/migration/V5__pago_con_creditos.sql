-- ms-ecommerce V5 (D-44, auditoria del 4-oct, cambio autorizado n.º 5): pagar
-- con los creditos del juego como metodo ADICIONAL al pago simulado
-- (ecommerce-carrito.yaml 1.6.0, POST /checkout/creditos).
--
-- La orden es la misma: mismos estados, mismas claves, mismos pasos despues de
-- cobrar. Lo que cambia es como se pago:
--
--   forma_de_pago  TARJETA (la pasarela simulada, todas las ordenes de antes)
--                  o CREDITOS (ms-finanzas descuenta el saldo del jugador).
--   moneda         la del cobro con tarjeta; NULL con creditos, porque sus
--                  importes son creditos enteros y no una moneda.
--
-- Las restricciones dejan escrito lo que la tienda garantiza: una orden con
-- tarjeta tiene moneda y una con creditos no, y una orden con creditos no
-- tiene centavos.

ALTER TABLE ordenes ADD COLUMN forma_de_pago VARCHAR(10) NOT NULL DEFAULT 'TARJETA';

ALTER TABLE ordenes ALTER COLUMN moneda DROP NOT NULL;

ALTER TABLE ordenes ADD CONSTRAINT ordenes_forma_de_pago
    CHECK (forma_de_pago IN ('TARJETA', 'CREDITOS'));

ALTER TABLE ordenes ADD CONSTRAINT ordenes_moneda_segun_forma_de_pago
    CHECK ((forma_de_pago = 'TARJETA' AND moneda IS NOT NULL)
        OR (forma_de_pago = 'CREDITOS' AND moneda IS NULL));

ALTER TABLE ordenes ADD CONSTRAINT ordenes_creditos_enteros
    CHECK (forma_de_pago <> 'CREDITOS' OR total = trunc(total));

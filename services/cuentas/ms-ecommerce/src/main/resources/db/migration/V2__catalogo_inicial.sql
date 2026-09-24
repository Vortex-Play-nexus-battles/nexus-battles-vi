-- ms-ecommerce: catalogo inicial de la tienda.
--
-- Por que: la vitrina (GET /api/v1/productos) lee esta tabla y en el
-- servidor estaba vacia, asi que la tienda mostraba "La tienda no tiene
-- productos ahora mismo". data.sql no sirve para esto: la base no es
-- embebida (spring.sql.init no lo corre) y el esquema es de Flyway.
--
-- Que: 56 productos de las reglas del juego del curso -- 8 heroes, 16 armas,
-- 16 armaduras, 8 items y 8 habilidades epicas -- con nombre y efectos
-- literales de las tablas del documento del curso.
--
-- Precios: el documento del curso NO los fija (RF-ADM-03: los fija el
-- administrador). Los de aqui son de demostracion, uno por tipo, decididos
-- por el PO del Grupo 2 para la demo del 24-sep-2026, y se cambian despues
-- desde la administracion (RF-ADM-09 / HU-PRD-003), no editando esta
-- migracion (Flyway rechaza una migracion aplicada cuyo checksum cambia).
--   HEROE 20000 | EPICA 10000 | ARMA 6000 | ARMADURA 5000 | ITEM 3000 (COP)
--
-- Idempotente: solo inserta un producto si no hay ya uno con el mismo nombre
-- y tipo, de modo que no duplica nada si alguien lo cargo antes a mano o si
-- la sentencia se vuelve a correr. Cubierto por CatalogoInicialIT.
--
-- imagen_url va en NULL: la tienda pinta la caja de color por tipo cuando no
-- hay imagen. habilidades dice para que heroe es el equipo (y la parte, en
-- las armaduras); en los heroes va en NULL y la tarjeta omite esa linea.
--
-- Generado a partir de un catalogo extraido de las reglas; fuentes:
--   heroes: heroes.md, Tabla 6
--   armas: armas.md, Tabla 8
--   armas: armas.md, Tabla 9
--   armas: armas.md, Tabla 10
--   armas: armas.md, Tabla 11
--   armaduras: armaduras.md, Tabla 12
--   armaduras: armaduras.md, Tabla 13
--   armaduras: armaduras.md, Tabla 14
--   armaduras: armaduras.md, Tabla 15
--   items: items.md, Tabla 16
--   items: items.md, Tabla 17
--   items: items.md, Tabla 18
--   items: items.md, Tabla 19
--   epicas: epicas.md, Tabla 20: Habilidades Épicas de los Héroes

INSERT INTO productos (nombre, descripcion, habilidades, tipo, precio_base_cop,
                       en_promocion, porcentaje_descuento, imagen_url)
SELECT v.nombre, v.descripcion, v.habilidades, v.tipo, v.precio_base_cop,
       FALSE, 0, NULL
FROM (VALUES
    -- heroes
    ('Guerrero Tanque', 'Héroe Guerrero Tanque. Estadísticas de nivel 1: poder 10, vida 44, defensa 11, ataque 10 + 1d6, daño 1d4.', NULL, 'HEROE', 20000.00),
    ('Guerrero Armas', 'Héroe Guerrero Armas. Estadísticas de nivel 1: poder 8, vida 44, defensa 11, ataque 10 + 1d6, daño 1d6.', NULL, 'HEROE', 20000.00),
    ('Mago Fuego', 'Héroe Mago Fuego. Estadísticas de nivel 1: poder 8, vida 40, defensa 10, ataque 10 + 1d8, daño 1d8.', NULL, 'HEROE', 20000.00),
    ('Mago Hielo', 'Héroe Mago Hielo. Estadísticas de nivel 1: poder 10, vida 40, defensa 10, ataque 10 + 1d8, daño 1d6.', NULL, 'HEROE', 20000.00),
    ('Pícaro Veneno', 'Héroe Pícaro Veneno. Estadísticas de nivel 1: poder 8, vida 36, defensa 8, ataque 10 + 1d10, daño 1d6.', NULL, 'HEROE', 20000.00),
    ('Pícaro Machete', 'Héroe Pícaro Machete. Estadísticas de nivel 1: poder 8, vida 36, defensa 8, ataque 10 + 1d10, daño 1d8.', NULL, 'HEROE', 20000.00),
    ('Chamán', 'Héroe Chamán. Estadísticas de nivel 1: poder 10, vida 28, defensa 4, sanar 6 + 1d6.', NULL, 'HEROE', 20000.00),
    ('Médico', 'Héroe Médico. Estadísticas de nivel 1: poder 10, vida 28, defensa 4, sanar 4 + 1d8.', NULL, 'HEROE', 20000.00),
    -- armas
    ('Espada de una mano', '+1 al ataque, +1% de crítico al ataque', 'Para Guerrero Tanque', 'ARMA', 6000.00),
    ('Espada de dos manos', '+1 al ataque, +3% de crítico al ataque', 'Para Guerrero Armas', 'ARMA', 6000.00),
    ('Escudo de dragón', '+1 a la defensa', 'Para Guerrero Tanque', 'ARMA', 6000.00),
    ('Piedra de afilar', '+2 al daño', 'Para Guerrero Armas', 'ARMA', 6000.00),
    ('Orbe de manos ardientes', '+1 al daño, +3% de crítico al ataque', 'Para Mago Fuego', 'ARMA', 6000.00),
    ('Báculo de Permafrost', '-1 al daño del oponente, -2% de crítico al ataque del oponente', 'Para Mago Hielo', 'ARMA', 6000.00),
    ('Fuego fatuo', '+1 al ataque', 'Para Mago Fuego', 'ARMA', 6000.00),
    ('Venas heladas', '+1 al daño', 'Para Mago Hielo', 'ARMA', 6000.00),
    ('Daga purulenta', '+1 al daño por dos turnos, +3% de crítico al ataque', 'Para Pícaro Veneno', 'ARMA', 6000.00),
    ('Machete vendito', '+2 al daño, +2% de crítico al ataque', 'Para Pícaro Machete', 'ARMA', 6000.00),
    ('Visión borrosa', '-1 al ataque del oponente', 'Para Pícaro Veneno', 'ARMA', 6000.00),
    ('Cierra sangrienta', '+2 al daño por dos turnos', 'Para Pícaro Machete', 'ARMA', 6000.00),
    ('Raíz china', '+(2d4) de sanación', 'Para Chamán', 'ARMA', 6000.00),
    ('Kit de urgencias', '+(2d6) de sanación', 'Para Médico', 'ARMA', 6000.00),
    ('Yerbabuena', '+2 de sanación por dos turnos', 'Para Chamán', 'ARMA', 6000.00),
    ('Reanimador', '+(4d6) de sanación', 'Para Médico', 'ARMA', 6000.00),
    -- armaduras
    ('Defensa del enfurecido', '+2 a la defensa, +2 de vida', 'Para Guerrero Tanque (Pecho)', 'ARMADURA', 5000.00),
    ('Puño lúcido', '+2 a la defensa, +1 de vida', 'Para Guerrero Armas (Guantes)', 'ARMADURA', 5000.00),
    ('Magma Ardiente', '+2 a la defensa, +1 de vida', 'Para Guerrero Tanque (Casco)', 'ARMADURA', 5000.00),
    ('Puños en llamas', '+1 a la defensa, +1 de vida', 'Para Guerrero Armas (Brazaletes)', 'ARMADURA', 5000.00),
    ('Túnica arcana', '+1 a la defensa, +2 de vida', 'Para Mago Fuego (Pecho)', 'ARMADURA', 5000.00),
    ('Corona de hielo', '+1 a la defensa, +1 de vida', 'Para Mago Hielo (Casco)', 'ARMADURA', 5000.00),
    ('Caída de fuego', '+1 a la defensa, +1 de vida', 'Para Mago Fuego (Pantalón)', 'ARMADURA', 5000.00),
    ('Ventisca', '+1 a la defensa, +2 de vida', 'Para Mago Hielo (Pecho)', 'ARMADURA', 5000.00),
    ('Mano del desterrado', '+2 a la defensa, +1 de vida', 'Para Pícaro Veneno (Guantes)', 'ARMADURA', 5000.00),
    ('Pie de atleta', '+2 a la defensa, +1 de vida', 'Para Pícaro Machete (Zapatos)', 'ARMADURA', 5000.00),
    ('Atadura carmesí', '+1 a la defensa, +2 de vida', 'Para Pícaro Veneno (Pecho)', 'ARMADURA', 5000.00),
    ('Sangre cruel', '+1 a la defensa, +1 de vida', 'Para Pícaro Machete (Brazaletes)', 'ARMADURA', 5000.00),
    ('Piel de Caminante del Bosque', '+1 a la defensa, +2 de vida', 'Para Chamán (Pecho)', 'ARMADURA', 5000.00),
    ('Bata de Cirujano', '+1 a la defensa, +2 de vida', 'Para Médico (Pecho)', 'ARMADURA', 5000.00),
    ('Casco de Ecos Ancestrales', '+1 a la defensa, +2 de vida', 'Para Chamán (Casco)', 'ARMADURA', 5000.00),
    ('Pantalón de Expedición Médica', '+1 a la defensa, +2 de vida', 'Para Médico (Pantalón)', 'ARMADURA', 5000.00),
    -- items
    ('Pinchos de escudo', 'Si el ataque del oponente es menor que la defensa del guerrero, el oponente recibe +1 de daño.', 'Para Guerrero Tanque', 'ITEM', 3000.00),
    ('Empuñadura de Furia', '+1 de daño por dos turnos. Esto causa que el guerrero pierda -1 de vida en los mismos turnos.', 'Para Guerrero Armas', 'ITEM', 3000.00),
    ('Anillo para Piro-explosión', '+3 de daño', 'Para Mago Fuego', 'ITEM', 3000.00),
    ('Libro de la ventisca helada', '+2 de daño', 'Para Mago Hielo', 'ITEM', 3000.00),
    ('Veneno lacerante', '-1 al poder del oponente. Solo aplica cada dos turnos.', 'Para Pícaro Veneno', 'ITEM', 3000.00),
    ('Mancuerna yugular', 'Explota por 2 el valor en turno causado por la cierra sangrienta en el oponente.', 'Para Pícaro Machete', 'ITEM', 3000.00),
    ('Pluma sanadora', 'Mejora la sanación multiplicando x2', 'Para Chamán', 'ITEM', 3000.00),
    ('Benditas', 'Sana las heridas por tres turnos o cura +1 por tres turnos', 'Para Médico', 'ITEM', 3000.00),
    -- epicas
    ('Golpe de defensa', 'Efecto general: +1 al ataque. Potenciado: +4 al daño, +2% de crítico.', 'Para Guerrero Tanque', 'EPICA', 10000.00),
    ('Segundo impulso', 'Efecto general: Recupera 1d4 de vida. Potenciado: +3 a la vida, +5% de crítico.', 'Para Guerrero Armas', 'EPICA', 10000.00),
    ('Luz cegadora', 'Efecto general: +1 a la vida. Potenciado: +2 al daño, +1% de crítico.', 'Para Mago Fuego', 'EPICA', 10000.00),
    ('Frio concentrado', 'Efecto general: -1 de poder al oponente. Potenciado: No recibe ningún daño en el siguiente turno.', 'Para Mago Hielo', 'EPICA', 10000.00),
    ('Toma y lleva', 'Efecto general: +1 al ataque. Potenciado: Disminuye a la mitad del daño causado por el oponente y se lo retorna.', 'Para Pícaro Veneno', 'EPICA', 10000.00),
    ('Intimidación sangrienta', 'Efecto general: +1 al daño. Potenciado: +2 a la vida, +2% de crítico.', 'Para Pícaro Machete', 'EPICA', 10000.00),
    ('Té changua', 'Efecto general: No aplica. Potenciado: Sana a todos +(4d8).', 'Para Chamán', 'EPICA', 10000.00),
    ('Reanimador 3000', 'Efecto general: No aplica. Potenciado: Se asocia con un compañero. Si este último fallece, se reanima con el 20% de su salud.', 'Para Médico', 'EPICA', 10000.00)
) AS v (nombre, descripcion, habilidades, tipo, precio_base_cop)
WHERE NOT EXISTS (
    SELECT 1 FROM productos p
    WHERE p.nombre = v.nombre AND p.tipo = v.tipo
);

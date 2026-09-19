-- P2.4 · HU-SAL-003 + HU-SAL-005 (RF-JUE-003, RF-JUE-009) — el heroe viaja.
--
-- Desde SCRUM-1074 la puerta de heroe consulta al inventario cada vez que
-- alguien entra, y recibe el heroe con el que va a combatir. Esa respuesta se
-- usaba para decidir «pasa / no pasa» y se tiraba. El resultado: partidas con
-- participantes sin heroe y una barra de vida imposible de pintar sin
-- inventarse los numeros.
--
-- Se guarda en el ingreso y no se vuelve a pedir al arrancar, por dos razones:
-- al iniciar solo esta autenticado el anfitrion y este servicio no puede
-- preguntar al inventario por el heroe de otro; y el heroe con el que alguien
-- entro es el que apuesta, no el que tenga equipado cinco minutos despues.
--
-- TODO nullable a proposito. Las filas que ya existen entraron antes de que la
-- puerta guardara nada: de esas no se sabe con que heroe se entro, y decirlo
-- con NULL es la verdad. Poner un valor por defecto pintaria barras falsas
-- justo en las salas que la demo tiene abiertas.
-- Discriminante, NOT NULL. Sin ella una fila sin ficha tendria todas sus
-- columnas nuevas nulas; Hibernate colapsa ese embebido a NULL, descarta la
-- entrada del mapa y el participante desaparece de la sala al releerla. Con
-- Set<UUID> no hacia falta porque no habia embebido que colapsar.
ALTER TABLE participantes_de_sala
    ADD COLUMN con_ficha         boolean NOT NULL DEFAULT false,
    ADD COLUMN apodo             varchar(120),
    ADD COLUMN heroe_id          varchar(100),
    ADD COLUMN heroe_nombre      varchar(120),
    ADD COLUMN heroe_retrato_url varchar(500),
    ADD COLUMN heroe_nivel       integer,
    ADD COLUMN heroe_vida_actual integer,
    ADD COLUMN heroe_vida_maxima integer;

-- Misma invariante que en partida_participantes (V6): o la ficha esta completa,
-- o no esta. Media ficha de heroe no la sabe pintar ninguna vista, y una vida
-- maxima sin vida actual es una barra sin relleno.
ALTER TABLE participantes_de_sala
    ADD CONSTRAINT ck_participantes_sala_ficha_completa
        CHECK (
            (con_ficha = false AND apodo IS NULL AND heroe_id IS NULL AND heroe_nombre IS NULL
                AND heroe_vida_actual IS NULL AND heroe_vida_maxima IS NULL)
            OR (con_ficha = true AND apodo IS NOT NULL AND heroe_id IS NOT NULL
                AND heroe_nombre IS NOT NULL
                AND heroe_vida_actual IS NOT NULL AND heroe_vida_maxima IS NOT NULL)
        );

-- Las mismas cotas que exige HeroeDeCombate en el dominio. Se repiten aqui
-- porque la base es la ultima linea: un INSERT que no pase por el agregado
-- -una carga manual, otro servicio manana- no puede dejar una vida negativa.
ALTER TABLE participantes_de_sala
    ADD CONSTRAINT ck_participantes_sala_vida_coherente
        CHECK (
            heroe_vida_actual IS NULL
            OR (heroe_vida_actual >= 0 AND heroe_vida_maxima >= 1
                AND heroe_vida_actual <= heroe_vida_maxima)
        );

COMMENT ON COLUMN participantes_de_sala.apodo IS
    'Apodo en el momento de entrar. Copia: el dueno del apodo es el modulo de cuentas.';
COMMENT ON COLUMN participantes_de_sala.heroe_id IS
    'Heroe validado por el inventario al entrar (SCRUM-1074). NULL en filas anteriores a V7.';

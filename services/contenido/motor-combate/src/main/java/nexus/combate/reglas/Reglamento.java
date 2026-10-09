package nexus.combate.reglas;

import nexus.combate.DistribucionEfectos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static nexus.combate.reglas.Plan.Objetivo.ALIADO_O_SI_MISMO;
import static nexus.combate.reglas.Plan.Objetivo.COMPANERO;
import static nexus.combate.reglas.Plan.Objetivo.COMPANERO_CAIDO_O_VIVO;
import static nexus.combate.reglas.Plan.Objetivo.GRUPO;
import static nexus.combate.reglas.Plan.Objetivo.RIVAL;
import static nexus.combate.reglas.Plan.Objetivo.SI_MISMO;

/**
 * Las reglas de las acciones: las 24 de la Tabla 7, las dos basicas, las 8
 * epicas de la Tabla 20 y «Velo de Sombras», la epica del Master del Templo
 * (7.8.14) — §6.1.1 y §6.1.2.
 *
 * <p><b>Datos contra reglas.</b> El nombre, el coste, la carga y el nivel de
 * desbloqueo de cada accion de la Tabla 7 son DATOS del catalogo de heroes
 * ({@link AccionDelCatalogo}); lo que la accion HACE, su efecto, es una REGLA y
 * vive aqui, una sola vez, con el texto de la tabla al lado. Si el catalogo
 * anade un prototipo con una accion que este reglamento no conoce, el motor no
 * se la inventa: responde {@link MotivoDeRechazo#ACCION_DESCONOCIDA}.
 *
 * <p><b>Como se lee cada efecto</b> (decisiones D-B7-03 a D-B7-09):
 * <ul>
 *   <li>«+N al ataque / al dano» suma a la tirada de ESTE golpe.</li>
 *   <li>«... por dos turnos» deja ademas el mismo bono para el siguiente turno
 *       propio (efecto de familia {@code PROPIO}).</li>
 *   <li>Una defensa («+12 a la defensa», «Inmune al dano fisico») protege hasta
 *       que empieza el siguiente turno del que la usa.</li>
 *   <li>«afecta el ataque / el dano del enemigo» es un efecto sobre el objetivo
 *       si el golpe causa efecto.</li>
 *   <li>«(0dN)» es una tirada entre cero y N.</li>
 *   <li>§6.1.2: las acciones especiales «son afectadas por el multiplicador
 *       asociado al nivel»: todo bono y todo efecto de la Tabla 7 se multiplica
 *       por el nivel; las epicas no (la frase no las incluye) y la Reanimacion
 *       tampoco (el 100 % de la vida no se multiplica).</li>
 * </ul>
 */
public final class Reglamento {

    /** El golpe sin accion especial: la formula de ataque de la Tabla 6. */
    public static final String ATAQUE_BASICO = "ATAQUE_BASICO";
    /** La sanacion sin accion especial de un sanador: la formula «Sanar» de la Tabla 6. */
    public static final String SANACION_BASICA = "SANACION_BASICA";
    /** El motor decide por el ejecutor (la IA de la partida, D-B7-12). */
    public static final String DECISION_DE_LA_MAQUINA = "DECISION_DE_LA_MAQUINA";
    /** §6.1.2: «tienen un turno de carga». Respaldo si el catalogo no lo dice. */
    public static final int TURNOS_DE_CARGA_ACCION = 1;
    /** §6.1.2: las epicas «tienen dos turnos de recarga». */
    public static final int TURNOS_DE_RECARGA_EPICA = 2;

    /** 7.8.14, Velo de Sombras: «+2 a la defensa». */
    static final int VELO_DE_SOMBRAS_DEFENSA = 2;
    /** 7.8.14, Velo de Sombras: «+3 de dano por veneno». */
    static final int VELO_DE_SOMBRAS_VENENO_POR_TURNO = 3;
    /** 7.8.14, Velo de Sombras: «durante 2 turnos». */
    static final int VELO_DE_SOMBRAS_TURNOS_DE_VENENO = 2;

    /** Tabla 7: por nombre normalizado, la regla en funcion del multiplicador de nivel. */
    private static final Map<String, BiFunction<AccionDelCatalogo, Integer, Plan>> TABLA_7 = tabla7();

    /** Tabla 20 y la epica de 7.8.14 (Velo de Sombras), por nombre normalizado. */
    private static final Map<String, Epica> TABLA_20 = tabla20();

    /**
     * El plan de la accion {@code codigo} para este ejecutor.
     *
     * @throws AccionNoPermitida si no es suya, no la ha aprendido, es un
     *                           sanador que ataca, no tiene esa epica o la
     *                           epica no hace nada para su tipo de heroe
     */
    public Plan planPara(String codigo, Contendiente ejecutor, FichaDeCombate ficha) {
        Estadisticas estadisticas = ejecutor.estadisticasResueltas();
        if (ATAQUE_BASICO.equalsIgnoreCase(codigo.trim())) {
            exigirQueAtaque(ejecutor, estadisticas);
            return basico(ATAQUE_BASICO, "Ataque básico", TipoDeAccion.ATAQUE, RIVAL, Plan.Ataque.BASICO, null);
        }
        if (SANACION_BASICA.equalsIgnoreCase(codigo.trim())) {
            if (!estadisticas.sana()) {
                throw new AccionNoPermitida(MotivoDeRechazo.ACCION_DESCONOCIDA,
                        "Solo los sanadores tienen sanación básica.");
            }
            return basico(SANACION_BASICA, "Sanación básica", TipoDeAccion.SANACION, ALIADO_O_SI_MISMO, null,
                    new Plan.Sanacion(Plan.Sanacion.Fuente.FORMULA_SANAR, Tirada.NINGUNA,
                            Plan.Sanacion.Destino.OBJETIVO, List.of()));
        }

        Optional<AccionDelCatalogo> deLaTabla7 = ficha.accion(codigo);
        if (deLaTabla7.isPresent()) {
            AccionDelCatalogo datos = deLaTabla7.get();
            if (ejecutor.nivel() < datos.nivelRequerido()) {
                throw new AccionNoPermitida(MotivoDeRechazo.BLOQUEADA_POR_NIVEL,
                        datos.nombre() + " se aprende en el nivel " + datos.nivelRequerido()
                                + "; tu héroe es de nivel " + ejecutor.nivel() + ".");
            }
            BiFunction<AccionDelCatalogo, Integer, Plan> regla = TABLA_7.get(Nombres.normalizar(datos.nombre()));
            if (regla == null) {
                throw new AccionNoPermitida(MotivoDeRechazo.ACCION_DESCONOCIDA,
                        datos.nombre() + " no tiene regla en el motor de combate.");
            }
            Plan plan = regla.apply(datos, ficha.multiplicador());
            if (plan.ataque() != null) {
                exigirQueAtaque(ejecutor, estadisticas);
            }
            return plan;
        }

        Epica epica = TABLA_20.get(Nombres.normalizar(codigo));
        if (epica != null) {
            boolean laTiene = ejecutor.epicas().stream()
                    .anyMatch(e -> Nombres.normalizar(e).equals(Nombres.normalizar(epica.nombre())));
            if (!laTiene) {
                throw new AccionNoPermitida(MotivoDeRechazo.EPICA_NO_DISPONIBLE,
                        "Tu héroe no tiene la épica " + epica.nombre() + ".");
            }
            boolean afin = Nombres.normalizar(epica.afin()).equals(Nombres.normalizar(ejecutor.prototipo()));
            Plan plan = afin ? epica.potenciada() : epica.general();
            if (plan == null) {
                throw new AccionNoPermitida(MotivoDeRechazo.EPICA_SIN_EFECTO,
                        epica.nombre() + " no tiene efecto para " + ejecutor.prototipo()
                                + " (Tabla 20: «No aplica»).");
            }
            if (plan.ataque() != null) {
                exigirQueAtaque(ejecutor, estadisticas);
            }
            return plan;
        }

        throw new AccionNoPermitida(MotivoDeRechazo.ACCION_DESCONOCIDA,
                "«" + codigo + "» no es una acción de " + ejecutor.prototipo() + ".");
    }

    /**
     * El turno jugado sin poder suficiente — §6.1.1: «Si el heroe carece de
     * poder suficiente para ejecutar una accion, el valor de ataque se reduce a
     * su valor base». La accion no se ejecuta; se juega la basica, y quien la
     * ejecuta usa solo la base de su formula (D-B7-06).
     */
    public Plan planEnValorBase(Contendiente ejecutor) {
        if (ejecutor.estadisticasResueltas().ataca()) {
            return basico(ATAQUE_BASICO, "Ataque básico", TipoDeAccion.ATAQUE, RIVAL, Plan.Ataque.BASICO, null);
        }
        return basico(SANACION_BASICA, "Sanación básica", TipoDeAccion.SANACION, ALIADO_O_SI_MISMO, null,
                new Plan.Sanacion(Plan.Sanacion.Fuente.FORMULA_SANAR, Tirada.NINGUNA,
                        Plan.Sanacion.Destino.OBJETIVO, List.of()));
    }

    /** Si el reglamento conoce la accion de la Tabla 7 con ese nombre. */
    public boolean conoce(String nombreDeAccion) {
        return TABLA_7.containsKey(Nombres.normalizar(nombreDeAccion));
    }

    /** El tipo de una accion de la Tabla 7, para decidir y para listar. */
    public Optional<TipoDeAccion> tipoDe(AccionDelCatalogo accion) {
        BiFunction<AccionDelCatalogo, Integer, Plan> regla = TABLA_7.get(Nombres.normalizar(accion.nombre()));
        return regla == null ? Optional.empty() : Optional.of(regla.apply(accion, 1).tipo());
    }

    /** La epica de la Tabla 20 con ese nombre, si existe. */
    public Optional<Epica> epica(String nombre) {
        return Optional.ofNullable(TABLA_20.get(Nombres.normalizar(nombre)));
    }

    private static void exigirQueAtaque(Contendiente ejecutor, Estadisticas estadisticas) {
        if (!estadisticas.ataca()) {
            throw new AccionNoPermitida(MotivoDeRechazo.SANADOR_NO_ATACA,
                    "Un sanador no puede infligir daño (§6.1.1): elige una sanación.");
        }
        if (DistribucionEfectos.dePrototipo(ejecutor.prototipo()).isEmpty()) {
            throw new AccionNoPermitida(MotivoDeRechazo.SIN_TABLA_DE_EFECTOS,
                    ejecutor.prototipo() + " no tiene fila en la Tabla 21: no puede atacar todavía.");
        }
    }

    private static Plan basico(String codigo, String nombre, TipoDeAccion tipo, Plan.Objetivo objetivo,
                               Plan.Ataque ataque, Plan.Sanacion sanacion) {
        return new Plan(codigo, nombre, tipo, objetivo, false, false, null, 0, ataque, sanacion,
                List.of(), 0, false);
    }

    // ---------------------------------------------------------------- Tabla 7

    private static Map<String, BiFunction<AccionDelCatalogo, Integer, Plan>> tabla7() {
        Map<String, BiFunction<AccionDelCatalogo, Integer, Plan>> t = new LinkedHashMap<>();

        // --- Guerrero Tanque --------------------------------------------------
        // Golpe con escudo · 2 de poder · «+2 al ataque»
        t.put("golpe con escudo", (d, m) -> ataque(d, m, Tirada.fija(2), Tirada.NINGUNA, List.of(), false, List.of()));
        // Mano de piedra · 4 · «+12 a la defensa»
        t.put("mano de piedra", (d, m) -> defensa(d, m, List.of(
                efecto("MANO_DE_PIEDRA", "Mano de piedra", TipoDeEfecto.BONO_DEFENSA, Tirada.fija(12), 1))));
        // Defensa feroz · 6 · «Inmune al dano fisico y (3d6) al dano magico»
        t.put("defensa feroz", (d, m) -> defensa(d, m, List.of(
                efecto("DEFENSA_FEROZ_FISICO", "Defensa feroz", TipoDeEfecto.INMUNE_FISICO, Tirada.NINGUNA, 1),
                efecto("DEFENSA_FEROZ_MAGICO", "Defensa feroz", TipoDeEfecto.REDUCE_MAGICO, Tirada.dados(3, 6), 1))));

        // --- Guerrero Armas ---------------------------------------------------
        // Embate sangriento · 4 · «+2 al ataque +1 de dano»
        t.put("embate sangriento", (d, m) -> ataque(d, m, Tirada.fija(2), Tirada.fija(1), List.of(), false, List.of()));
        // Lanza de los dioses · 4 · «+2 al dano»
        t.put("lanza de los dioses", (d, m) -> ataque(d, m, Tirada.NINGUNA, Tirada.fija(2), List.of(), false, List.of()));
        // Golpe de tormenta · 6 · «+(3d6) al ataque +2 al dano»
        t.put("golpe de tormenta", (d, m) -> ataque(d, m, Tirada.dados(3, 6), Tirada.fija(2), List.of(), false, List.of()));

        // --- Mago Fuego -------------------------------------------------------
        // Misiles de magma · 2 · «+1 al ataque +2 de dano»
        t.put("misiles de magma", (d, m) -> ataque(d, m, Tirada.fija(1), Tirada.fija(2), List.of(), false, List.of()));
        // Vulcano · 6 · «+3 al ataque +(3d9) al dano»
        t.put("vulcano", (d, m) -> ataque(d, m, Tirada.fija(3), Tirada.dados(3, 9), List.of(), false, List.of()));
        // Pare de fuego · 4 · «+1 al ataque y retorna el (0dx) dano causado por el
        // oponente en el turno anterior»
        t.put("pare de fuego", (d, m) -> ataque(d, m, Tirada.fija(1), Tirada.NINGUNA, List.of(), true, List.of()));

        // --- Mago Hielo -------------------------------------------------------
        // Lluvia de hielo · 2 · «+2 al ataque +2 de dano»
        t.put("lluvia de hielo", (d, m) -> ataque(d, m, Tirada.fija(2), Tirada.fija(2), List.of(), false, List.of()));
        // Cono de hielo · 6 · «+2 al dano y afecta el ataque del enemigo en un
        // (1d3) durante los dos turnos siguientes»
        t.put("cono de hielo", (d, m) -> ataque(d, m, Tirada.NINGUNA, Tirada.fija(2), List.of(
                efecto("CONO_DE_HIELO", "Cono de hielo", TipoDeEfecto.PENALIZA_ATAQUE, Tirada.dados(1, 3), 2)),
                false, List.of()));
        // Bola de hielo · 4 · «+2 al ataque y afecta en (0d4) al dano causado por
        // el oponente»
        t.put("bola de hielo", (d, m) -> ataque(d, m, Tirada.fija(2), Tirada.NINGUNA, List.of(
                efecto("BOLA_DE_HIELO", "Bola de hielo", TipoDeEfecto.PENALIZA_DANO, Tirada.entreCeroY(4), 1)),
                false, List.of()));

        // --- Picaro Veneno ----------------------------------------------------
        // Flor de loto · 2 · «+(4d8) al dano»
        t.put("flor de loto", (d, m) -> ataque(d, m, Tirada.NINGUNA, Tirada.dados(4, 8), List.of(), false, List.of()));
        // Agonia · 4 · «+(2d9) de dano»
        t.put("agonia", (d, m) -> ataque(d, m, Tirada.NINGUNA, Tirada.dados(2, 9), List.of(), false, List.of()));
        // Piquete · 4 · «+1 al ataque por dos turnos +2 al dano por 1 turno»
        t.put("piquete", (d, m) -> ataque(d, m, Tirada.fija(1), Tirada.fija(2), List.of(), false, List.of(
                efecto("PIQUETE", "Piquete", TipoDeEfecto.BONO_ATAQUE, Tirada.fija(1), 1))));

        // --- Picaro Machete ---------------------------------------------------
        // Cortada · 2 · «+2 al dano por dos turnos»
        t.put("cortada", (d, m) -> ataque(d, m, Tirada.NINGUNA, Tirada.fija(2), List.of(), false, List.of(
                efecto("CORTADA", "Cortada", TipoDeEfecto.BONO_DANO, Tirada.fija(2), 1))));
        // Machetazo · 4 · «+(2d8) al dano +1 al ataque»
        t.put("machetazo", (d, m) -> ataque(d, m, Tirada.fija(1), Tirada.dados(2, 8), List.of(), false, List.of()));
        // Planazo · 4 · «+(2d8) al ataque +1 al dano»
        t.put("planazo", (d, m) -> ataque(d, m, Tirada.dados(2, 8), Tirada.fija(1), List.of(), false, List.of()));

        // --- Chaman (Sanar 6 + 1d6) ------------------------------------------
        // Toque de la Vida · 2 · «+2 de sanacion»
        t.put("toque de la vida", (d, m) -> sanacion(d, m, Tirada.fija(2), List.of()));
        // Vinculo Natural · 4 · «+2 de sanacion por dos turnos»
        t.put("vinculo natural", (d, m) -> sanacion(d, m, Tirada.fija(2), List.of(
                efecto("VINCULO_NATURAL", "Vínculo Natural", TipoDeEfecto.BONO_SANACION, Tirada.fija(2), 1))));
        // Canto del Bosque · 6 · «Sana al todo el grupo + (2d6) durante dos turnos»
        t.put("canto del bosque", (d, m) -> new Plan(d.nombre(), d.nombre(), TipoDeAccion.SANACION_GRUPAL, GRUPO,
                false, false, d, cargaDe(d),
                null,
                new Plan.Sanacion(Plan.Sanacion.Fuente.FORMULA_SANAR, Tirada.dados(2, 6).porNivel(m),
                        Plan.Sanacion.Destino.GRUPO, List.of(
                        efecto("CANTO_DEL_BOSQUE", "Canto del Bosque", TipoDeEfecto.SANACION_POR_TURNO,
                                Tirada.dados(2, 6), 1).porNivel(m))),
                List.of(), 0, false));

        // --- Medico (Sanar 4 + 1d8) ------------------------------------------
        // Curacion Directa · 2 · «+2 de sanacion»
        t.put("curacion directa", (d, m) -> sanacion(d, m, Tirada.fija(2), List.of()));
        // Neutralizacion de Efectos · 4 · «+2 y +(2d4) de sanacion»
        t.put("neutralizacion de efectos", (d, m) -> sanacion(d, m, Tirada.fijaMasDados(2, 2, 4), List.of()));
        // Reanimacion · todos los puntos de poder · «Sana el 100% de la vida del
        // companero». Sin multiplicador: el 100 % no se multiplica.
        t.put("reanimacion", (d, m) -> new Plan(d.nombre(), d.nombre(), TipoDeAccion.REANIMACION,
                COMPANERO_CAIDO_O_VIVO, false, false, d, cargaDe(d), null,
                new Plan.Sanacion(Plan.Sanacion.Fuente.VIDA_COMPLETA, Tirada.NINGUNA,
                        Plan.Sanacion.Destino.OBJETIVO, List.of()),
                List.of(), 0, false));
        return Map.copyOf(t);
    }

    private static Plan ataque(AccionDelCatalogo d, int m, Tirada bonoAtaque, Tirada bonoDano,
                               List<PlantillaDeEfecto> alAcertar, boolean retorna,
                               List<PlantillaDeEfecto> propios) {
        return new Plan(d.nombre(), d.nombre(), TipoDeAccion.ATAQUE, RIVAL, false, false, d, cargaDe(d),
                new Plan.Ataque(bonoAtaque.porNivel(m), bonoDano.porNivel(m), 0,
                        alAcertar.stream().map(e -> e.porNivel(m)).toList(), retorna),
                null,
                propios.stream().map(e -> e.porNivel(m)).toList(), 0, false);
    }

    private static Plan defensa(AccionDelCatalogo d, int m, List<PlantillaDeEfecto> propios) {
        return new Plan(d.nombre(), d.nombre(), TipoDeAccion.DEFENSA, SI_MISMO, false, false, d, cargaDe(d),
                null, null, propios.stream().map(e -> e.porNivel(m)).toList(), 0, false);
    }

    private static Plan sanacion(AccionDelCatalogo d, int m, Tirada bono, List<PlantillaDeEfecto> propios) {
        return new Plan(d.nombre(), d.nombre(), TipoDeAccion.SANACION, ALIADO_O_SI_MISMO, false, false, d,
                cargaDe(d), null,
                new Plan.Sanacion(Plan.Sanacion.Fuente.FORMULA_SANAR, bono.porNivel(m),
                        Plan.Sanacion.Destino.OBJETIVO, List.of()),
                propios.stream().map(e -> e.porNivel(m)).toList(), 0, false);
    }

    private static int cargaDe(AccionDelCatalogo d) {
        return d.turnosDeCarga() > 0 ? d.turnosDeCarga() : TURNOS_DE_CARGA_ACCION;
    }

    static PlantillaDeEfecto efecto(String codigo, String nombre, TipoDeEfecto tipo, Tirada valor, int turnos) {
        return new PlantillaDeEfecto(codigo, nombre, tipo, valor, turnos);
    }

    // --------------------------------------------------------------- Tabla 20

    /**
     * Una epica de la Tabla 20: su efecto para todos (general) y el que se suma
     * si la juega su tipo de heroe afin (potenciada). Nulo donde la tabla dice
     * «No aplica».
     */
    public record Epica(String nombre, String afin, Plan general, Plan potenciada) {
    }

    private static Map<String, Epica> tabla20() {
        Map<String, Epica> t = new LinkedHashMap<>();
        // Golpe de defensa (Guerrero Tanque): «+1 al ataque» | «+4 al dano, +2% de critico»
        t.put("golpe de defensa", new Epica("Golpe de defensa", "Guerrero Tanque",
                epicaQueAtaca("Golpe de defensa", false, Tirada.fija(1), Tirada.NINGUNA, 0, null, List.of()),
                epicaQueAtaca("Golpe de defensa", true, Tirada.fija(1), Tirada.fija(4), 2, null, List.of())));
        // Segundo impulso (Guerrero Armas): «Recupera 1d4 de vida» | «+3 a la vida +5% de critico»
        t.put("segundo impulso", new Epica("Segundo impulso", "Guerrero Armas",
                epicaQueSana("Segundo impulso", false, Tirada.dados(1, 4), List.of()),
                epicaQueSana("Segundo impulso", true, Tirada.fijaMasDados(3, 1, 4), List.of(
                        efecto("SEGUNDO_IMPULSO", "Segundo impulso", TipoDeEfecto.BONO_CRITICO, Tirada.fija(5), 1)))));
        // Luz cegadora (Mago Fuego): «+1 a la vida» | «+2 al dano +1% de critico»
        t.put("luz cegadora", new Epica("Luz cegadora", "Mago Fuego",
                epicaQueSana("Luz cegadora", false, Tirada.fija(1), List.of()),
                epicaQueAtaca("Luz cegadora", true, Tirada.NINGUNA, Tirada.fija(2), 1, Tirada.fija(1), List.of())));
        // Frio concentrado (Mago Hielo): «-1 de poder al oponente» | «No recibe
        // ningun dano en el siguiente turno»
        t.put("frio concentrado", new Epica("Frío concentrado", "Mago Hielo",
                epicaDeApoyo("Frío concentrado", false, RIVAL, 1, List.of(), false),
                epicaDeApoyo("Frío concentrado", true, RIVAL, 1, List.of(
                        efecto("FRIO_CONCENTRADO", "Frío concentrado", TipoDeEfecto.INMUNE_TOTAL, Tirada.NINGUNA, 1)),
                        false)));
        // Toma y lleva (Picaro Veneno): «+1 al ataque» | «Disminuye a la mitad
        // del dano causado por el oponente y se lo retorna»
        t.put("toma y lleva", new Epica("Toma y lleva", "Pícaro Veneno",
                epicaQueAtaca("Toma y lleva", false, Tirada.fija(1), Tirada.NINGUNA, 0, null, List.of()),
                epicaQueAtaca("Toma y lleva", true, Tirada.fija(1), Tirada.NINGUNA, 0, null, List.of(
                        efecto("TOMA_Y_LLEVA", "Toma y lleva", TipoDeEfecto.REFLEJA_MITAD, Tirada.NINGUNA, 1)))));
        // Intimidacion sangrienta (Picaro Machete): «+1 al dano» | «+2 a la vida +2% de critico»
        t.put("intimidacion sangrienta", new Epica("Intimidación sangrienta", "Pícaro Machete",
                epicaQueAtaca("Intimidación sangrienta", false, Tirada.NINGUNA, Tirada.fija(1), 0, null, List.of()),
                epicaQueAtaca("Intimidación sangrienta", true, Tirada.NINGUNA, Tirada.fija(1), 2, Tirada.fija(2),
                        List.of())));
        // Te changua (Chaman): «No aplica» | «Sana a todos +(4d8)»
        t.put("te changua", new Epica("Té changua", "Chamán", null,
                new Plan("Té changua", "Té changua", TipoDeAccion.SANACION_GRUPAL, GRUPO, true, true, null,
                        TURNOS_DE_RECARGA_EPICA, null,
                        new Plan.Sanacion(Plan.Sanacion.Fuente.FIJA, Tirada.dados(4, 8),
                                Plan.Sanacion.Destino.GRUPO, List.of()),
                        List.of(), 0, false)));
        // Reanimador 3000 (Medico): «No aplica» | «Se asocia con un companero. Si
        // este ultimo fallece, se reanima con el 20% de su salud.»
        t.put("reanimador 3000", new Epica("Reanimador 3000", "Médico", null,
                epicaDeApoyo("Reanimador 3000", true, COMPANERO, 0, List.of(), true)));
        // Velo de Sombras (Picaro Veneno): NO es de la Tabla 20, es la epica del Master
        // «Sombra del Olvido» de la mision de ejemplo (7.8.14). «Efecto general: +2 a la
        // defensa para todos los heroes» | «Efecto epico (solo Picaro Veneno): el heroe se
        // vuelve intangible durante 1 turno, evitando todo el dano recibido y causando
        // envenenamiento al atacante (+3 de dano por veneno durante 2 turnos)».
        // Como en el resto de la tabla, el efecto epico incluye el general (la defensa
        // +2) y suma lo suyo: la intangibilidad es la de Frio concentrado (INMUNE_TOTAL,
        // hasta que empieza su siguiente turno) y el veneno lo deja en quien lo golpea.
        PlantillaDeEfecto masDosDeDefensa = efecto("VELO_DE_SOMBRAS_DEFENSA", "Velo de Sombras",
                TipoDeEfecto.BONO_DEFENSA, Tirada.fija(VELO_DE_SOMBRAS_DEFENSA), 1);
        t.put("velo de sombras", new Epica("Velo de Sombras", "Pícaro Veneno",
                epicaDeDefensa("Velo de Sombras", false, List.of(masDosDeDefensa)),
                epicaDeDefensa("Velo de Sombras", true, List.of(masDosDeDefensa,
                        efecto("VELO_DE_SOMBRAS_INTANGIBLE", "Velo de Sombras", TipoDeEfecto.INMUNE_TOTAL,
                                Tirada.NINGUNA, 1),
                        efecto("VELO_DE_SOMBRAS_VENENO", "Velo de Sombras", TipoDeEfecto.ENVENENA_AL_ATACANTE,
                                Tirada.fija(VELO_DE_SOMBRAS_VENENO_POR_TURNO), VELO_DE_SOMBRAS_TURNOS_DE_VENENO)))));
        return Map.copyOf(t);
    }

    private static Plan epicaQueAtaca(String nombre, boolean potenciada, Tirada bonoAtaque, Tirada bonoDano,
                                      int bonoCritico, Tirada sanacionPropia, List<PlantillaDeEfecto> propios) {
        Plan.Sanacion sanacion = sanacionPropia == null ? null
                : new Plan.Sanacion(Plan.Sanacion.Fuente.FIJA, sanacionPropia, Plan.Sanacion.Destino.SI_MISMO,
                List.of());
        return new Plan(nombre, nombre, TipoDeAccion.ATAQUE, RIVAL, true, potenciada, null,
                TURNOS_DE_RECARGA_EPICA,
                new Plan.Ataque(bonoAtaque, bonoDano, bonoCritico, List.of(), false),
                sanacion, propios, 0, false);
    }

    private static Plan epicaQueSana(String nombre, boolean potenciada, Tirada cantidad,
                                     List<PlantillaDeEfecto> propios) {
        return new Plan(nombre, nombre, TipoDeAccion.SANACION, SI_MISMO, true, potenciada, null,
                TURNOS_DE_RECARGA_EPICA, null,
                new Plan.Sanacion(Plan.Sanacion.Fuente.FIJA, cantidad, Plan.Sanacion.Destino.SI_MISMO, List.of()),
                propios, 0, false);
    }

    /** Una epica que solo se protege: no golpea, no sana y no pide objetivo. */
    private static Plan epicaDeDefensa(String nombre, boolean potenciada, List<PlantillaDeEfecto> propios) {
        return new Plan(nombre, nombre, TipoDeAccion.DEFENSA, SI_MISMO, true, potenciada, null,
                TURNOS_DE_RECARGA_EPICA, null, null, propios, 0, false);
    }

    private static Plan epicaDeApoyo(String nombre, boolean potenciada, Plan.Objetivo objetivo, int quitaPoder,
                                     List<PlantillaDeEfecto> propios, boolean vinculo) {
        return new Plan(nombre, nombre, TipoDeAccion.APOYO, objetivo, true, potenciada, null,
                TURNOS_DE_RECARGA_EPICA, null, null, propios, quitaPoder, vinculo);
    }
}

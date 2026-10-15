package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;
import nexus.combate.DistribucionEfectos;
import nexus.combate.IndiceNormal;
import nexus.combate.TablaEfectos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * El motor de combate: resuelve una accion, o el comienzo de un turno, con el
 * estado que le mandan — §6.1.1 a §6.1.4.
 *
 * <p><b>Una accion</b> ({@link #resolver}), en este orden:
 * <ol>
 *   <li>El reglamento dice que hace la accion y si el ejecutor puede usarla:
 *       es suya, la tiene aprendida en su nivel (RC-01), no es un sanador
 *       atacando, tiene la epica.</li>
 *   <li>La carga: una accion especial tiene UN turno de carga y una epica DOS
 *       (§6.1.2), contados en turnos propios del ejecutor.</li>
 *   <li>El poder: si no alcanza, «el valor de ataque se reduce a su valor
 *       base» (§6.1.1). La accion no se ejecuta y el turno se juega como la
 *       basica con la base de la formula.</li>
 *   <li>El objetivo: un rival en pie para un ataque, nunca un companero
 *       (§6.1.3); uno mismo o un companero para una sanacion.</li>
 *   <li>El golpe: tirada de ataque contra la defensa; si la supera, fila de la
 *       tabla de 8.000 con indice normal, porcentaje de la Tabla 22 sobre la
 *       tirada de DANO (§6.1.4). Si no la supera, «no se produce ningun
 *       efecto».</li>
 *   <li>El cierre del turno del ejecutor: paga el poder, entra en carga,
 *       gasta sus bonos propios y recibe los que deja la accion.</li>
 * </ol>
 *
 * <p><b>El comienzo de un turno</b> ({@link #iniciarTurno}): terminan las
 * protecciones del que empieza, actuan sus efectos por turno y recupera dos de
 * poder (§6.1.1).
 *
 * <p>Java puro, sin estado propio y sin Spring: cada llamada recibe su
 * generador. En partida real es {@code SecureRandom}; en las pruebas, una
 * semilla.
 */
public final class MotorDeAcciones {

    /** §6.1.1: «el poder se recupera ... cada dos (2) puntos por turno durante el combate». */
    public static final int PODER_POR_TURNO = 2;

    private final CatalogoDeCombate catalogo;
    private final IndiceNormal indice;
    private final Reglamento reglamento = new Reglamento();
    private final DificultadDeLaMaquina dificultad;

    public MotorDeAcciones(CatalogoDeCombate catalogo, IndiceNormal indice) {
        this(catalogo, indice, DificultadDeLaMaquina.NORMAL);
    }

    /**
     * @param dificultad como decide la maquina con {@code DECISION_DE_LA_MAQUINA}
     *                   (D-41, {@code MOTOR_IA_DIFICULTAD})
     */
    public MotorDeAcciones(CatalogoDeCombate catalogo, IndiceNormal indice, DificultadDeLaMaquina dificultad) {
        this.catalogo = Objects.requireNonNull(catalogo, "Sin catalogo de heroes no hay combate.");
        this.indice = Objects.requireNonNull(indice, "Sin indice no hay tabla de efectos.");
        this.dificultad = Objects.requireNonNull(dificultad, "La IA necesita su dificultad.");
    }

    public Reglamento reglamento() {
        return reglamento;
    }

    public DificultadDeLaMaquina dificultad() {
        return dificultad;
    }

    // =====================================================================
    // Una accion
    // =====================================================================

    public ResultadoDeAccion resolver(SolicitudDeAccion solicitud, RandomGenerator azar) {
        Objects.requireNonNull(azar, "Sin generador no hay combate.");
        if (solicitud.combatientes().size() < 2) {
            throw new IllegalArgumentException("Un combate necesita al menos dos combatientes.");
        }
        Mesa mesa = sentar(solicitud.combatientes());
        String idEjecutor = mesa.buscar(solicitud.ejecutor())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El ejecutor " + solicitud.ejecutor() + " no esta en la partida."))
                .id();
        if (!mesa.de(idEjecutor).enPie()) {
            throw new AccionNoPermitida(MotivoDeRechazo.EJECUTOR_CAIDO, "Sin vida no se actúa.");
        }
        boolean porEquipos = solicitud.porEquipos();

        String accion = solicitud.accion().trim();
        String objetivoPedido = solicitud.objetivo();
        if (Reglamento.DECISION_DE_LA_MAQUINA.equalsIgnoreCase(accion)) {
            // La maquina decide SIN el generador de la partida: ensaya con el
            // suyo (D-41). Asi no ve las tiradas que van a salir ni las gasta.
            PoliticaDeLaMaquina.Decision decision =
                    new PoliticaDeLaMaquina(this, dificultad).decidir(idEjecutor, mesa, porEquipos);
            accion = decision.accion();
            objetivoPedido = decision.objetivo();
        }

        Jugada jugada = jugar(mesa, idEjecutor, accion, objetivoPedido, porEquipos, azar);
        Plan plan = jugada.plan();
        Contendiente objetivo = jugada.objetivo();
        return new ResultadoDeAccion(
                accion, plan.codigo(), jugada.enValorBase(), idEjecutor, objetivo == null ? null : objetivo.id(),
                plan.tipo(), plan.esEpica(), plan.potenciada(), jugada.detalle(), mesa.afectados(), mesa.eventos(),
                mesa.todos(), accionesDe(mesa), recargasDe(mesa));
    }

    /** Lo que se jugo: el plan, si fue en valor base, contra quien y la tirada del golpe. */
    private record Jugada(Plan plan, boolean enValorBase, Contendiente objetivo, DetalleDeAtaque detalle) {
    }

    /**
     * Una accion sobre una mesa, de principio a fin. Es el UNICO camino: lo
     * recorren la accion de un jugador, la de la maquina y los ensayos con los
     * que la maquina decide ({@link #ensayar}). Por eso la maquina no puede
     * elegir una jugada que el reglamento no deje hacer.
     */
    private Jugada jugar(Mesa mesa, String idEjecutor, String accion, String objetivoPedido, boolean porEquipos,
                         RandomGenerator azar) {
        Contendiente ejecutor = mesa.de(idEjecutor);
        Plan pedido = reglamento.planPara(accion, ejecutor, mesa.ficha(idEjecutor));
        exigirQueNoEsteEnCarga(ejecutor, pedido);

        boolean enValorBase = !pedido.alcanzaCon(ejecutor.poder());
        Plan plan = enValorBase ? reglamento.planEnValorBase(ejecutor) : pedido;
        if (enValorBase) {
            mesa.evento(TipoDeEvento.VALOR_BASE, idEjecutor, null, pedido.nombre(), null);
        }

        Contendiente objetivo = elegirObjetivo(plan, ejecutor, objetivoPedido, mesa, porEquipos);

        DetalleDeAtaque detalle = null;
        if (plan.ataque() != null) {
            detalle = golpear(mesa, idEjecutor, objetivo.id(), plan, enValorBase, azar);
        }
        if (plan.sanacion() != null) {
            sanar(mesa, idEjecutor, objetivo, plan, enValorBase, porEquipos, azar);
        }
        if (plan.quitaPoderAlObjetivo() > 0) {
            mesa.quitarPoder(objetivo.id(), plan.quitaPoderAlObjetivo(), idEjecutor, plan.nombre());
        }
        if (plan.vinculo()) {
            mesa.aplicarEfecto(objetivo.id(), new EfectoActivo("REANIMADOR_3000", plan.nombre(),
                    TipoDeEfecto.VINCULO_REANIMACION, Mesa.PORCENTAJE_DE_REANIMACION_DEL_VINCULO, 0, idEjecutor));
        }
        cerrarTurnoDelEjecutor(mesa, idEjecutor, plan, enValorBase, azar);
        return new Jugada(plan, enValorBase, objetivo, detalle);
    }

    /**
     * Ensaya una jugada sobre una COPIA de la mesa con el generador de quien
     * ensaya: la mesa de la partida no cambia y su azar no se toca (D-41).
     *
     * @return la copia, con el estado y los eventos que dejaria la jugada
     * @throws AccionNoPermitida si la jugada no vale: el mismo reglamento que
     *                           una de verdad
     */
    Mesa ensayar(Mesa mesa, String idEjecutor, String accion, String objetivo, boolean porEquipos,
                 RandomGenerator azar) {
        Mesa copia = mesa.copia();
        jugar(copia, idEjecutor, accion, objetivo, porEquipos, azar);
        return copia;
    }

    private void exigirQueNoEsteEnCarga(Contendiente ejecutor, Plan plan) {
        if (plan.turnosDeCarga() <= 0) {
            return;
        }
        Integer ultimo = ejecutor.cargas().get(plan.codigo());
        int faltan = ultimo == null ? 0 : turnosQueFaltan(plan.turnosDeCarga(), ultimo, ejecutor.turnosJugados());
        if (faltan > 0) {
            throw new AccionNoPermitida(MotivoDeRechazo.EN_CARGA,
                    plan.nombre() + " está en carga: vuelve a estar disponible dentro de " + faltan
                            + (faltan == 1 ? " turno." : " turnos."));
        }
    }

    /**
     * Turnos propios que le faltan a una accion para volver a estar lista: se
     * uso en el turno {@code ultimo} (su valor de {@code turnosJugados}
     * entonces) y tiene {@code carga} turnos de carga. Con un turno de carga,
     * usada en el turno k, el k+1 carga y el k+2 ya se puede.
     */
    static int turnosQueFaltan(int carga, int ultimo, int turnosJugados) {
        return Math.max(0, carga + 1 - (turnosJugados - ultimo));
    }

    // =====================================================================
    // Objetivo
    // =====================================================================

    private static Contendiente elegirObjetivo(Plan plan, Contendiente ejecutor, String pedido, Mesa mesa,
                                               boolean porEquipos) {
        return switch (plan.objetivo()) {
            case SI_MISMO, GRUPO -> ejecutor;
            case RIVAL -> unoDe(mesa.todos().stream()
                            .filter(c -> c.enPie() && ejecutor.esRivalDe(c, porEquipos)).toList(),
                    pedido, mesa, "un rival en pie",
                    pedido != null && mesa.buscar(pedido).map(c -> ejecutor.esCompaneroDe(c, porEquipos)).orElse(false)
                            ? "Es de tu equipo: en el modo cooperativo no se ataca a un compañero."
                            : null);
            case ALIADO_O_SI_MISMO -> pedido == null ? ejecutor : unoDe(mesa.todos().stream()
                            .filter(c -> c.enPie() && (c.id().equals(ejecutor.id())
                                    || ejecutor.esCompaneroDe(c, porEquipos))).toList(),
                    pedido, mesa, "tú mismo o un compañero en pie", null);
            case COMPANERO -> unoDe(mesa.todos().stream()
                            .filter(c -> c.enPie() && ejecutor.esCompaneroDe(c, porEquipos)).toList(),
                    pedido, mesa, "un compañero en pie", null);
            case COMPANERO_CAIDO_O_VIVO -> unoDe(mesa.todos().stream()
                            .filter(c -> ejecutor.esCompaneroDe(c, porEquipos)).toList(),
                    pedido, mesa, "un compañero", null);
        };
    }

    /**
     * El objetivo pedido, si esta entre los validos; si no se pidio ninguno y
     * solo hay uno posible, ese. Elegir entre varios seria decidir la jugada
     * del jugador, y eso no lo hace el servidor.
     */
    private static Contendiente unoDe(List<Contendiente> validos, String pedido, Mesa mesa, String queSeEspera,
                                      String motivoSiEsCompanero) {
        if (pedido != null && !pedido.isBlank()) {
            return validos.stream().filter(c -> c.id().equals(pedido)).findFirst()
                    .orElseThrow(() -> new AccionNoPermitida(MotivoDeRechazo.OBJETIVO_INVALIDO,
                            motivoSiEsCompanero != null ? motivoSiEsCompanero
                                    : mesa.buscar(pedido).isEmpty()
                                    ? "Ese objetivo no está en la partida."
                                    : "Ese objetivo no vale para esta acción: tiene que ser " + queSeEspera + "."));
        }
        if (validos.isEmpty()) {
            throw new AccionNoPermitida(MotivoDeRechazo.OBJETIVO_INVALIDO,
                    "No hay a quién dirigir esta acción: hace falta " + queSeEspera + ".");
        }
        if (validos.size() > 1) {
            throw new AccionNoPermitida(MotivoDeRechazo.OBJETIVO_REQUERIDO,
                    "Hay más de un objetivo posible: indica a quién.");
        }
        return validos.get(0);
    }

    // =====================================================================
    // Golpe
    // =====================================================================

    private DetalleDeAtaque golpear(Mesa mesa, String idAtacante, String idDefensor, Plan plan,
                                    boolean enValorBase, RandomGenerator azar) {
        Contendiente atacante = mesa.de(idAtacante);
        Contendiente defensor = mesa.de(idDefensor);
        FichaDeCombate fichaAtacante = mesa.ficha(idAtacante);
        EquipoDeCombate.EfectosDeEquipo equipoAtacante = EquipoDeCombate.de(atacante.equipamiento());
        EquipoDeCombate.EfectosDeEquipo equipoDefensor = EquipoDeCombate.de(defensor.equipamiento());
        Estadisticas propias = atacante.estadisticasResueltas();
        Plan.Ataque golpe = plan.ataque();

        int ataque = enValorBase
                ? propias.ataque().base()
                : propias.ataque().tirar(azar) + golpe.bonoAtaque().tirar(azar)
                  + atacante.sumaDe(TipoDeEfecto.BONO_ATAQUE) - atacante.sumaDe(TipoDeEfecto.PENALIZA_ATAQUE)
                  - equipoDefensor.restaAtaqueDelAtacante();
        ataque = Math.max(0, ataque);
        int defensa = defensor.estadisticasResueltas().defensa() + defensor.sumaDe(TipoDeEfecto.BONO_DEFENSA);

        if (ataque <= defensa) {
            // §6.1.4: «si el ataque no logra superar la defensa del enemigo, no se
            // produce ningun efecto». Salvo los Pinchos de escudo del defensor,
            // que castigan precisamente eso (Tabla 16: «menor que la defensa»).
            if (equipoDefensor.pinchos() > 0 && ataque < defensa) {
                mesa.danar(idAtacante, equipoDefensor.pinchos(), idDefensor, "Pinchos de escudo",
                        TipoDeEvento.REFLEJO);
            }
            return new DetalleDeAtaque(idDefensor, ataque, defensa, false, CategoriaEfecto.SIN_EFECTO,
                    null, 0, 0, 0);
        }

        DistribucionEfectos reparto = DistribucionEfectos.dePrototipo(atacante.prototipo())
                .orElseThrow(() -> new AccionNoPermitida(MotivoDeRechazo.SIN_TABLA_DE_EFECTOS,
                        atacante.prototipo() + " no tiene fila en la Tabla 21."))
                .ajustarCritico(equipoAtacante.critico() + golpe.bonoCritico()
                        + atacante.sumaDe(TipoDeEfecto.BONO_CRITICO) - equipoDefensor.restaCriticoDelAtacante());
        int fila = indice.generar(azar);
        CategoriaEfecto categoria = TablaEfectos.desde(reparto).efectoEn(fila);
        int porcentaje = categoria == CategoriaEfecto.CAUSAR_DANO_CRITICO
                ? categoria.porcentajeDanoMinimo()
                  + azar.nextInt(categoria.porcentajeDanoMaximo() - categoria.porcentajeDanoMinimo() + 1)
                : categoria.porcentajeDanoMinimo();

        int danoBase = propias.dano().tirar(azar)
                + (enValorBase ? 0 : golpe.bonoDano().tirar(azar))
                + atacante.sumaDe(TipoDeEfecto.BONO_DANO) - atacante.sumaDe(TipoDeEfecto.PENALIZA_DANO)
                - equipoDefensor.restaDanoDelAtacante();
        if (!enValorBase && golpe.retornaDanoRecibido()) {
            DanoRecibido ultimo = atacante.ultimoDanoRecibido();
            if (ultimo != null && ultimo.de().equals(idDefensor)) {
                // «(0dx)»: de cero a lo que el objetivo le hizo en su ultimo golpe (D-B7-03).
                danoBase += azar.nextInt(ultimo.cantidad() + 1);
            }
        }
        danoBase = Math.max(0, danoBase);
        int dano = danoBase * porcentaje / 100;

        int recibido = protegido(mesa, defensor, fichaAtacante.danoMagico(), dano, idAtacante);
        int retornado = 0;
        if (recibido > 0 && defensor.tiene(TipoDeEfecto.REFLEJA_MITAD)) {
            // Toma y lleva: «Disminuye a la mitad del dano causado por el oponente y se lo retorna».
            int mitad = recibido / 2;
            retornado = recibido - mitad;
            recibido = mitad;
        }
        int perdido = mesa.danar(idDefensor, recibido, idAtacante, plan.nombre(), TipoDeEvento.DANO);
        if (retornado > 0) {
            mesa.danar(idAtacante, retornado, idDefensor, "Toma y lleva", TipoDeEvento.REFLEJO);
        }

        if (categoria != CategoriaEfecto.SIN_EFECTO) {
            // Velo de Sombras (7.8.14): quien golpea al intangible queda envenenado.
            for (EfectoActivo velo : defensor.efectos()) {
                if (velo.tipo() == TipoDeEfecto.ENVENENA_AL_ATACANTE) {
                    mesa.aplicarEfecto(idAtacante, new EfectoActivo("VELO_DE_SOMBRAS", velo.nombre(),
                            TipoDeEfecto.DANO_POR_TURNO, velo.valor(), velo.turnos(), idDefensor));
                }
            }
            for (PlantillaDeEfecto efecto : golpe.alAcertar()) {
                mesa.aplicarEfecto(idDefensor, efecto.crear(azar, idAtacante));
            }
            for (PlantillaDeEfecto efecto : equipoAtacante.alAcertar()) {
                mesa.aplicarEfecto(idDefensor, efecto.crear(azar, idAtacante));
            }
            for (PlantillaDeEfecto efecto : equipoAtacante.propiosAlAcertar()) {
                mesa.aplicarEfecto(idAtacante, efecto.crear(azar, idAtacante));
            }
            // Veneno lacerante: «-1 al poder de oponente. Solo aplica cada dos
            // turnos» — en los turnos pares del portador (D-B7-10).
            if (equipoAtacante.venenoLacerante() && (atacante.turnosJugados() + 1) % 2 == 0) {
                mesa.quitarPoder(idDefensor, 1, idAtacante, "Veneno lacerante");
            }
        }
        return new DetalleDeAtaque(idDefensor, ataque, defensa, true, categoria, fila, porcentaje, danoBase, perdido);
    }

    /** Lo que queda del dano tras las protecciones del defensor (Defensa feroz, Frio concentrado). */
    private static int protegido(Mesa mesa, Contendiente defensor, boolean danoMagico, int dano, String origen) {
        if (dano <= 0) {
            return 0;
        }
        if (defensor.tiene(TipoDeEfecto.INMUNE_TOTAL)) {
            mesa.evento(TipoDeEvento.PROTEGIDO, defensor.id(), origen, nombreDe(defensor, TipoDeEfecto.INMUNE_TOTAL), dano);
            return 0;
        }
        if (!danoMagico && defensor.tiene(TipoDeEfecto.INMUNE_FISICO)) {
            mesa.evento(TipoDeEvento.PROTEGIDO, defensor.id(), origen, nombreDe(defensor, TipoDeEfecto.INMUNE_FISICO), dano);
            return 0;
        }
        if (danoMagico && defensor.tiene(TipoDeEfecto.REDUCE_MAGICO)) {
            int reduccion = Math.min(dano, defensor.sumaDe(TipoDeEfecto.REDUCE_MAGICO));
            if (reduccion > 0) {
                mesa.evento(TipoDeEvento.PROTEGIDO, defensor.id(), origen,
                        nombreDe(defensor, TipoDeEfecto.REDUCE_MAGICO), reduccion);
            }
            return dano - reduccion;
        }
        return dano;
    }

    private static String nombreDe(Contendiente c, TipoDeEfecto tipo) {
        return c.efectos().stream().filter(e -> e.tipo() == tipo).map(EfectoActivo::nombre).findFirst().orElse(null);
    }

    // =====================================================================
    // Sanacion
    // =====================================================================

    private static void sanar(Mesa mesa, String idSanador, Contendiente objetivo, Plan plan, boolean enValorBase,
                              boolean porEquipos, RandomGenerator azar) {
        Plan.Sanacion sanacion = plan.sanacion();
        Contendiente sanador = mesa.de(idSanador);
        EquipoDeCombate.EfectosDeEquipo equipo = EquipoDeCombate.de(sanador.equipamiento());

        List<String> destinos = switch (sanacion.destino()) {
            case OBJETIVO -> List.of(objetivo.id());
            case SI_MISMO -> List.of(idSanador);
            case GRUPO -> mesa.todos().stream()
                    .filter(c -> c.enPie() && (c.id().equals(idSanador) || sanador.esCompaneroDe(c, porEquipos)))
                    .map(Contendiente::id)
                    .toList();
        };

        for (String destino : destinos) {
            if (sanacion.fuente() == Plan.Sanacion.Fuente.VIDA_COMPLETA) {
                mesa.restaurarPorCompleto(destino, idSanador, plan.nombre());
                continue;
            }
            int cantidad;
            if (sanacion.fuente() == Plan.Sanacion.Fuente.FORMULA_SANAR) {
                Formula formula = sanador.estadisticasResueltas().sanar();
                cantidad = enValorBase
                        ? formula.base()
                        : formula.tirar(azar) + sanacion.cantidad().tirar(azar)
                          + sanador.sumaDe(TipoDeEfecto.BONO_SANACION);
            } else {
                cantidad = sanacion.cantidad().tirar(azar);
            }
            // Pluma sanadora: «Mejora la sanacion multiplicando x2» (Tabla 19).
            cantidad *= equipo.multiplicadorDeSanacion();
            mesa.sanar(destino, cantidad, idSanador, plan.nombre(), TipoDeEvento.SANACION);
            for (PlantillaDeEfecto efecto : sanacion.porTurno()) {
                mesa.aplicarEfecto(destino, efecto.crear(azar, idSanador));
            }
            if (sanacion.fuente() == Plan.Sanacion.Fuente.FORMULA_SANAR && !enValorBase) {
                // Yerbabuena y Benditas: la sanacion del portador deja sanacion por turno.
                for (PlantillaDeEfecto efecto : equipo.alSanar()) {
                    mesa.aplicarEfecto(destino, efecto.crear(azar, idSanador));
                }
            }
        }
    }

    // =====================================================================
    // Cierre del turno del ejecutor
    // =====================================================================

    private static void cerrarTurnoDelEjecutor(Mesa mesa, String id, Plan plan, boolean enValorBase,
                                               RandomGenerator azar) {
        Contendiente ejecutor = mesa.de(id);
        if (!enValorBase) {
            ejecutor = ejecutor.conPoder(plan.poderTrasPagar(ejecutor.poder()));
            if (plan.turnosDeCarga() > 0) {
                ejecutor = ejecutor.conCarga(plan.codigo(), ejecutor.turnosJugados());
            }
        }
        // Los bonos PROPIOS que traia se gastan con esta accion.
        List<EfectoActivo> quedan = new ArrayList<>();
        for (EfectoActivo efecto : ejecutor.efectos()) {
            if (efecto.tipo().familia() != TipoDeEfecto.Familia.PROPIO) {
                quedan.add(efecto);
                continue;
            }
            EfectoActivo descontado = efecto.descontado();
            if (descontado.turnos() > 0) {
                quedan.add(descontado);
            } else {
                mesa.evento(TipoDeEvento.EFECTO_TERMINADO, id, efecto.origen(), efecto.nombre(), null);
            }
        }
        ejecutor = ejecutor.conEfectos(quedan);
        mesa.poner(ejecutor);
        // Los que deja esta accion, para los turnos siguientes.
        if (!enValorBase) {
            for (PlantillaDeEfecto efecto : plan.efectosPropios()) {
                mesa.aplicarEfecto(id, efecto.crear(azar, id));
            }
        }
        Contendiente cerrado = mesa.de(id);
        mesa.poner(cerrado.conTurnosJugados(cerrado.turnosJugados() + 1));
    }

    // =====================================================================
    // Comienzo de turno
    // =====================================================================

    public ResultadoDeTurno iniciarTurno(SolicitudDeTurno solicitud, RandomGenerator azar) {
        Objects.requireNonNull(azar, "Sin generador no hay combate.");
        if (solicitud.combatientes().isEmpty()) {
            throw new IllegalArgumentException("Hace falta al menos el combatiente que empieza.");
        }
        Mesa mesa = sentar(solicitud.combatientes());
        String id = mesa.buscar(solicitud.combatiente())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El combatiente " + solicitud.combatiente() + " no esta en la partida."))
                .id();

        if (mesa.de(id).enPie()) {
            terminarProtecciones(mesa, id);
            aplicarEfectosPorTurno(mesa, id);
            Contendiente despues = mesa.de(id);
            if (despues.enPie()) {
                int antes = despues.poder();
                Contendiente recuperado = despues.conPoder(antes + PODER_POR_TURNO);
                mesa.poner(recuperado);
                if (recuperado.poder() > antes) {
                    mesa.evento(TipoDeEvento.PODER_RECUPERADO, id, null, null, recuperado.poder() - antes);
                }
            }
        }
        return new ResultadoDeTurno(id, mesa.afectados(), mesa.eventos(), mesa.todos(),
                accionesDe(mesa), recargasDe(mesa));
    }

    private static void terminarProtecciones(Mesa mesa, String id) {
        Contendiente c = mesa.de(id);
        List<EfectoActivo> quedan = new ArrayList<>();
        for (EfectoActivo efecto : c.efectos()) {
            if (efecto.hastaSuTurno()) {
                mesa.evento(TipoDeEvento.EFECTO_TERMINADO, id, efecto.origen(), efecto.nombre(), null);
            } else {
                quedan.add(efecto);
            }
        }
        mesa.poner(c.conEfectos(quedan));
    }

    private static void aplicarEfectosPorTurno(Mesa mesa, String id) {
        for (EfectoActivo efecto : List.copyOf(mesa.de(id).efectos())) {
            if (efecto.tipo().familia() != TipoDeEfecto.Familia.POR_TURNO) {
                continue;
            }
            if (efecto.tipo() == TipoDeEfecto.DANO_POR_TURNO) {
                mesa.danar(id, efecto.valor(), efecto.origen(), efecto.nombre(), TipoDeEvento.DANO_POR_TURNO);
            } else {
                mesa.sanar(id, efecto.valor(), efecto.origen(), efecto.nombre(), TipoDeEvento.SANACION_POR_TURNO);
            }
            Contendiente c = mesa.de(id);
            List<EfectoActivo> actualizados = new ArrayList<>();
            for (EfectoActivo e : c.efectos()) {
                if (e == efecto || e.equals(efecto)) {
                    EfectoActivo descontado = e.descontado();
                    if (descontado.turnos() > 0) {
                        actualizados.add(descontado);
                    } else {
                        mesa.evento(TipoDeEvento.EFECTO_TERMINADO, id, e.origen(), e.nombre(), null);
                    }
                } else {
                    actualizados.add(e);
                }
            }
            mesa.poner(c.conEfectos(actualizados));
        }
    }

    // =====================================================================
    // Mesa, acciones disponibles y recargas
    // =====================================================================

    /** Sienta a todos con sus estadisticas resueltas y la vida y el poder acotados. */
    private Mesa sentar(List<Contendiente> combatientes) {
        Mesa mesa = new Mesa();
        for (Contendiente c : combatientes) {
            FichaDeCombate ficha = catalogo.ficha(c.prototipo(), c.nivel());
            Estadisticas estadisticas = c.estadisticas() != null ? c.estadisticas() : ficha.estadisticas();
            Contendiente sentado = c.conEstadisticas(estadisticas);
            sentado = sentado.conVida(sentado.vida()).conPoder(sentado.poder());
            mesa.sentar(sentado, ficha);
        }
        return mesa;
    }

    private Map<String, List<EstadoDeAccion>> accionesDe(Mesa mesa) {
        Map<String, List<EstadoDeAccion>> porCombatiente = new LinkedHashMap<>();
        for (Contendiente c : mesa.todos()) {
            porCombatiente.put(c.id(), accionesDe(c, mesa.ficha(c.id())));
        }
        return porCombatiente;
    }

    /** Lo que puede jugar un combatiente ahora mismo, y por que no lo demas. */
    List<EstadoDeAccion> accionesDe(Contendiente c, FichaDeCombate ficha) {
        List<EstadoDeAccion> lista = new ArrayList<>();
        Estadisticas estadisticas = c.estadisticasResueltas();
        String sinVida = c.enPie() ? null : "Sin vida no se actúa.";
        if (estadisticas.ataca()) {
            lista.add(new EstadoDeAccion(Reglamento.ATAQUE_BASICO, "Ataque básico", TipoDeAccion.ATAQUE, false,
                    0, false, 0, 1, sinVida == null, sinVida));
        }
        if (estadisticas.sana()) {
            lista.add(new EstadoDeAccion(Reglamento.SANACION_BASICA, "Sanación básica", TipoDeAccion.SANACION,
                    false, 0, false, 0, 1, sinVida == null, sinVida));
        }
        for (AccionDelCatalogo accion : ficha.acciones()) {
            TipoDeAccion tipo = reglamento.tipoDe(accion).orElse(null);
            int carga = accion.turnosDeCarga() > 0 ? accion.turnosDeCarga() : Reglamento.TURNOS_DE_CARGA_ACCION;
            String motivo = sinVida;
            if (motivo == null && tipo == null) {
                motivo = "El motor de combate no tiene regla para esta acción.";
            }
            if (motivo == null && c.nivel() < accion.nivelRequerido()) {
                motivo = "Se aprende en el nivel " + accion.nivelRequerido() + ".";
            }
            if (motivo == null) {
                motivo = motivoDeCarga(c, accion.nombre(), carga);
            }
            if (motivo == null && !accion.alcanzaCon(c.poder())) {
                motivo = accion.todoElPoder()
                        ? "Cuesta todo tu poder y no te queda."
                        : "Poder insuficiente: cuesta " + accion.costoPoder() + " y tienes " + c.poder() + ".";
            }
            lista.add(new EstadoDeAccion(accion.nombre(), accion.nombre(), tipo, false, accion.costoPoder(),
                    accion.todoElPoder(), carga, accion.nivelRequerido(), motivo == null, motivo));
        }
        for (String nombre : c.epicas()) {
            reglamento.epica(nombre).ifPresent(epica -> {
                boolean afin = Nombres.normalizar(epica.afin()).equals(Nombres.normalizar(c.prototipo()));
                Plan plan = afin ? epica.potenciada() : epica.general();
                String motivo = sinVida;
                if (motivo == null && plan == null) {
                    motivo = "No tiene efecto para tu héroe (Tabla 20: «No aplica»).";
                }
                if (motivo == null && plan.ataque() != null && !estadisticas.ataca()) {
                    motivo = "Un sanador no puede infligir daño.";
                }
                if (motivo == null) {
                    motivo = motivoDeCarga(c, epica.nombre(), Reglamento.TURNOS_DE_RECARGA_EPICA);
                }
                lista.add(new EstadoDeAccion(epica.nombre(), epica.nombre(), plan == null ? null : plan.tipo(),
                        true, 0, false, Reglamento.TURNOS_DE_RECARGA_EPICA, 1, motivo == null, motivo));
            });
        }
        return lista;
    }

    private static String motivoDeCarga(Contendiente c, String codigo, int carga) {
        Integer ultimo = c.cargas().get(codigo);
        if (ultimo == null) {
            return null;
        }
        int faltan = turnosQueFaltan(carga, ultimo, c.turnosJugados());
        return faltan > 0 ? "En carga: " + faltan + (faltan == 1 ? " turno." : " turnos.") : null;
    }

    private Map<String, Map<String, Integer>> recargasDe(Mesa mesa) {
        Map<String, Map<String, Integer>> porCombatiente = new LinkedHashMap<>();
        for (Contendiente c : mesa.todos()) {
            Map<String, Integer> faltan = new LinkedHashMap<>();
            FichaDeCombate ficha = mesa.ficha(c.id());
            c.cargas().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(carga -> {
                        int turnosDeCarga = reglamento.epica(carga.getKey()).isPresent()
                                ? Reglamento.TURNOS_DE_RECARGA_EPICA
                                : ficha.accion(carga.getKey()).map(AccionDelCatalogo::turnosDeCarga)
                                .filter(t -> t > 0).orElse(Reglamento.TURNOS_DE_CARGA_ACCION);
                        int restan = turnosQueFaltan(turnosDeCarga, carga.getValue(), c.turnosJugados());
                        if (restan > 0) {
                            faltan.put(carga.getKey(), restan);
                        }
                    });
            porCombatiente.put(c.id(), faltan);
        }
        return porCombatiente;
    }
}

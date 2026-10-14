package nexus.misiones.dominio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nexus.misiones.dominio.simulacion.Azar;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;

/**
 * De un resultado a lo que el jugador gana (seccion 7.8.3, «sistema de
 * recompensas», y 7.8.8, «recompensas obtenidas» y «objetivos cumplidos»).
 *
 * <ul>
 *   <li><b>Creditos</b>: los base «por completar la mision», multiplicados por
 *       el escalon; mas los de primera vez, una sola vez (HU-MIS-003); mas las
 *       bonificaciones de los objetivos cumplidos (HU-MIS-007). Una mision
 *       fallida no da creditos.</li>
 *   <li><b>Botin</b>: lo garantizado y lo potencial, sorteado unidad por
 *       unidad, solo al completar (HU-MIS-007).</li>
 *   <li><b>Epicas</b>: la del Master derrotado (7.8.4, «al ser derrotados,
 *       otorgan su habilidad epica»). Si el PO decide lo de HU-MIS-007
 *       («si lo derrota y completa la mision»), solo al completar.</li>
 *   <li><b>Experiencia</b>: la de los enemigos derrotados (6.1.1), tambien en
 *       una mision fallida porque esos enemigos si cayeron, mas la de completar
 *       segun la dificultad.</li>
 * </ul>
 *
 * <p>Lo que el documento da y no existe en ningun servicio (el Cofre de
 * Bronce, los objetos propios del ejemplo, los titulos) no se inventa: va a
 * {@code sinEntregar} con su motivo, y el reporte lo dice.
 */
public final class CalculadoraDeRecompensas {

    static final String NO_ESTA_EN_EL_CATALOGO =
            "No está en el catálogo oficial de productos: no se puede entregar al inventario.";
    static final String SIN_SERVICIO_DE_TITULOS =
            "Todavía no hay títulos ni insignias en el juego (sección 7.8.11): queda anotado en el reporte.";
    static final String EPICA_SOLO_EN_LA_COLECCION =
            "Queda en tu colección de épicas de Máster; no está en el catálogo oficial, así que no llega al inventario.";

    private CalculadoraDeRecompensas() {
    }

    public static RecompensasDeEjecucion calcular(Mision mision, Escalon escalon, ResultadoDeMision resultado,
                                                  boolean primeraVez, ParametrosDeRecompensa parametros,
                                                  Azar azar) {
        boolean exito = resultado.exito();
        RecompensasDeMision tabla = mision.recompensas();

        Map<String, Integer> botinGanado = new LinkedHashMap<>();
        List<RecompensasDeEjecucion.ObjetoGanado> productos = new ArrayList<>();
        List<RecompensasDeEjecucion.SinEntregar> sinEntregar = new ArrayList<>();
        if (exito) {
            sortearBotin(tabla, azar, botinGanado, productos, sinEntregar);
        }

        List<RecompensasDeEjecucion.ObjetivoEvaluado> objetivos = new ArrayList<>();
        int bonificaciones = 0;
        for (int i = 0; i < mision.objetivos().size(); i++) {
            Objetivo objetivo = mision.objetivos().get(i);
            boolean cumplido = cumplido(objetivo, resultado, botinGanado);
            int bono = 0;
            if (cumplido) {
                bono = bonificacionDe(tabla, i);
                bonificaciones += bono;
            }
            objetivos.add(new RecompensasDeEjecucion.ObjetivoEvaluado(objetivo.texto(), cumplido,
                    bono > 0 ? "+" + bono + " créditos" : null));
        }

        int creditos = 0;
        if (exito) {
            creditos = (int) Math.round(tabla.creditos() * parametros.multiplicadorDeCreditos(escalon))
                    + bonificaciones;
            if (primeraVez) {
                creditos += tabla.primeraVez().creditos();
                for (String otra : tabla.primeraVez().otras()) {
                    sinEntregar.add(new RecompensasDeEjecucion.SinEntregar(otra, SIN_SERVICIO_DE_TITULOS));
                }
            }
        }

        List<RecompensasDeEjecucion.EpicaGanada> epicas = new ArrayList<>();
        if (exito || !parametros.epicaExigeCompletar()) {
            // Una epica se aprende una vez: en una exploracion larga el mismo Master puede caer dos veces.
            Set<String> yaGanadas = new HashSet<>();
            for (ResultadoDeMision.MasterEnfrentado master : resultado.masters()) {
                if (!master.derrotado() || !yaGanadas.add(master.epica().nombre())) {
                    continue;
                }
                Epica epica = master.epica();
                epicas.add(new RecompensasDeEjecucion.EpicaGanada(epica.nombre(), master.nombre(),
                        epica.entregable() ? epica.productoId() : null));
                if (!epica.entregable()) {
                    sinEntregar.add(new RecompensasDeEjecucion.SinEntregar(
                            "Épica «" + epica.nombre() + "»", EPICA_SOLO_EN_LA_COLECCION));
                }
            }
        }

        double experiencia = resultado.experiencia()
                + (exito ? parametros.experienciaPorCompletar(mision.dificultad()) : 0);

        return new RecompensasDeEjecucion(creditos, productos, epicas, experiencia, sinEntregar, objetivos,
                exito && primeraVez);
    }

    private static void sortearBotin(RecompensasDeMision tabla, Azar azar, Map<String, Integer> botinGanado,
                                     List<RecompensasDeEjecucion.ObjetoGanado> productos,
                                     List<RecompensasDeEjecucion.SinEntregar> sinEntregar) {
        for (RecompensasDeMision.ObjetoDeRecompensa objeto : tabla.garantizadas()) {
            anotar(objeto.nombre(), objeto.productoId(), objeto.cantidad(), botinGanado, productos, sinEntregar);
        }
        for (RecompensasDeMision.ObjetoPotencial botin : tabla.potenciales()) {
            int unidades = 0;
            for (int unidad = 0; unidad < botin.cantidad(); unidad++) {
                if (azar.acierta(botin.probabilidad())) {
                    unidades++;
                }
            }
            if (unidades > 0) {
                anotar(botin.nombre(), botin.productoId(), unidades, botinGanado, productos, sinEntregar);
            }
        }
    }

    private static void anotar(String nombre, String productoId, int cantidad, Map<String, Integer> botinGanado,
                               List<RecompensasDeEjecucion.ObjetoGanado> productos,
                               List<RecompensasDeEjecucion.SinEntregar> sinEntregar) {
        botinGanado.merge(nombre, cantidad, Integer::sum);
        if (productoId != null && !productoId.isBlank()) {
            productos.add(new RecompensasDeEjecucion.ObjetoGanado(nombre, productoId, cantidad));
        } else {
            sinEntregar.add(new RecompensasDeEjecucion.SinEntregar(cantidad + " " + nombre, NO_ESTA_EN_EL_CATALOGO));
        }
    }

    private static boolean cumplido(Objetivo objetivo, ResultadoDeMision resultado, Map<String, Integer> botin) {
        return switch (objetivo.tipo()) {
            case DERROTAR_JEFE -> resultado.jefeDerrotado();
            case COMPLETAR_ENCUENTROS -> resultado.encuentrosRegularesCompletos();
            // «Completar la mision sin que la vida del heroe baje del 50%»: sin
            // completar no se cumple, aunque la vida no bajara.
            case VIDA_MINIMA -> resultado.exito() && resultado.vidaMinimaPorcentaje() >= objetivo.valor();
            case DERROTAR_MASTER -> resultado.derrotoAlgunMaster();
            case OBTENER_BOTIN -> botin.getOrDefault(objetivo.botin(), 0) >= objetivo.valor();
        };
    }

    private static int bonificacionDe(RecompensasDeMision tabla, int objetivo) {
        return tabla.porObjetivos().stream()
                .filter(b -> b.objetivo() == objetivo)
                .mapToInt(RecompensasDeMision.BonificacionPorObjetivo::creditos)
                .sum();
    }
}

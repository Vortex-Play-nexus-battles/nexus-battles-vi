package nexus.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * HU-SIM-002: la logica de decision de la IA por turno, seccion 7.8.5, p. 61
 * (RF-MIS-13 a RF-MIS-16). En cada turno se verifica la Rotacion 1 (poder
 * suficiente, cooldown completado, estado de salud del heroe); si no es
 * viable, la 2; luego la 3; si ninguna, "ataque basico sin consumir poder".
 * El ciclo empieza siempre por la Rotacion 1.
 *
 * Decision de implementacion: una rotacion es una secuencia con un cursor;
 * en cada turno se evalua el paso que le toca a cada rotacion en orden de
 * prioridad y solo avanza el cursor de la rotacion ejecutada (da la vuelta al
 * terminar). "Estado de salud" no tiene umbral en ninguna fuente (pendiente
 * P-E): hipotesis de trabajo, el heroe debe tener vida para actuar.
 */
public final class DecisorDeRotaciones {

    private DecisorDeRotaciones() {
    }

    public static Decision decidir(EstrategiaDeCombate estrategia, EstadoEnTurno estado) {
        if (estado.vida() <= 0) {
            throw new IllegalArgumentException("Un héroe sin vida no actúa.");
        }
        Heroe heroe = estrategia.heroe();
        EstadoDePoder poder = new EstadoDePoder(estado.poder(), heroe.estadisticasActuales().poder());

        List<Rotacion> rotaciones = estrategia.rotaciones();
        List<Integer> cursores = new ArrayList<>();
        for (int i = 0; i < rotaciones.size(); i++) {
            cursores.add(estado.cursorDe(i));
        }
        List<Decision.Evaluacion> evaluaciones = new ArrayList<>();

        for (int i = 0; i < rotaciones.size(); i++) {
            Rotacion rotacion = rotaciones.get(i);
            String paso = rotacion.pasos().get(cursores.get(i) % rotacion.pasos().size());
            String razon = motivoDeInviabilidad(heroe, paso, poder, estado);
            evaluaciones.add(new Decision.Evaluacion(i + 1, paso, razon == null, razon));
            if (razon == null) {
                cursores.set(i, cursores.get(i) + 1);
                return new Decision(paso, i + 1, costoDe(heroe, paso, poder), evaluaciones, cursores);
            }
        }
        // RF-MIS-15: ninguna rotacion viable, ataque basico sin consumir poder.
        return new Decision(EstrategiaDeCombate.ATAQUE_BASICO, null, 0, evaluaciones, cursores);
    }

    /** null cuando el paso es viable en este turno. */
    private static String motivoDeInviabilidad(Heroe heroe, String paso, EstadoDePoder poder, EstadoEnTurno estado) {
        if (EstrategiaDeCombate.ATAQUE_BASICO.equals(paso)) {
            return null;
        }
        Accion accion = accionDe(heroe, paso);
        if (!poder.usar(accion).ejecutada()) {
            String costo = accion.cuestaTodoElPoder() ? "todos los puntos de poder" : String.valueOf(accion.costoPuntos());
            return "Poder insuficiente: " + paso + " cuesta " + costo + " y el héroe tiene " + poder.actual() + ".";
        }
        Integer ultimoUso = estado.turnoDeUltimoUso().get(paso);
        if (ultimoUso != null) {
            ControlDeRecarga recarga = ControlDeRecarga.paraAccionEspecial();
            recarga.registrarUso(ultimoUso);
            if (!recarga.disponibleEn(estado.turno())) {
                int disponibleEn = estado.turno() + recarga.turnosRestantesEn(estado.turno());
                return "En recarga: " + paso + " se usó en el turno " + ultimoUso
                        + " y vuelve a estar disponible en el turno " + disponibleEn + ".";
            }
        }
        return null;
    }

    private static int costoDe(Heroe heroe, String paso, EstadoDePoder poder) {
        if (EstrategiaDeCombate.ATAQUE_BASICO.equals(paso)) {
            return 0;
        }
        Accion accion = accionDe(heroe, paso);
        return accion.cuestaTodoElPoder() ? poder.actual() : accion.costoPuntos();
    }

    private static Accion accionDe(Heroe heroe, String nombre) {
        return heroe.prototipo().acciones().stream()
                .filter(a -> a.nombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La rotación usa una habilidad que no es de " + heroe.prototipo().nombre() + ": " + nombre + "."));
    }
}

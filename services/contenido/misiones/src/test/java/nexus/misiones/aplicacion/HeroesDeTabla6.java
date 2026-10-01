package nexus.misiones.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;

/**
 * Heroes con las estadisticas reales de la Tabla 6 del documento y la regla del nivel como factor multiplicador
 * (6.1.1: «un mago de fuego de nivel 3 posee un ataque base de 30»): poder, vida y defensa se multiplican por el nivel,
 * la base del ataque tambien y los dados no se escalan. Como el servicio de verdad, solo conoce los niveles 1 a 8 y
 * rechaza cualquier otro: una prueba que pida el nivel 10 se entera. Recuerda cada consulta para que se pueda decir
 * QUE nivel se le pidio por cada prototipo. Lo demas (decidir, validar, experiencia) lo hace el doble de siempre.
 */
final class HeroesDeTabla6 implements ServicioDeHeroes {

    /** Poder, vida, defensa, caras del dado de ataque (base 10) y caras del dado de dano; 0 = sin ataque. */
    private record Fila(int poder, int vida, int defensa, int carasAtaque, int carasDano) {
    }

    private static final Map<String, Fila> TABLA_6 = Map.of(
            "Guerrero Tanque", new Fila(10, 44, 11, 6, 4),
            "Guerrero Armas", new Fila(8, 44, 11, 6, 6),
            "Mago Fuego", new Fila(8, 40, 10, 8, 8),
            "Mago Hielo", new Fila(10, 40, 10, 8, 6),
            "Pícaro Veneno", new Fila(8, 36, 8, 10, 6),
            "Pícaro Machete", new Fila(8, 36, 8, 10, 8),
            "Chamán", new Fila(10, 28, 4, 0, 0),
            "Médico", new Fila(10, 28, 4, 0, 0));

    final Dobles.Heroes base = new Dobles.Heroes();
    /** Cada consulta de estadisticas por nivel: {@code prototipo@nivel}, en orden. */
    final List<String> consultas = new ArrayList<>();

    @Override
    public EstadisticasDeNivel enNivel(String prototipo, int nivel) {
        consultas.add(prototipo + "@" + nivel);
        if (nivel < 1 || nivel > 8) {
            throw new IllegalArgumentException("El nivel de un héroe está entre 1 y 8: pidieron " + nivel + ".");
        }
        Fila f = TABLA_6.get(prototipo);
        if (f.carasAtaque() == 0) {
            return new EstadisticasDeNivel(f.poder() * nivel, f.vida() * nivel, f.defensa() * nivel, null, null,
                    new Formula(6 * nivel, 1, 6));
        }
        return new EstadisticasDeNivel(f.poder() * nivel, f.vida() * nivel, f.defensa() * nivel,
                new Formula(10 * nivel, 1, f.carasAtaque()), new Formula(0, 1, f.carasDano()), null);
    }

    @Override
    public VeredictoDeEstrategia validarEstrategia(String prototipo, int nivel, List<List<String>> rotaciones) {
        return base.validarEstrategia(prototipo, nivel, rotaciones);
    }

    @Override
    public VeredictoDeComposicion validarIndividual(String prototipo) {
        return base.validarIndividual(prototipo);
    }

    @Override
    public DecisionDeTurno decidir(TurnoParaDecidir turno) {
        return base.decidir(turno);
    }

    @Override
    public double porEnemigoDerrotado(int dado) {
        return base.porEnemigoDerrotado(dado);
    }
}

package nexus.combate.reglas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static nexus.combate.reglas.Reglamento.efecto;

/**
 * Los efectos de COMBATE de las armas y los items — Tablas 8 a 19.
 *
 * <p><b>Reparto con inventario.</b> Los efectos PLANOS sobre el portador
 * (+1 al ataque, +2 al dano, +1 a la defensa, +2 de vida, +(2d4) de sanacion)
 * los aplica inventario al calcular las estadisticas equipadas
 * ({@code CatalogoEfectosEquipamiento}), y llegan al motor ya sumados en
 * {@link Estadisticas}. Aqui van los que inventario dejo fuera a proposito por
 * ser reglas de combate: el critico (Tabla 23), lo que afecta al OPONENTE y lo
 * que dura varios turnos. Cada entrada cita su tabla; como se lee cada una esta
 * en la decision D-B7-10.
 *
 * <p>Un objeto que no aparece aqui no tiene efecto de combate propio (sus
 * efectos son planos). Un nombre desconocido no rompe nada: no suma nada.
 */
public final class EquipoDeCombate {

    /** Codigo del sangrado de la Cierra sangrienta, el que dobla la Mancuerna yugular. */
    static final String CIERRA_SANGRIENTA = "CIERRA_SANGRIENTA";

    private static final Map<String, EfectosDeEquipo> EFECTOS = Map.ofEntries(
            // Tabla 8 · Espada de una mano (Guerrero Tanque): «+1 al ataque, +1% de critico al ataque»
            Map.entry("espada de una mano", EfectosDeEquipo.critico(1)),
            // Tabla 8 · Espada de dos manos (Guerrero Armas): «+1 al ataque, +3% de critico al ataque»
            Map.entry("espada de dos manos", EfectosDeEquipo.critico(3)),
            // Tabla 9 · Orbe de manos ardientes (Mago Fuego): «+1 al dano, +3% de critico al ataque»
            Map.entry("orbe de manos ardientes", EfectosDeEquipo.critico(3)),
            // Tabla 9 · Baculo de Permafrost (Mago Hielo): «-1 al dano del oponente,
            // -2% de critico al ataque del oponente»
            Map.entry("baculo de permafrost", EfectosDeEquipo.NINGUNO.conDefensa(0, 1, 2)),
            // Tabla 10 · Daga purulenta (Picaro Veneno): «+1 al dano por dos turnos,
            // +3% de critico al ataque» — el dano por dos turnos es un sangrado.
            Map.entry("daga purulenta", EfectosDeEquipo.critico(3).conAlAcertar(List.of(
                    efecto("DAGA_PURULENTA", "Daga purulenta", TipoDeEfecto.DANO_POR_TURNO, Tirada.fija(1), 2)))),
            // Tabla 10 · Vision borrosa (Picaro Veneno): «-1 al ataque del oponente»
            Map.entry("vision borrosa", EfectosDeEquipo.NINGUNO.conDefensa(1, 0, 0)),
            // Tabla 10 · Machete vendito (Picaro Machete): «+2 al dano, +2% de critico al ataque»
            Map.entry("machete vendito", EfectosDeEquipo.critico(2)),
            // Tabla 10 · Cierra sangrienta (Picaro Machete): «+2 al dano por dos turnos»
            Map.entry("cierra sangrienta", EfectosDeEquipo.NINGUNO.conAlAcertar(List.of(
                    efecto(CIERRA_SANGRIENTA, "Cierra sangrienta", TipoDeEfecto.DANO_POR_TURNO, Tirada.fija(2), 2)))),
            // Tabla 11 · Yerbabuena (Chaman): «+2 de sanacion por dos turnos»
            Map.entry("yerbabuena", EfectosDeEquipo.NINGUNO.conAlSanar(List.of(
                    efecto("YERBABUENA", "Yerbabuena", TipoDeEfecto.SANACION_POR_TURNO, Tirada.fija(2), 2)))),
            // Tabla 16 · Pinchos de escudo (Guerrero Tanque): «Si el ataque del
            // oponente es menor que la defensa del guerrero. El oponente recibe +1 de dano»
            Map.entry("pinchos de escudo", EfectosDeEquipo.NINGUNO.conPinchos(1)),
            // Tabla 16 · Empunadura de Furia (Guerrero Armas): «+1 de dano por dos
            // turnos. Esto causa que el guerrero pierda -1 de vida en los mismos turnos.»
            Map.entry("empunadura de furia", EfectosDeEquipo.NINGUNO
                    .conAlAcertar(List.of(efecto("EMPUNADURA_DE_FURIA", "Empuñadura de Furia",
                            TipoDeEfecto.DANO_POR_TURNO, Tirada.fija(1), 2)))
                    .conPropiosAlAcertar(List.of(efecto("EMPUNADURA_DE_FURIA_RETROCESO", "Empuñadura de Furia",
                            TipoDeEfecto.DANO_POR_TURNO, Tirada.fija(1), 2)))),
            // Tabla 18 · Veneno lacerante (Picaro Veneno): «-1 al poder de oponente.
            // Solo aplica cada dos turnos.»
            Map.entry("veneno lacerante", EfectosDeEquipo.NINGUNO.conVenenoLacerante()),
            // Tabla 18 · Mancuerna yugular (Picaro Machete): «Explota por 2 el valor en
            // turno causado por la cierra sangrienta en el oponente»
            Map.entry("mancuerna yugular", EfectosDeEquipo.NINGUNO.conMancuerna()),
            // Tabla 19 · Pluma sanadora (Chaman): «Mejora la sanacion multiplicando x2»
            Map.entry("pluma sanadora", EfectosDeEquipo.NINGUNO.conMultiplicadorDeSanacion(2)),
            // Tabla 19 · Benditas (Medico): «Sana las heridas por tres turnos o cura +1 por tres turnos»
            Map.entry("benditas", EfectosDeEquipo.NINGUNO.conAlSanar(List.of(
                    efecto("BENDITAS", "Benditas", TipoDeEfecto.SANACION_POR_TURNO, Tirada.fija(1), 3)))));

    private EquipoDeCombate() {
    }

    /** Los efectos de combate de todo lo que lleva puesto, sumados. */
    public static EfectosDeEquipo de(List<String> nombres) {
        EfectosDeEquipo total = EfectosDeEquipo.NINGUNO;
        for (String nombre : nombres == null ? List.<String>of() : nombres) {
            total = total.mas(EFECTOS.getOrDefault(Nombres.normalizar(nombre), EfectosDeEquipo.NINGUNO));
        }
        return total.conMancuernaAplicada();
    }

    /**
     * Efectos de combate de un conjunto de objetos.
     *
     * @param critico                  puntos de % de critico para sus ataques (Tabla 23)
     * @param restaAtaqueDelAtacante   quien le ataca tira con esto de menos
     * @param restaDanoDelAtacante     quien le ataca hace esto de menos
     * @param restaCriticoDelAtacante  quien le ataca tiene esto de menos de critico
     * @param pinchos                  dano al atacante cuyo ataque no llega a su defensa
     * @param alAcertar                efectos sobre el objetivo cuando su golpe causa efecto
     * @param propiosAlAcertar         efectos sobre si mismo cuando su golpe causa efecto
     * @param venenoLacerante          quita 1 de poder al objetivo en sus turnos pares
     * @param mancuerna                dobla el sangrado de la Cierra sangrienta
     * @param multiplicadorDeSanacion  multiplica lo que sana
     * @param alSanar                  efectos sobre quien sana
     */
    public record EfectosDeEquipo(int critico, int restaAtaqueDelAtacante, int restaDanoDelAtacante,
                                  int restaCriticoDelAtacante, int pinchos,
                                  List<PlantillaDeEfecto> alAcertar, List<PlantillaDeEfecto> propiosAlAcertar,
                                  boolean venenoLacerante, boolean mancuerna, int multiplicadorDeSanacion,
                                  List<PlantillaDeEfecto> alSanar) {

        public static final EfectosDeEquipo NINGUNO = new EfectosDeEquipo(0, 0, 0, 0, 0, List.of(), List.of(),
                false, false, 1, List.of());

        public EfectosDeEquipo {
            alAcertar = List.copyOf(alAcertar);
            propiosAlAcertar = List.copyOf(propiosAlAcertar);
            alSanar = List.copyOf(alSanar);
        }

        static EfectosDeEquipo critico(int puntos) {
            return new EfectosDeEquipo(puntos, 0, 0, 0, 0, List.of(), List.of(), false, false, 1, List.of());
        }

        EfectosDeEquipo conDefensa(int ataque, int dano, int criticoAjeno) {
            return new EfectosDeEquipo(critico, ataque, dano, criticoAjeno, pinchos, alAcertar, propiosAlAcertar,
                    venenoLacerante, mancuerna, multiplicadorDeSanacion, alSanar);
        }

        EfectosDeEquipo conAlAcertar(List<PlantillaDeEfecto> efectos) {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, pinchos, efectos, propiosAlAcertar, venenoLacerante, mancuerna,
                    multiplicadorDeSanacion, alSanar);
        }

        EfectosDeEquipo conPropiosAlAcertar(List<PlantillaDeEfecto> efectos) {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, pinchos, alAcertar, efectos, venenoLacerante, mancuerna,
                    multiplicadorDeSanacion, alSanar);
        }

        EfectosDeEquipo conPinchos(int dano) {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, dano, alAcertar, propiosAlAcertar, venenoLacerante, mancuerna,
                    multiplicadorDeSanacion, alSanar);
        }

        EfectosDeEquipo conVenenoLacerante() {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, pinchos, alAcertar, propiosAlAcertar, true, mancuerna,
                    multiplicadorDeSanacion, alSanar);
        }

        EfectosDeEquipo conMancuerna() {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, pinchos, alAcertar, propiosAlAcertar, venenoLacerante, true,
                    multiplicadorDeSanacion, alSanar);
        }

        EfectosDeEquipo conMultiplicadorDeSanacion(int factor) {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, pinchos, alAcertar, propiosAlAcertar, venenoLacerante, mancuerna,
                    factor, alSanar);
        }

        EfectosDeEquipo conAlSanar(List<PlantillaDeEfecto> efectos) {
            return new EfectosDeEquipo(critico, restaAtaqueDelAtacante, restaDanoDelAtacante,
                    restaCriticoDelAtacante, pinchos, alAcertar, propiosAlAcertar, venenoLacerante, mancuerna,
                    multiplicadorDeSanacion, efectos);
        }

        EfectosDeEquipo mas(EfectosDeEquipo otro) {
            List<PlantillaDeEfecto> acertar = new ArrayList<>(alAcertar);
            acertar.addAll(otro.alAcertar);
            List<PlantillaDeEfecto> propios = new ArrayList<>(propiosAlAcertar);
            propios.addAll(otro.propiosAlAcertar);
            List<PlantillaDeEfecto> sanar = new ArrayList<>(alSanar);
            sanar.addAll(otro.alSanar);
            return new EfectosDeEquipo(critico + otro.critico,
                    restaAtaqueDelAtacante + otro.restaAtaqueDelAtacante,
                    restaDanoDelAtacante + otro.restaDanoDelAtacante,
                    restaCriticoDelAtacante + otro.restaCriticoDelAtacante,
                    pinchos + otro.pinchos, acertar, propios,
                    venenoLacerante || otro.venenoLacerante, mancuerna || otro.mancuerna,
                    multiplicadorDeSanacion * otro.multiplicadorDeSanacion, sanar);
        }

        /** La Mancuerna yugular dobla el sangrado de la Cierra sangrienta, si llevan las dos. */
        EfectosDeEquipo conMancuernaAplicada() {
            if (!mancuerna) {
                return this;
            }
            List<PlantillaDeEfecto> doblados = alAcertar.stream()
                    .map(e -> e.codigo().equals(CIERRA_SANGRIENTA)
                            ? e.conValor(Tirada.fija(e.valor().fijo() * 2))
                            : e)
                    .toList();
            return conAlAcertar(doblados);
        }
    }
}

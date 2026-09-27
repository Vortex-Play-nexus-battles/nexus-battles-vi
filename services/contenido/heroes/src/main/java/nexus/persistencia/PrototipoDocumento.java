package nexus.persistencia;

import java.util.List;
import nexus.dominio.Accion;
import nexus.dominio.Estadisticas;
import nexus.dominio.Formula;
import nexus.dominio.Prototipo;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Representacion del prototipo en la coleccion "prototipos". Clases planas a
 * proposito, sin depender del mapeo de records, con conversion explicita
 * hacia y desde el dominio.
 *
 * <p>B4: {@code origen}, {@code semillaVersion} y {@code modificadoPor} son las
 * marcas de la semilla versionada, con el mismo criterio que el catalogo de
 * productos. Hoy heroes no tiene escritura por API, asi que nadie fija
 * {@code modificadoPor}; la marca existe para que el dia que la tenga, la
 * semilla respete lo editado sin cambiar de criterio.
 */
@Document("prototipos")
public class PrototipoDocumento {

    /** Origen de los prototipos que siembra {@link CatalogoEnMongo}. */
    public static final String ORIGEN_SEMILLA = "SEMILLA";

    @Id
    public String nombre;
    public String nombreNormalizado;
    public String tipo;
    public String descripcion;
    public boolean esSanador;
    public EstadisticasDoc estadisticasNivel1;
    public List<AccionDoc> acciones;
    public String origen;
    public Integer semillaVersion;
    public String modificadoPor;

    public static class EstadisticasDoc {
        public int poder;
        public int vida;
        public int defensa;
        public FormulaDoc ataque;
        public FormulaDoc dano;
        public FormulaDoc sanar;
    }

    public static class FormulaDoc {
        public int base;
        public int cantidadDados;
        public int carasDado;
    }

    public static class AccionDoc {
        public String nombre;
        public Integer costoPuntos;
        public String efecto;
    }

    public static PrototipoDocumento de(Prototipo p) {
        PrototipoDocumento d = new PrototipoDocumento();
        d.nombre = p.nombre();
        d.nombreNormalizado = CatalogoEnMongo.normalizar(p.nombre());
        d.tipo = p.tipo();
        d.descripcion = p.descripcion();
        d.esSanador = p.esSanador();
        d.estadisticasNivel1 = new EstadisticasDoc();
        d.estadisticasNivel1.poder = p.estadisticasNivel1().poder();
        d.estadisticasNivel1.vida = p.estadisticasNivel1().vida();
        d.estadisticasNivel1.defensa = p.estadisticasNivel1().defensa();
        d.estadisticasNivel1.ataque = deFormula(p.estadisticasNivel1().ataque());
        d.estadisticasNivel1.dano = deFormula(p.estadisticasNivel1().dano());
        d.estadisticasNivel1.sanar = deFormula(p.estadisticasNivel1().sanar());
        d.acciones = p.acciones().stream().map(a -> {
            AccionDoc ad = new AccionDoc();
            ad.nombre = a.nombre();
            ad.costoPuntos = a.costoPuntos();
            ad.efecto = a.efecto();
            return ad;
        }).toList();
        return d;
    }

    /** Un prototipo de la semilla, con sus marcas (B4). */
    public static PrototipoDocumento sembrado(Prototipo p, int semillaVersion) {
        PrototipoDocumento d = de(p);
        d.origen = ORIGEN_SEMILLA;
        d.semillaVersion = semillaVersion;
        return d;
    }

    /** Sembrado (con marca) o anterior a las marcas; un prototipo registrado aparte, no. */
    boolean esDeLaSemilla() {
        return origen == null || ORIGEN_SEMILLA.equals(origen);
    }

    /** La version de la semilla que lo escribio; 0 si es anterior a B4. */
    int versionSembrada() {
        return semillaVersion == null ? 0 : semillaVersion;
    }

    boolean editadoPorAlguien() {
        return modificadoPor != null && !modificadoPor.isBlank();
    }

    public Prototipo aDominio() {
        return new Prototipo(
                nombre, tipo, descripcion, esSanador,
                new Estadisticas(
                        estadisticasNivel1.poder, estadisticasNivel1.vida, estadisticasNivel1.defensa,
                        aFormula(estadisticasNivel1.ataque), aFormula(estadisticasNivel1.dano),
                        aFormula(estadisticasNivel1.sanar)),
                acciones.stream().map(a -> new Accion(a.nombre, a.costoPuntos, a.efecto)).toList());
    }

    private static FormulaDoc deFormula(Formula f) {
        if (f == null) {
            return null;
        }
        FormulaDoc d = new FormulaDoc();
        d.base = f.base();
        d.cantidadDados = f.cantidadDados();
        d.carasDado = f.carasDado();
        return d;
    }

    private static Formula aFormula(FormulaDoc d) {
        return d == null ? null : new Formula(d.base, d.cantidadDados, d.carasDado);
    }
}

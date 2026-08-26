package nexus.dominio;

/**
 * Formula de la Tabla 6 en forma estructurada: base + NdM (notacion de dados
 * del documento, seccion 6.1.2: "NdM significa lanzar N dados de M caras").
 * Se guarda estructurada para que el motor de combate no tenga que parsear
 * texto; texto() reproduce la forma exacta de la tabla del cliente.
 */
public record Formula(int base, int cantidadDados, int carasDado) {

    public Formula {
        if (cantidadDados < 0 || carasDado < 0 || base < 0) {
            throw new IllegalArgumentException("Una fórmula no admite valores negativos.");
        }
    }

    public String texto() {
        if (cantidadDados == 0) {
            return String.valueOf(base);
        }
        String dados = cantidadDados + "d" + carasDado;
        return base == 0 ? dados : base + " + " + dados;
    }
}

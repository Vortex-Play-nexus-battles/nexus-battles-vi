package nexus.inventario.api;

import nexus.inventario.dominio.FormulaDetalle;

/**
 * Vista de {@code FormulaDetalle} para la API: ademas de los tres campos
 * sueltos (utiles para quien vaya a tirar el dado), incluye {@code formula}
 * ya reconstruida en el mismo formato que usan los comentarios del dominio
 * y el catalogo ("10 + 1d6", "1d4") — exponer solo el objeto interno sin
 * esto obligaria al cliente a reimplementar ese formato el mismo.
 */
public record FormulaDetalleResponse(int base, int cantidadDados, int caras, String formula) {

    static FormulaDetalleResponse de(FormulaDetalle detalle) {
        if (detalle == null) {
            return null;
        }
        return new FormulaDetalleResponse(detalle.base(), detalle.cantidadDados(), detalle.caras(), formatoLegible(detalle));
    }

    private static String formatoLegible(FormulaDetalle detalle) {
        if (detalle.cantidadDados() == 0) {
            return String.valueOf(detalle.base());
        }
        String dados = detalle.cantidadDados() + "d" + detalle.caras();
        return detalle.base() == 0 ? dados : detalle.base() + " + " + dados;
    }
}

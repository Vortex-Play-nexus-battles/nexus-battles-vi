package nexus.inventario.dominio;

/**
 * El heroe esta en una mision (1.6.0, B9; seccion 7.8.10): no se equipa, no se
 * desequipa, no se renombra, no se borra ni se subasta hasta que vuelva. Es un
 * 409 «Heroe en mision», un caso particular de elemento no disponible.
 */
public class HeroeEnMisionException extends ElementoNoDisponibleException {

    public HeroeEnMisionException(String mensaje) {
        super(mensaje);
    }
}

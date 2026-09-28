package com.nexusbattles.ms_ecommerce.precios;

import java.util.List;
import java.util.Set;

/**
 * Se pidio una moneda para la que la tienda no tiene tasa: el parametro de
 * admin-parametros no esta definido (decision del PO pendiente) o el servicio
 * no respondio. La respuesta es 422 {@code moneda-no-disponible} con las
 * monedas que si se pueden pedir; nunca se inventa una tasa.
 */
public class MonedaNoDisponibleException extends RuntimeException {

    private final Moneda moneda;
    private final List<Moneda> disponibles;

    public MonedaNoDisponibleException(Moneda moneda, Set<Moneda> disponibles) {
        super("La tienda no tiene tasa de cambio para " + moneda + ": por ahora se vende en "
                + String.join(", ", disponibles.stream().sorted().map(Enum::name).toList()) + ".");
        this.moneda = moneda;
        this.disponibles = disponibles.stream().sorted().toList();
    }

    public Moneda moneda() {
        return moneda;
    }

    public List<Moneda> disponibles() {
        return disponibles;
    }
}

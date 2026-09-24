package com.nexusbattles.ms_ecommerce.catalogo;

import java.math.BigDecimal;

/**
 * Productos del catalogo maestro para las pruebas. Son dobles de prueba: la
 * tienda no tiene ni debe tener productos propios.
 */
public final class ProductosDePrueba {

    public static final String ESPADA = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    public static final String ESCUDO = "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b";
    public static final String POCION = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";

    private ProductosDePrueba() {
    }

    /** Un producto que se vende: ACTIVO, tiraje ilimitado y precio en moneda real. */
    public static ProductoDelCatalogo enVenta(String id, String tipo, String precioMonedaReal) {
        return new ProductoDelCatalogo(id, "Producto " + id, "img/" + id + ".png", "Descripcion de " + id,
                tipo, ProductoDelCatalogo.TIRAJE_ILIMITADO, null, new BigDecimal(precioMonedaReal), true,
                "ACTIVO", null);
    }

    public static ProductoDelCatalogo enVenta(String id) {
        return enVenta(id, "ARMA", "6000");
    }

    public static ProductoDelCatalogo conNombre(ProductoDelCatalogo p, String nombre) {
        return new ProductoDelCatalogo(p.id(), nombre, p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.estado(), p.habilidades());
    }

    public static ProductoDelCatalogo conEstado(ProductoDelCatalogo p, String estado) {
        return new ProductoDelCatalogo(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), estado, p.habilidades());
    }

    public static ProductoDelCatalogo conTiraje(ProductoDelCatalogo p, Integer tiraje) {
        return new ProductoDelCatalogo(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), tiraje,
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.estado(), p.habilidades());
    }

    public static ProductoDelCatalogo conPrecio(ProductoDelCatalogo p, BigDecimal precioMonedaReal) {
        return new ProductoDelCatalogo(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), precioMonedaReal, p.premium(), p.estado(), p.habilidades());
    }

    /** Un producto que solo se vende en creditos (precio en moneda real nulo). */
    public static ProductoDelCatalogo soloEnCreditos(ProductoDelCatalogo p, int precioCreditos) {
        return new ProductoDelCatalogo(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                precioCreditos, null, false, p.estado(), p.habilidades());
    }

    public static ProductoDelCatalogo conHabilidades(ProductoDelCatalogo p, Object habilidades) {
        return new ProductoDelCatalogo(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.estado(), habilidades);
    }

    public static ProductoDelCatalogo sinId(ProductoDelCatalogo p) {
        return new ProductoDelCatalogo(null, p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.estado(), p.habilidades());
    }

    /**
     * El JSON de un {@code ProductoCreado} vendible, con los campos extra que
     * el catalogo publica y la tienda no usa.
     */
    public static String json(String id, String nombre, String tipo, String estado, int tiraje, String precio) {
        String precioJson = precio == null ? "" : "\"precioMonedaReal\":" + precio + ",";
        return """
                {"id":"%s","nombre":"%s","imagen":"img/%s.png","descripcion":"Descripcion de %s","tipo":"%s",\
                "tiraje":%d,%s"premium":true,"estado":"%s","prototipo":"Guerrero Tanque","version":3,\
                "creadoEn":"2026-09-01T10:00:00Z","modificadoEn":"2026-09-20T10:00:00Z"}"""
                .formatted(id, nombre, id, nombre, tipo, tiraje, precioJson, estado);
    }

    public static String json(String id, String nombre) {
        return json(id, nombre, "ARMA", "ACTIVO", -1, "6000");
    }

    /** Una pagina del listado del catalogo ({@code PaginaDeProductos}). */
    public static String pagina(int numero, int totalDePaginas, String... productos) {
        return """
                {"content":[%s],"page":%d,"size":50,"totalElements":%d,"totalPages":%d}"""
                .formatted(String.join(",", productos), numero, productos.length, totalDePaginas);
    }
}

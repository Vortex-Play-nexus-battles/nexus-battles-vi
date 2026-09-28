package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import nexus.misiones.aplicacion.CatalogoDeProductos;
import nexus.misiones.aplicacion.HeroeNoEncontrado;
import org.springframework.web.client.RestClient;

/**
 * {@link CatalogoDeProductos} contra el servicio de productos
 * ({@code GET /api/v1/productos/{id}}, publico, productos.yaml): el prototipo
 * de un producto HEROE, que es el nombre que entienden heroes y el motor.
 */
public class ClienteProductos implements CatalogoDeProductos {

    static final String DEPENDENCIA = "productos";

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteProductos(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public String prototipoDe(String productoId) {
        Contestacion<Producto> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/productos/{id}", productoId)
                .retrieve()
                .body(Producto.class));
        if (c.rechazada()) {
            if (c.estado() == 404 || c.estado() == 400) {
                throw new HeroeNoEncontrado();
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
        return "HEROE".equals(c.cuerpo().tipo()) ? c.cuerpo().prototipo() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(String id, String tipo, String nombre, String prototipo) {
    }
}

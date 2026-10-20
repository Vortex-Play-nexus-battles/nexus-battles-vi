package nexus.semilla;

import java.util.List;

import nexus.persistencia.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Saca de la tienda, al arrancar, los productos que no tienen imagen real:
 * los de prueba dados de alta a mano con una ruta suelta ({@code espada.png},
 * {@code casco.png}), que el sitio no sirve y que la tienda pintaba con el
 * simbolo de reserva. No son del catalogo oficial (la guarda
 * {@code catalogo-oficial.py} lo comprueba) y no tienen arte.
 *
 * <p>No se borran: se <b>suspenden</b>, igual que lo haria la administracion.
 * Dejan de venderse y de verse en la tienda y la vitrina, se conservan en los
 * inventarios de quien ya los tiene y pueden reactivarse. Idempotente: un
 * producto ya suspendido no se vuelve a tocar. Si falla, el servicio arranca
 * igual.
 */
@Component
public class RetiradaDeProductosSinImagen implements ApplicationRunner {

    private static final Logger BITACORA = LoggerFactory.getLogger(RetiradaDeProductosSinImagen.class);

    private final ProductoRepository repositorio;

    public RetiradaDeProductosSinImagen(ProductoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            retirar();
        } catch (RuntimeException e) {
            BITACORA.error("Retirada de productos sin imagen fallida: el servicio arranca igual", e);
        }
    }

    /** Devuelve los nombres de los productos que se retiraron. */
    public List<String> retirar() {
        List<String> retirados = repositorio.suspenderConImagenSuelta();
        if (!retirados.isEmpty()) {
            BITACORA.info("Productos sin imagen real retirados de la tienda (suspendidos): {}", retirados);
        }
        return retirados;
    }
}

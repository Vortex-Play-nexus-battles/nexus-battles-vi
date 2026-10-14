package nexus.inventario.aplicacion;

import java.util.Map;

/**
 * De dónde sale el texto buscable de cada producto del catálogo.
 *
 * <p>Implementación real: {@link FuenteDelCatalogoHttp}, que lee el catálogo
 * público de productos y la ficha de cada héroe.
 */
public interface FuenteDelCatalogo {

    /**
     * @return id del producto → todo el texto que lo describe (nombre, tipo,
     *         parte, descripción, efectos, prototipo y, en los héroes, sus
     *         acciones)
     * @throws ResolutorDeProductoException si el catálogo no responde
     */
    Map<String, String> textosBuscables();
}

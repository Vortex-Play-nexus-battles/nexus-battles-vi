package com.nexusbattles.ms_ecommerce.integracion.inventario;

import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import com.nexusbattles.ms_ecommerce.seguridad.CredencialDeServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lo que cada jugador ya tiene, para la marca «propio» de la vitrina (7.5:
 * «los productos que el cliente ha adquirido estaran marcados como propios»).
 *
 * <p>Es un dato del inventario, no una deduccion de la tienda: un producto se
 * pudo conseguir comprandolo, en una subasta, en un cofre o en el paquete
 * inicial. Se pregunta al inventario con la credencial de la tienda.
 *
 * <p><b>Nunca tumba la vitrina.</b> Si el inventario no responde, o la tienda
 * no tiene credencial, el resultado es «nada marcado» y la vitrina sale igual:
 * una marca que falta es mejor que una vitrina caida, y mucho mejor que una
 * marca equivocada.
 *
 * <p>Copia de {@value #SEGUNDOS_DE_VIGENCIA} s por jugador (las paginas de la
 * vitrina se piden seguidas), con un tope de {@value #MAXIMO_DE_JUGADORES}
 * jugadores en memoria; al pasarlo se olvida el mas antiguo.
 */
@Component
public class ProductosPropios {

    private static final Logger log = LoggerFactory.getLogger(ProductosPropios.class);

    static final long SEGUNDOS_DE_VIGENCIA = 30;
    static final int MAXIMO_DE_JUGADORES = 1000;

    private final ClienteDeInventario inventario;
    private final CredencialDeServicio credencial;
    private final Clock reloj;
    private final Map<String, Copia> copias = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Copia> masAntigua) {
            return size() > MAXIMO_DE_JUGADORES;
        }
    };

    public ProductosPropios(ClienteDeInventario inventario, CredencialDeServicio credencial, Clock reloj) {
        this.inventario = inventario;
        this.credencial = credencial;
        this.reloj = reloj;
    }

    /** Los productos del catalogo que el jugador tiene; vacio si no se puede saber. */
    public Set<String> de(String uid) {
        if (uid == null || uid.isBlank() || !credencial.configurada()) {
            return Set.of();
        }
        Instant ahora = reloj.instant();
        synchronized (copias) {
            Copia copia = copias.get(uid);
            if (copia != null && ahora.isBefore(copia.caducaEn())) {
                return copia.productos();
            }
        }
        try {
            Set<String> productos = inventario.productosDe(uid);
            synchronized (copias) {
                copias.put(uid, new Copia(productos, ahora.plus(Duration.ofSeconds(SEGUNDOS_DE_VIGENCIA))));
            }
            return productos;
        } catch (ServicioNoDisponibleException caido) {
            log.warn("La vitrina sale sin la marca de lo propio: {}", caido.getMessage());
            return Set.of();
        }
    }

    private record Copia(Set<String> productos, Instant caducaEn) {
    }
}

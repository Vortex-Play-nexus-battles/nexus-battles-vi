package com.nexusbattles.ms_ecommerce.precios;

import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import com.nexusbattles.ms_ecommerce.integracion.parametros.ClienteDeParametros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las tasas con las que la tienda convierte de COP a USD y EUR (7.5: el
 * precio en la moneda de la ubicacion del cliente).
 *
 * <p><b>De donde salen: admin-parametros, nunca del codigo.</b> Cuantos pesos
 * vale un dolar o un euro es una decision del PO que el documento no fija
 * (pendiente). La tienda la lee de dos parametros que un administrador fija
 * desde el panel ({@code PUT /parametros/{clave}}, con motivo y version):
 *
 * <ul>
 *   <li>{@value #CLAVE_USD}: pesos colombianos por 1 USD (por ejemplo 4000);</li>
 *   <li>{@value #CLAVE_EUR}: pesos colombianos por 1 EUR.</li>
 * </ul>
 *
 * <p>Mientras un parametro no exista en el catalogo de admin-parametros o no
 * tenga valor, esa moneda no se ofrece: la vitrina la quita de
 * {@code monedasDisponibles} y pedirla responde 422. COP no depende de nada.
 * Como se crea el parametro (una migracion de admin-parametros, que es quien
 * es dueno de su catalogo) esta en el README de este servicio.
 *
 * <p><b>Copia de un minuto.</b> Cada precio de la vitrina no puede costar una
 * llamada a admin-parametros; se guarda {@value #SEGUNDOS_DE_VIGENCIA} s. Si al
 * caducar admin-parametros no responde, se sigue usando la ultima tasa leida
 * hasta {@value #MINUTOS_DE_GRACIA} minutos: una caida corta del panel no
 * deja a la tienda sin dolares a mitad de una compra, y una larga no la deja
 * cobrando con una tasa de hace horas.
 *
 * <p>Sin cerrojo, por lo mismo que la copia del catalogo de la vitrina: si
 * admin-parametros tarda, un cerrojo pondria en fila a todas las peticiones.
 * Dos lecturas a la vez son inocuas; gana la ultima.
 */
@Component
public class TasasDeCambio {

    private static final Logger log = LoggerFactory.getLogger(TasasDeCambio.class);

    public static final String CLAVE_USD = "tienda.tasa-cop-usd";
    public static final String CLAVE_EUR = "tienda.tasa-cop-eur";

    static final long SEGUNDOS_DE_VIGENCIA = 60;
    static final long MINUTOS_DE_GRACIA = 15;

    private static final Duration VIGENCIA = Duration.ofSeconds(SEGUNDOS_DE_VIGENCIA);
    private static final Duration GRACIA = Duration.ofMinutes(MINUTOS_DE_GRACIA);

    private final ClienteDeParametros parametros;
    private final Clock reloj;
    private final Map<Moneda, Lectura> lecturas = new ConcurrentHashMap<>();

    public TasasDeCambio(ClienteDeParametros parametros, Clock reloj) {
        this.parametros = parametros;
        this.reloj = reloj;
    }

    /**
     * La tarifa de una moneda para esta peticion.
     *
     * @throws MonedaNoDisponibleException si no hay tasa para ella
     */
    public Tarifa tarifa(Moneda moneda) {
        if (moneda.esLaDelCatalogo()) {
            return Tarifa.enPesos();
        }
        return tasa(moneda)
                .map(tasa -> new Tarifa(moneda, tasa))
                .orElseThrow(() -> new MonedaNoDisponibleException(moneda, disponibles()));
    }

    /** Las monedas que se pueden pedir ahora mismo: COP siempre. */
    public Set<Moneda> disponibles() {
        Set<Moneda> disponibles = EnumSet.of(Moneda.COP);
        for (Moneda moneda : Moneda.values()) {
            if (!moneda.esLaDelCatalogo() && tasa(moneda).isPresent()) {
                disponibles.add(moneda);
            }
        }
        return disponibles;
    }

    private Optional<BigDecimal> tasa(Moneda moneda) {
        Instant ahora = reloj.instant();
        Lectura anterior = lecturas.get(moneda);
        if (anterior != null && ahora.isBefore(anterior.leidaEn().plus(VIGENCIA))) {
            return anterior.tasa();
        }
        try {
            Optional<BigDecimal> leida = parametros.valor(claveDe(moneda)).flatMap(valor -> interpretar(moneda, valor));
            lecturas.put(moneda, new Lectura(leida, ahora));
            return leida;
        } catch (ServicioNoDisponibleException caida) {
            if (anterior != null && anterior.tasa().isPresent() && ahora.isBefore(anterior.leidaEn().plus(GRACIA))) {
                log.warn("admin-parametros no responde; se sigue con la tasa de {} leida a las {}", moneda,
                        anterior.leidaEn());
                return anterior.tasa();
            }
            log.warn("admin-parametros no responde y no hay tasa reciente de {}: esa moneda no se ofrece ({})",
                    moneda, caida.getMessage());
            return Optional.empty();
        }
    }

    private static String claveDe(Moneda moneda) {
        return moneda == Moneda.USD ? CLAVE_USD : CLAVE_EUR;
    }

    private static Optional<BigDecimal> interpretar(Moneda moneda, String valor) {
        try {
            BigDecimal tasa = new BigDecimal(valor.strip());
            if (tasa.signum() > 0) {
                return Optional.of(tasa);
            }
        } catch (NumberFormatException ilegible) {
            // cae al aviso de abajo
        }
        log.warn("El parametro de la tasa de {} no es un numero positivo; esa moneda no se ofrece", moneda);
        return Optional.empty();
    }

    private record Lectura(Optional<BigDecimal> tasa, Instant leidaEn) {
    }
}

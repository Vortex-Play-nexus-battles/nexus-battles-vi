package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteParametros;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteProductos;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Con que empieza un jugador nuevo: cuantos creditos y que kit (un heroe y
 * lo necesario para que pueda jugar).
 *
 * <p><b>De donde sale cada valor, en este orden:</b>
 * <ol>
 *   <li>El parametro del sistema ({@value #CLAVE_CREDITOS},
 *       {@value #CLAVE_KIT}) en admin-parametros. Es lo que decide el PO y
 *       lo que un administrador cambia desde el panel, sin desplegar.</li>
 *   <li>Si el parametro no tiene valor (el PO no ha decidido: PEN-04), el
 *       respaldo de desarrollo por variable de entorno
 *       ({@code JUGADOR_CREDITOS_INICIALES}, {@code JUGADOR_KIT_INICIAL}).
 *       Es un valor PROVISIONAL, solo para que DEV sea jugable; queda
 *       marcado como {@link Fuente#RESPALDO_DEV} en el paso del alta.</li>
 *   <li>Sin ninguno de los dos, el paso no se puede hacer y el alta queda
 *       en error reintentable diciendo que falta configurarlo. No se inventa
 *       una cifra en el codigo.</li>
 * </ol>
 *
 * <p>El kit se configura solo con identificadores de producto; tipo, nombre
 * y parte de armadura los dice el catalogo, que es su dueno. Tiene que haber
 * exactamente un heroe y al menos un elemento equipable: HU-SAL-003 no deja
 * entrar a una partida con un heroe sin equipo.
 */
@Component
public class PoliticaInicial {

    public static final String CLAVE_CREDITOS = "jugador.creditos-iniciales";
    public static final String CLAVE_KIT = "jugador.kit-inicial";

    static final String HEROE = "HEROE";
    static final String ARMADURA = "ARMADURA";
    static final Set<String> EQUIPABLES = Set.of("ARMA", ARMADURA, "ITEM");

    public enum Fuente {
        /** Valor fijado en admin-parametros. */
        PARAMETRO,
        /** Valor provisional de DEV por variable de entorno (el parametro esta sin decidir). */
        RESPALDO_DEV
    }

    public record CreditosIniciales(long monto, Fuente fuente) {
    }

    public record ProductoDelKit(String id, String nombre, String tipo, String parte) {
    }

    public record KitInicial(ProductoDelKit heroe, List<ProductoDelKit> equipo, Fuente fuente) {
    }

    private final ClienteParametros parametros;
    private final ClienteProductos productos;
    private final String creditosRespaldo;
    private final String kitRespaldo;

    public PoliticaInicial(ClienteParametros parametros,
                           ClienteProductos productos,
                           @Value("${app.onboarding.creditos-iniciales-respaldo:}") String creditosRespaldo,
                           @Value("${app.onboarding.kit-inicial-respaldo:}") String kitRespaldo) {
        this.parametros = parametros;
        this.productos = productos;
        this.creditosRespaldo = creditosRespaldo == null ? "" : creditosRespaldo.trim();
        this.kitRespaldo = kitRespaldo == null ? "" : kitRespaldo.trim();
    }

    public CreditosIniciales creditos() {
        Valor valor = resolver(CLAVE_CREDITOS, creditosRespaldo, "JUGADOR_CREDITOS_INICIALES");
        long monto;
        try {
            monto = new BigDecimal(valor.texto()).longValueExact();
        } catch (ArithmeticException | NumberFormatException noEntero) {
            throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                    CLAVE_CREDITOS + " no es un entero: '" + valor.texto() + "'");
        }
        if (monto <= 0) {
            throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                    CLAVE_CREDITOS + " debe ser mayor que cero (todo jugador nuevo empieza con creditos)");
        }
        return new CreditosIniciales(monto, valor.fuente());
    }

    public KitInicial kit() {
        Valor valor = resolver(CLAVE_KIT, kitRespaldo, "JUGADOR_KIT_INICIAL");
        List<String> ids = Arrays.stream(valor.texto().split("[,;\\s]+"))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        if (ids.isEmpty()) {
            throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA, CLAVE_KIT + " no tiene productos");
        }
        List<ProductoDelKit> heroes = new ArrayList<>();
        List<ProductoDelKit> equipo = new ArrayList<>();
        for (String id : ids) {
            ClienteProductos.Producto producto = productos.consultar(id);
            ProductoDelKit delKit = new ProductoDelKit(id, nombreDe(producto), producto.tipo(), producto.parte());
            if (HEROE.equals(producto.tipo())) {
                heroes.add(delKit);
            } else if (EQUIPABLES.contains(producto.tipo())) {
                if (ARMADURA.equals(producto.tipo()) && (producto.parte() == null || producto.parte().isBlank())) {
                    throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                            "la armadura " + id + " del kit no dice que parte cubre");
                }
                equipo.add(delKit);
            } else {
                throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                        "el producto " + id + " del kit es " + producto.tipo()
                                + " y no se puede equipar a un heroe");
            }
        }
        if (heroes.size() != 1) {
            throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                    CLAVE_KIT + " debe tener exactamente un heroe y tiene " + heroes.size());
        }
        if (equipo.isEmpty()) {
            throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                    CLAVE_KIT + " no trae ningun arma, armadura ni item: sin equipo el heroe no puede entrar"
                            + " a una partida (HU-SAL-003)");
        }
        return new KitInicial(heroes.get(0), List.copyOf(equipo), valor.fuente());
    }

    private Valor resolver(String clave, String respaldo, String variable) {
        Optional<String> delParametro = parametros.valorDe(clave);
        if (delParametro.isPresent()) {
            return new Valor(delParametro.get(), Fuente.PARAMETRO);
        }
        if (!respaldo.isEmpty()) {
            return new Valor(respaldo, Fuente.RESPALDO_DEV);
        }
        throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                clave + " no tiene valor en admin-parametros (decision pendiente del PO) y " + variable
                        + " no esta definida");
    }

    private static String nombreDe(ClienteProductos.Producto producto) {
        return producto.nombre() == null || producto.nombre().isBlank() ? producto.id() : producto.nombre();
    }

    private record Valor(String texto, Fuente fuente) {
    }
}

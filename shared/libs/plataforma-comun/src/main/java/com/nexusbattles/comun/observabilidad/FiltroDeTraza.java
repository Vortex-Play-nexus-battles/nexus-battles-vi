package com.nexusbattles.comun.observabilidad;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Propagacion del identificador de traza.
 *
 * <p><b>Regla 5 de plataforma:</b> propagacion del trace id en toda llamada entre
 * servicios y todo mensaje de cola. Sin esto, un error que cruza cuatro
 * microservicios deja cuatro lineas de bitacora que nadie puede relacionar.
 *
 * <p>Sigue el formato W3C {@code traceparent}:
 * {@code 00-<32 hex de traza>-<16 hex de tramo>-<banderas>}. Si la peticion ya
 * trae uno, se reutiliza su identificador de traza; si no, se genera. En ambos
 * casos queda en el MDC bajo {@value #CLAVE_MDC}, donde el formato JSON de la
 * bitacora lo recoge (regla 6), y se devuelve en la respuesta para que quien
 * llamo pueda citarlo.
 *
 * <p>Se limpia siempre en el {@code finally}: los hilos se reutilizan, y una
 * traza olvidada aparecerio en la peticion de otra persona.
 */
public class FiltroDeTraza implements Filter {

    public static final String CABECERA = "traceparent";
    public static final String CLAVE_MDC = "trazaId";

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    @Override
    public void doFilter(ServletRequest peticion, ServletResponse respuesta, FilterChain cadena)
            throws IOException, ServletException {

        String trazaId = trazaDe(peticion);
        MDC.put(CLAVE_MDC, trazaId);
        try {
            if (respuesta instanceof HttpServletResponse http) {
                http.setHeader(CABECERA, cabeceraPara(trazaId));
            }
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(CLAVE_MDC);
        }
    }

    private String trazaDe(ServletRequest peticion) {
        if (peticion instanceof HttpServletRequest http) {
            String recibida = http.getHeader(CABECERA);
            String extraida = extraerTrazaId(recibida);
            if (extraida != null) {
                return extraida;
            }
        }
        return nuevoIdentificador(16);
    }

    /**
     * Extrae la parte de traza de un {@code traceparent} bien formado.
     *
     * @return el identificador, o {@code null} si la cabecera falta o es invalida
     */
    static String extraerTrazaId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] partes = traceparent.strip().split("-");
        if (partes.length < 3) {
            return null;
        }
        String traza = partes[1];
        // 32 hexadecimales, y no todo ceros: el cero es el valor invalido del estandar.
        if (traza.length() != 32 || !traza.matches("[0-9a-f]{32}") || traza.chars().allMatch(c -> c == '0')) {
            return null;
        }
        return traza;
    }

    private static String cabeceraPara(String trazaId) {
        return "00-" + trazaId + "-" + nuevoIdentificador(8) + "-01";
    }

    private static String nuevoIdentificador(int bytes) {
        byte[] datos = new byte[bytes];
        ALEATORIO.nextBytes(datos);
        return HEX.formatHex(datos);
    }
}

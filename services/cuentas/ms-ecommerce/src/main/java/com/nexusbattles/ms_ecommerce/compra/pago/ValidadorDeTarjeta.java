package com.nexusbattles.ms_ecommerce.compra.pago;

import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida el formulario de pago del 7.5 antes de que toque nada (contrato
 * 1.3.0, {@code SolicitudDePago}).
 *
 * <ul>
 *   <li><b>Titular:</b> de 3 a 80 caracteres.</li>
 *   <li><b>Numero:</b> solo cifras y espacios, de 12 a 19 cifras, y que pase
 *       el algoritmo de Luhn (el digito de control de toda tarjeta: descarta
 *       un numero mal copiado antes de ir a la pasarela).</li>
 *   <li><b>Vencimiento:</b> {@code MM/AA}, y no vencida: una tarjeta vale
 *       hasta el ultimo dia de su mes, asi que el mes en curso todavia sirve.
 *       El mes en curso es el de {@code tienda.zona-horaria} (Colombia).</li>
 *   <li><b>Codigo de seguridad:</b> 3 o 4 cifras.</li>
 * </ul>
 *
 * <p>El error dice el campo y que falla, nunca el valor.
 */
@Component
public class ValidadorDeTarjeta {

    private static final Pattern NUMERO = Pattern.compile("[0-9 ]{12,23}");
    private static final Pattern VENCIMIENTO = Pattern.compile("(0[1-9]|1[0-2])/([0-9]{2})");
    private static final Pattern CODIGO = Pattern.compile("[0-9]{3,4}");

    private final Clock reloj;
    private final ZoneId zona;

    public ValidadorDeTarjeta(Clock reloj, PropiedadesDeLaTienda propiedades) {
        this.reloj = reloj;
        this.zona = propiedades.zonaHoraria();
    }

    /**
     * @throws DatosDePagoInvalidosException con el primer campo que falle, en
     *         el orden del formulario
     */
    public TarjetaValidada validar(SolicitudDePago solicitud) {
        String titular = solicitud.titular() == null ? "" : solicitud.titular().strip();
        if (titular.length() < 3 || titular.length() > 80) {
            throw new DatosDePagoInvalidosException("titular",
                    "Escribe el nombre del titular tal como aparece en la tarjeta (de 3 a 80 caracteres).");
        }

        String numero = solicitud.numeroTarjeta() == null ? "" : solicitud.numeroTarjeta().strip();
        String cifras = numero.replace(" ", "");
        if (!NUMERO.matcher(numero).matches() || cifras.length() < 12 || cifras.length() > 19 || !pasaLuhn(cifras)) {
            throw new DatosDePagoInvalidosException("numeroTarjeta",
                    "El número de tarjeta no es válido. Revisa que lo hayas escrito completo.");
        }

        Matcher vencimiento = VENCIMIENTO.matcher(solicitud.vencimiento() == null ? "" : solicitud.vencimiento().strip());
        if (!vencimiento.matches()) {
            throw new DatosDePagoInvalidosException("vencimiento", "Escribe el vencimiento como MM/AA.");
        }
        YearMonth vence = YearMonth.of(2000 + Integer.parseInt(vencimiento.group(2)),
                Integer.parseInt(vencimiento.group(1)));
        if (vence.isBefore(YearMonth.now(reloj.withZone(zona)))) {
            throw new DatosDePagoInvalidosException("vencimiento", "La tarjeta está vencida.");
        }

        String codigo = solicitud.codigoSeguridad() == null ? "" : solicitud.codigoSeguridad().strip();
        if (!CODIGO.matcher(codigo).matches()) {
            throw new DatosDePagoInvalidosException("codigoSeguridad",
                    "El código de seguridad son los 3 o 4 números del reverso de la tarjeta.");
        }
        return new TarjetaValidada(marcaDe(cifras), cifras);
    }

    /** El algoritmo de Luhn (ISO/IEC 7812-1): el digito de control de toda tarjeta. */
    static boolean pasaLuhn(String cifras) {
        int suma = 0;
        boolean doblar = false;
        for (int i = cifras.length() - 1; i >= 0; i--) {
            int digito = cifras.charAt(i) - '0';
            if (doblar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }

    /** La marca por el prefijo del numero (IIN); TARJETA si no se reconoce. */
    static String marcaDe(String cifras) {
        int dos = Integer.parseInt(cifras.substring(0, 2));
        int tres = Integer.parseInt(cifras.substring(0, 3));
        int cuatro = Integer.parseInt(cifras.substring(0, 4));
        if (cifras.startsWith("4")) {
            return "VISA";
        }
        if ((dos >= 51 && dos <= 55) || (cuatro >= 2221 && cuatro <= 2720)) {
            return "MASTERCARD";
        }
        if (dos == 34 || dos == 37) {
            return "AMEX";
        }
        if (dos == 36 || dos == 38 || (tres >= 300 && tres <= 305)) {
            return "DINERS";
        }
        if (cuatro == 6011 || dos == 65 || (tres >= 644 && tres <= 649)) {
            return "DISCOVER";
        }
        return "TARJETA";
    }
}

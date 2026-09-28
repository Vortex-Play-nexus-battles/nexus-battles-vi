package com.nexusbattles.ms_identidad.auth.codigos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Las cifras de los codigos: vigencia, intentos y frecuencia de envio.
 *
 * <p>El documento del curso pide codigos «de vigencia limitada» y «de un solo
 * uso» sin fijar cifras (RF-AUT-005 las deja [POR DEFINIR]). Las de aqui son
 * <b>PROVISIONALES</b> y cada una tiene su propiedad, sobreescribible por
 * variable de entorno (regla 10), para que el PO las cambie sin tocar
 * codigo:
 * <ul>
 *   <li>verificacion del correo: 1440 min (24 h), porque sin ella la cuenta no
 *       se puede usar y un correo puede tardar;</li>
 *   <li>restablecimiento: 30 min, porque devuelve el control de una cuenta;</li>
 *   <li>activacion administrativa: las 24 h que ya tenia
 *       ({@code app.seguridad.horas-expiracion-token});</li>
 *   <li>5 intentos fallidos por codigo, y lo anulan;</li>
 *   <li>reenvio de verificacion: 60 s entre dos y 5 por hora; solicitud de
 *       restablecimiento: 60 s entre dos y 3 por hora.</li>
 * </ul>
 */
@Component
public class PoliticaDeCodigos {

    private final int minutosVerificacion;
    private final int minutosRestablecimiento;
    private final int minutosActivacion;
    private final int intentosMaximos;
    private final Duration pausaVerificacion;
    private final int verificacionesPorHora;
    private final Duration pausaRestablecimiento;
    private final int restablecimientosPorHora;

    @Autowired
    public PoliticaDeCodigos(
            @Value("${identidad.verificacion.minutos-vigencia:1440}") int minutosVerificacion,
            @Value("${identidad.recuperacion.minutos-vigencia:${app.seguridad.minutos-expiracion-restablecimiento:30}}")
            int minutosRestablecimiento,
            @Value("${app.seguridad.horas-expiracion-token:24}") int horasActivacion,
            @Value("${identidad.codigos.intentos-maximos:5}") int intentosMaximos,
            @Value("${identidad.verificacion.segundos-entre-reenvios:60}") int segundosEntreVerificaciones,
            @Value("${identidad.verificacion.reenvios-por-hora:5}") int verificacionesPorHora,
            @Value("${identidad.recuperacion.segundos-entre-solicitudes:60}") int segundosEntreRestablecimientos,
            @Value("${identidad.recuperacion.solicitudes-por-hora:3}") int restablecimientosPorHora) {
        this.minutosVerificacion = alMenosUno(minutosVerificacion, "identidad.verificacion.minutos-vigencia");
        this.minutosRestablecimiento = alMenosUno(minutosRestablecimiento, "identidad.recuperacion.minutos-vigencia");
        this.minutosActivacion = alMenosUno(horasActivacion, "app.seguridad.horas-expiracion-token") * 60;
        this.intentosMaximos = alMenosUno(intentosMaximos, "identidad.codigos.intentos-maximos");
        this.pausaVerificacion = Duration.ofSeconds(noNegativo(segundosEntreVerificaciones,
                "identidad.verificacion.segundos-entre-reenvios"));
        this.verificacionesPorHora = alMenosUno(verificacionesPorHora, "identidad.verificacion.reenvios-por-hora");
        this.pausaRestablecimiento = Duration.ofSeconds(noNegativo(segundosEntreRestablecimientos,
                "identidad.recuperacion.segundos-entre-solicitudes"));
        this.restablecimientosPorHora = alMenosUno(restablecimientosPorHora,
                "identidad.recuperacion.solicitudes-por-hora");
    }

    /** Los valores por omision; para pruebas. */
    public static PoliticaDeCodigos porOmision() {
        return new PoliticaDeCodigos(1440, 30, 24, 5, 60, 5, 60, 3);
    }

    public int minutosVigencia(TipoCodigo tipo) {
        return switch (tipo) {
            case VERIFICACION -> minutosVerificacion;
            case RESTABLECIMIENTO -> minutosRestablecimiento;
            case ACTIVACION -> minutosActivacion;
        };
    }

    public int intentosMaximos() {
        return intentosMaximos;
    }

    /**
     * Minimo entre dos envios del mismo tipo a la misma cuenta. La activacion
     * no tiene limite: la emite un administrador, no un formulario publico.
     */
    public Duration pausaEntreEnvios(TipoCodigo tipo) {
        return switch (tipo) {
            case VERIFICACION -> pausaVerificacion;
            case RESTABLECIMIENTO -> pausaRestablecimiento;
            case ACTIVACION -> Duration.ZERO;
        };
    }

    public int maximoPorHora(TipoCodigo tipo) {
        return switch (tipo) {
            case VERIFICACION -> verificacionesPorHora;
            case RESTABLECIMIENTO -> restablecimientosPorHora;
            case ACTIVACION -> Integer.MAX_VALUE;
        };
    }

    private static int alMenosUno(int valor, String propiedad) {
        if (valor < 1) {
            throw new IllegalArgumentException(propiedad + " debe ser al menos 1 (vale " + valor + ")");
        }
        return valor;
    }

    private static int noNegativo(int valor, String propiedad) {
        if (valor < 0) {
            throw new IllegalArgumentException(propiedad + " no puede ser negativo (vale " + valor + ")");
        }
        return valor;
    }
}

package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.time.Clock;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.SondaDeActuator;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ConfiguracionDelSondeo;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cablea la pantalla «Sistema» (RFINAL-08).
 *
 * <p>Su sonda NO es la del monitor de disponibilidad: es otra instancia de la
 * misma clase, con los plazos cortos del sondeo interactivo
 * ({@code sondeo.conexion-ms} y {@code sondeo.respuesta-ms}). No se publica
 * como bean para que no compita con la del monitor por el tipo
 * {@code SondaDeSalud}, y para que acortar la espera de esta pantalla no
 * mueva la cifra de HU-DIS-001.
 */
@Configuration
public class ConfiguracionDeLaPantallaDelSistema {

    @Bean
    EstadoDelSistema estadoDelSistema(ConfiguracionDelSistema sistema, ConfiguracionDelSondeo sondeo,
                                      RondaEnParalelo ronda, Clock reloj) {
        return new EstadoDelSistema(sistema,
                SondaDeActuator.conPlazos(sondeo.plazoDeConexion(), sondeo.plazoDeRespuesta()),
                ronda, new ResultadoReciente<>(sondeo.vigencia()), reloj);
    }
}

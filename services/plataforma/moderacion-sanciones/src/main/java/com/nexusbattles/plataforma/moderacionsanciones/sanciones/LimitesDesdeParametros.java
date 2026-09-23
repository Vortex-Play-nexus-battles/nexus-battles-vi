package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Los limites de sancion, leidos del catalogo de admin-parametros
 * (HU-ADM-001 CA-04) con el valor de las variables de entorno como respaldo.
 *
 * <p>La cache corta, el respaldo y la captura del fallo <b>ya no viven aqui</b>:
 * son {@link LectorDeParametros}, en {@code plataforma-resiliencia}. Esta clase
 * se queda con lo unico que es de este dominio —que clave corresponde a que
 * limite y en que unidad esta escrita— y no repite la politica de degradacion,
 * que tiene que ser la misma en los veinte modulos.
 *
 * <p>El comportamiento observable no cambio al extraerla: si el catalogo no
 * responde o el Product Owner no ha fijado el parametro, se usa el respaldo y
 * queda anotado en la bitacora.
 */
public class LimitesDesdeParametros implements LimitesDeSancion {

    static final String MINIMA = "sanciones.suspension.minima-horas";
    static final String MAXIMA = "sanciones.suspension.maxima-dias";
    static final String PLAZO = "sanciones.apelacion.plazo-dias";

    private final LectorDeParametros parametros;
    private final LimitesDeSancion respaldo;

    public LimitesDesdeParametros(LectorDeParametros parametros, LimitesDeSancion respaldo) {
        this.parametros = Objects.requireNonNull(parametros, "hace falta el lector de parametros");
        this.respaldo = Objects.requireNonNull(respaldo, "hace falta el respaldo de las variables de entorno");
    }

    /**
     * Atajo para quien ya tiene el cliente HTTP y la base del catalogo a mano
     * —el cableado del servicio y las pruebas—: arma el lector compartido con
     * esos dos datos.
     */
    public LimitesDesdeParametros(RestClient http, String base, LimitesDeSancion respaldo, Clock reloj,
                                  Duration vigenciaDeCache) {
        this(LectorDeParametros.sobre(http, base, reloj, vigenciaDeCache), respaldo);
    }

    @Override
    public Duration suspensionMinima() {
        return Duration.ofHours(parametros.entero(MINIMA, respaldo.suspensionMinima().toHours()));
    }

    @Override
    public Duration suspensionMaxima() {
        return Duration.ofDays(parametros.entero(MAXIMA, respaldo.suspensionMaxima().toDays()));
    }

    @Override
    public Duration plazoDeApelacion() {
        return Duration.ofDays(parametros.entero(PLAZO, respaldo.plazoDeApelacion().toDays()));
    }
}

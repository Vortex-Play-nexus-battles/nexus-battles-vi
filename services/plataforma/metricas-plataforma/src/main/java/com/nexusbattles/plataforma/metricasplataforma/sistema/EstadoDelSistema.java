package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.SondaDeSalud;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.MotivoDeFallo;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;

/**
 * Estado de los servicios para la pantalla «Sistema» de la consola.
 *
 * <h2>Por que lo sirve el backend y no lo pregunta el navegador</h2>
 *
 * La tentacion es que el panel consulte el {@code /actuator/health} de cada
 * servicio. Eso obligaria a publicar en el navegador los puertos internos y
 * el mapa de la red, y a abrir esos puertos al exterior: la pantalla que
 * cuenta si el sistema esta sano seria la que mas lo expone. Aqui las sondas
 * salen de dentro de la red de despliegue y al navegador solo llega un
 * nombre, un estado y una fecha.
 *
 * <h2>Cinco estados</h2>
 *
 * OPERATIVO y CAIDO son evidentes. LENTO (RFINAL-08) es el que conecto y no
 * contesto a tiempo. NO_DESPLEGADO y NO_OBSERVABLE son los que evitan mentir:
 * un servicio fuera del host a proposito, o en otro host sin sonda en este
 * entorno. Pintarlos de rojo diria que algo se rompio; omitirlos diria que no
 * existen. Se dicen con su nombre.
 *
 * <h2>Cuanto tarda (RFINAL-08)</h2>
 *
 * Hasta el 4-oct las trece sondas iban una detras de otra en el hilo de la
 * peticion: 8440 ms medidos en DEV. Ahora van todas a la vez en la
 * {@link RondaEnParalelo} (ejecutor acotado, tope para la ronda, sin
 * reintentos) y la ronda se reutiliza mientras este vigente
 * ({@link ResultadoReciente}): la consola pide este estado dos veces al
 * cargar y la segunda ya no sondea. La respuesta lleva la hora de la ronda y
 * si es reutilizada.
 *
 * <p>Solo lee. No escribe en el registro de disponibilidad ni produce
 * metricas: la cifra de HU-DIS-001 sigue saliendo de su propia lista y de su
 * propio monitor, que DEC-01 define aparte.
 */
public final class EstadoDelSistema {

    private final ConfiguracionDelSistema configuracion;
    private final SondaDeSalud sonda;
    private final RondaEnParalelo ronda;
    private final ResultadoReciente<RespuestaDelSistema> reciente;
    private final Clock reloj;

    public EstadoDelSistema(ConfiguracionDelSistema configuracion, SondaDeSalud sonda, RondaEnParalelo ronda,
                            ResultadoReciente<RespuestaDelSistema> reciente, Clock reloj) {
        this.configuracion = configuracion;
        this.sonda = sonda;
        this.ronda = ronda;
        this.reciente = reciente;
        this.reloj = reloj;
    }

    public RespuestaDelSistema consultar() {
        ResultadoReciente.Lectura<RespuestaDelSistema> lectura = reciente.obtener(this::sondear);
        return lectura.desdeCache() ? lectura.valor().reutilizada() : lectura.valor();
    }

    private RespuestaDelSistema sondear() {
        Instant ahora = reloj.instant();
        List<Map.Entry<String, String>> entradas = List.copyOf(configuracion.servicios().entrySet());
        List<EstadoDeServicio> estados = ronda.ejecutar(entradas,
                entrada -> estadoDe(entrada.getKey(), entrada.getValue(), ahora),
                (entrada, fallo) -> new EstadoDeServicio(entrada.getKey(), EstadoDeServicio.CAIDO,
                        "la sonda falló: " + MotivoDeFallo.describir(fallo, null, null), ahora),
                entrada -> new EstadoDeServicio(entrada.getKey(), EstadoDeServicio.LENTO,
                        "No respondió en " + ronda.plazo().toMillis()
                                + " ms; no se espera más para no retrasar al resto.",
                        ahora));
        return RespuestaDelSistema.de(estados, ahora);
    }

    private EstadoDeServicio estadoDe(String servicio, String url, Instant ahora) {
        String valor = url == null ? "" : url.trim();

        if (valor.isEmpty() || ConfiguracionDelSistema.NO_DESPLEGADO.equalsIgnoreCase(valor)) {
            return new EstadoDeServicio(servicio, EstadoDeServicio.NO_DESPLEGADO,
                    "No se despliega en este entorno (por capacidad o por catálogo); su imagen se publica y se "
                            + "levanta a demanda.",
                    ahora);
        }

        if (ConfiguracionDelSistema.NO_OBSERVABLE.equalsIgnoreCase(valor)) {
            return new EstadoDeServicio(servicio, EstadoDeServicio.NO_OBSERVABLE,
                    "Vive en otro host y aquí no tiene sonda configurada: no se puede afirmar si está arriba o "
                            + "caído.",
                    ahora);
        }

        Comprobacion comprobacion = sonda.comprobar(servicio, valor, ahora);
        String estado = comprobacion.disponible()
                ? EstadoDeServicio.OPERATIVO
                : comprobacion.esperaAgotada() ? EstadoDeServicio.LENTO : EstadoDeServicio.CAIDO;
        return new EstadoDeServicio(servicio, estado, comprobacion.detalle(), comprobacion.instante());
    }
}

package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.SondaDeSalud;

/**
 * Estado de los servicios para la pantalla «Sistema» de la consola.
 *
 * ## Por que lo sirve el backend y no lo pregunta el navegador
 *
 * La tentacion es que el panel consulte el `/actuator/health` de cada
 * servicio. Eso obligaria a publicar en el navegador los puertos internos y
 * el mapa de la red, y a abrir esos puertos al exterior: la pantalla que
 * cuenta si el sistema esta sano seria la que mas lo expone. Aqui las sondas
 * salen de dentro de la red de despliegue y al navegador solo llega un
 * nombre, un estado y una fecha.
 *
 * ## Tres estados, y el tercero importa
 *
 * OPERATIVO y CAIDO son evidentes. NO_DESPLEGADO es el que evita mentir: hay
 * servicios que estan fuera del host a proposito, por capacidad medida
 * (infrastructure/despliegue/CAPACIDAD.md). Pintarlos de rojo diria que algo
 * se rompio; omitirlos diria que no existen. Se dicen con su nombre.
 *
 * Solo lee. No escribe en el registro de disponibilidad ni produce metricas:
 * la cifra de HU-DIS-001 sigue saliendo de su propia lista, que DEC-01 define
 * aparte.
 */
@RestController
@RequestMapping("/api/v1/admin/sistema")
public class SistemaController {

    private final ConfiguracionDelSistema configuracion;
    private final SondaDeSalud sonda;
    private final Clock reloj;

    public SistemaController(ConfiguracionDelSistema configuracion, SondaDeSalud sonda, Clock reloj) {
        this.configuracion = configuracion;
        this.sonda = sonda;
        this.reloj = reloj;
    }

    @GetMapping("/servicios")
    public RespuestaDelSistema servicios() {
        Instant ahora = reloj.instant();
        List<EstadoDeServicio> estados = configuracion.servicios().entrySet().stream()
                .map(entrada -> estadoDe(entrada, ahora))
                .toList();

        long operativos = estados.stream().filter(e -> EstadoDeServicio.OPERATIVO.equals(e.estado())).count();
        long caidos = estados.stream().filter(e -> EstadoDeServicio.CAIDO.equals(e.estado())).count();
        long fuera = estados.stream().filter(e -> EstadoDeServicio.NO_DESPLEGADO.equals(e.estado())).count();

        return new RespuestaDelSistema(estados, (int) operativos, (int) caidos, (int) fuera, ahora);
    }

    private EstadoDeServicio estadoDe(Map.Entry<String, String> entrada, Instant ahora) {
        String servicio = entrada.getKey();
        String url = entrada.getValue();

        if (url == null || url.isBlank() || ConfiguracionDelSistema.NO_DESPLEGADO.equalsIgnoreCase(url.trim())) {
            return new EstadoDeServicio(servicio, EstadoDeServicio.NO_DESPLEGADO,
                    "Fuera del host por capacidad medida; su imagen se publica y se despliega a demanda.",
                    ahora);
        }

        Comprobacion comprobacion = sonda.comprobar(servicio, url, ahora);
        return new EstadoDeServicio(
                servicio,
                comprobacion.disponible() ? EstadoDeServicio.OPERATIVO : EstadoDeServicio.CAIDO,
                comprobacion.detalle(),
                comprobacion.instante());
    }

    /**
     * @param servicios     uno por servicio, en el orden de la configuracion
     * @param operativos    cuantos responden
     * @param caidos        cuantos deberian responder y no lo hacen
     * @param noDesplegados cuantos estan fuera a proposito
     * @param instante      cuando se hizo esta ronda
     */
    public record RespuestaDelSistema(
            List<EstadoDeServicio> servicios,
            int operativos,
            int caidos,
            int noDesplegados,
            Instant instante) { }
}

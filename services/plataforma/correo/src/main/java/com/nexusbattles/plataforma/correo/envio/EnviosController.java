package com.nexusbattles.plataforma.correo.envio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La evidencia de entrega que pide RF-COR-001.
 *
 * <p>Un 202 del envio dice que la peticion se acepto. Esta ruta dice que el
 * servidor de correo acepto el mensaje, con que identificador y cuando. Son
 * dos hechos distintos y hasta R18 solo se podia comprobar el primero: por eso
 * el sistema pudo estar semanas "enviando correos" que nadie recibia.
 *
 * <p>Ademas dice <b>a donde</b> se esta enviando. Ese dato es el que descubre
 * de un vistazo la causa real: un buzon de pruebas en el puerto 1025 recoge
 * todo y no lo reenvia a ninguna parte. Aparece como {@code buzonDePruebas}.
 *
 * <p>Nunca devuelve direcciones completas ni el cuerpo de ningun mensaje.
 */
@RestController
@RequestMapping("/api/v1/correos/envios")
public class EnviosController {

    private final RegistroDeEnvios registro;
    private final ConfiguracionDeCorreo configuracion;
    private final BuzonDePruebas buzon;
    private final String servidor;
    private final int puerto;
    private final boolean autentica;
    private final boolean tls;

    public EnviosController(
            RegistroDeEnvios registro,
            ConfiguracionDeCorreo configuracion,
            BuzonDePruebas buzon,
            // Como texto y no como int/boolean: una variable vacia en el .env
            // (SMTP_PORT=, SMTP_TLS=, tal como vienen en .env.example) llega
            // como cadena vacia y, en un int o boolean de @Value, impide que el
            // servicio arranque. Ver BuzonDePruebas#puertoDe.
            @Value("${spring.mail.host:}") String servidor,
            @Value("${spring.mail.port:}") String puerto,
            @Value("${spring.mail.properties.mail.smtp.auth:}") String autentica,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:}") String tls) {
        this.registro = registro;
        this.configuracion = configuracion;
        this.buzon = buzon;
        this.servidor = servidor == null ? "" : servidor;
        this.puerto = BuzonDePruebas.puertoDe(puerto, 0);
        this.autentica = activado(autentica);
        this.tls = activado(tls);
    }

    private static boolean activado(String texto) {
        return texto != null && Boolean.parseBoolean(texto.trim());
    }

    @GetMapping
    public EstadoDeEntrega estado(@RequestParam(name = "ultimos", defaultValue = "25") int ultimos) {
        return new EstadoDeEntrega(
                servidor,
                puerto,
                autentica,
                tls,
                BuzonDePruebas.esPuertoDeBuzon(puerto),
                buzon.descripcion(),
                configuracion.remitente(),
                registro.aceptados(),
                registro.desviados(),
                registro.rechazados(),
                registro.omitidos(),
                registro.ultimos(Math.min(ultimos, 100)));
    }

    /**
     * @param servidor        a donde se envia
     * @param puerto          por que puerto
     * @param autentica       si se autentica contra el servidor
     * @param tls             si la conexion se cifra con STARTTLS
     * @param buzonDePruebas  true cuando el servidor principal es un recogedor
     *                        local que NO reenvia a ninguna bandeja real
     * @param desvioDePruebas a donde van las direcciones reservadas para
     *                        pruebas (RFC 2606); vacio si no salen
     * @param remitente       cabecera From configurada
     * @param aceptados       cuantos mensajes acepto el servidor principal
     * @param desviados       cuantos acepto el buzon de pruebas
     * @param rechazados      cuantos se rechazaron
     * @param omitidos        cuantos no salieron a proposito
     * @param recientes       los ultimos, del mas nuevo al mas viejo
     */
    public record EstadoDeEntrega(
            String servidor,
            int puerto,
            boolean autentica,
            boolean tls,
            boolean buzonDePruebas,
            String desvioDePruebas,
            String remitente,
            long aceptados,
            long desviados,
            long rechazados,
            long omitidos,
            List<EnvioRegistrado> recientes) {}
}
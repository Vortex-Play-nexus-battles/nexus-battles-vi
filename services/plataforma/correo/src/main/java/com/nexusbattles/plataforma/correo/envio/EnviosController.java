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

    /** Puertos de los recogedores de correo de desarrollo (Mailpit, MailHog). */
    private static final List<Integer> PUERTOS_DE_BUZON_DE_PRUEBAS = List.of(1025, 1026, 8025);

    private final RegistroDeEnvios registro;
    private final ConfiguracionDeCorreo configuracion;
    private final String servidor;
    private final int puerto;
    private final boolean autentica;
    private final boolean tls;

    public EnviosController(
            RegistroDeEnvios registro,
            ConfiguracionDeCorreo configuracion,
            @Value("${spring.mail.host:}") String servidor,
            @Value("${spring.mail.port:0}") int puerto,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean autentica,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean tls) {
        this.registro = registro;
        this.configuracion = configuracion;
        this.servidor = servidor;
        this.puerto = puerto;
        this.autentica = autentica;
        this.tls = tls;
    }

    @GetMapping
    public EstadoDeEntrega estado(@RequestParam(name = "ultimos", defaultValue = "25") int ultimos) {
        return new EstadoDeEntrega(
                servidor,
                puerto,
                autentica,
                tls,
                PUERTOS_DE_BUZON_DE_PRUEBAS.contains(puerto),
                configuracion.remitente(),
                registro.aceptados(),
                registro.rechazados(),
                registro.ultimos(Math.min(ultimos, 100)));
    }

    /**
     * @param servidor       a donde se envia
     * @param puerto         por que puerto
     * @param autentica      si se autentica contra el servidor
     * @param tls            si la conexion se cifra con STARTTLS
     * @param buzonDePruebas true cuando el destino es un recogedor local que
     *                       NO reenvia a ninguna bandeja real
     * @param remitente      cabecera From configurada
     * @param aceptados      cuantos mensajes acepto el servidor
     * @param rechazados     cuantos rechazo
     * @param recientes      los ultimos, del mas nuevo al mas viejo
     */
    public record EstadoDeEntrega(
            String servidor,
            int puerto,
            boolean autentica,
            boolean tls,
            boolean buzonDePruebas,
            String remitente,
            long aceptados,
            long rechazados,
            List<EnvioRegistrado> recientes) {}
}
package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.EnvioRegistrado;
import com.nexusbattles.plataforma.correo.cola.EstadoDeEnvio;
import com.nexusbattles.plataforma.correo.cola.RepositorioDeEnvios;
import com.nexusbattles.plataforma.correo.envio.BuzonDePruebas;
import com.nexusbattles.plataforma.correo.envio.ConfiguracionDeCorreo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * La evidencia de entrega que pide RF-COR-001 ({@code GET /correos/envios}).
 *
 * <p>Un 202 del envio dice que el correo se guardo en la cola. Esta ruta dice
 * que paso despues: si el servidor de correo lo acepto, con que identificador
 * y cuando, o por que sigue esperando. Desde la 1.4.0 lee la tabla de la cola,
 * asi que los contadores y la lista ya no se pierden al reiniciar (antes solo
 * veia desde el ultimo arranque).
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

    /** Tope de {@code ultimos} (contrato: 1..100). */
    static final int MAXIMO_RECIENTES = 100;

    private final RepositorioDeEnvios repositorio;
    private final ConfiguracionDeCorreo configuracion;
    private final BuzonDePruebas buzon;
    private final String servidor;
    private final int puerto;
    private final boolean autentica;
    private final boolean tls;

    public EnviosController(
            RepositorioDeEnvios repositorio,
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
        this.repositorio = repositorio;
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

    /**
     * @param ultimos cuantos envios recientes devolver (se ajusta a 1..100)
     * @param estado  solo los de ese estado (1.4.0); sin el, todos
     */
    @GetMapping
    public EstadoDeEntrega estado(
            @RequestParam(name = "ultimos", defaultValue = "25") int ultimos,
            @RequestParam(name = "estado", required = false) EstadoDeEnvio estado) {
        Map<EstadoDeEnvio, Long> conteo = repositorio.contarPorEstado();
        return new EstadoDeEntrega(
                servidor,
                puerto,
                autentica,
                tls,
                BuzonDePruebas.esPuertoDeBuzon(puerto),
                buzon.descripcion(),
                configuracion.remitente(),
                cuantos(conteo, EstadoDeEnvio.ENVIADO),
                cuantos(conteo, EstadoDeEnvio.DESVIADO),
                cuantos(conteo, EstadoDeEnvio.FALLIDO),
                cuantos(conteo, EstadoDeEnvio.OMITIDO),
                cuantos(conteo, EstadoDeEnvio.PENDIENTE)
                        + cuantos(conteo, EstadoDeEnvio.ENVIANDO)
                        + cuantos(conteo, EstadoDeEnvio.ERROR_REINTENTABLE),
                repositorio.recientes(Math.clamp(ultimos, 1, MAXIMO_RECIENTES), estado));
    }

    private static long cuantos(Map<EstadoDeEnvio, Long> conteo, EstadoDeEnvio estado) {
        return conteo.getOrDefault(estado, 0L);
    }

    /**
     * Esquema {@code EstadoDeEntrega} del contrato. Los cuatro primeros
     * contadores conservan sus nombres anteriores a la cola, por compatibilidad.
     *
     * @param servidor        a donde se envia
     * @param puerto          por que puerto
     * @param autentica       si se autentica contra el servidor
     * @param tls             si la conexion se cifra con STARTTLS
     * @param buzonDePruebas  true cuando el servidor principal es un recogedor
     *                        local que NO reenvia a ninguna bandeja real
     * @param desvioDePruebas a donde van las direcciones reservadas para
     *                        pruebas (RFC 2606); vacio si no salen
     * @param remitente       cabecera From configurada (MAIL_FROM)
     * @param aceptados       ENVIADO: los que acepto el proveedor
     * @param desviados       DESVIADO: los que acepto el buzon de pruebas
     * @param rechazados      FALLIDO: rechazados sin remedio o sin intentos
     * @param omitidos        OMITIDO: los que no se enviaron a proposito
     * @param pendientes      PENDIENTE, ENVIANDO o ERROR_REINTENTABLE: en cola
     *                        o esperando reintento
     * @param recientes       los ultimos movimientos, del mas nuevo al mas viejo
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
            long pendientes,
            List<EnvioRegistrado> recientes) {}
}

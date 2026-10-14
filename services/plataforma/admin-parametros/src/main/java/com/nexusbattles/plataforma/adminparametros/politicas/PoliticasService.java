package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.plataforma.adminparametros.parametros.Actor;
import com.nexusbattles.plataforma.adminparametros.parametros.ParametroRechazado;
import com.nexusbattles.plataforma.adminparametros.parametros.ParametroRechazado.Motivo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Los cambios en las politicas de uso — SA-11, §7.3.7 («el sistema debe
 * notificar a los usuarios sobre cambios en politicas de uso que afecten al
 * usuario», RF-NOT-005).
 *
 * <p>Publicar es de administracion (Administrador o Super Administrador, como
 * cambiar un parametro) y lleva motivo: queda en la bitacora central (§7.3.5)
 * por el envio de {@link EnvioDeAuditoriaDePoliticas}. Leerlos es publico: es
 * lo que la bandeja de notificaciones lleva a cada jugador en su siguiente
 * ingreso, y lo que cualquiera puede consultar.
 */
@Service
public class PoliticasService {

    static final int TITULO_MINIMO = 5;
    static final int TITULO_MAXIMO = 120;
    static final int DESCRIPCION_MINIMA = 20;
    static final int DESCRIPCION_MAXIMA = 2000;
    static final int MOTIVO_MINIMO = 3;
    static final int MOTIVO_MAXIMO = 500;
    static final int HISTORIAL = 50;
    static final int LOTE_MAXIMO = 100;
    /** Una vigencia en el pasado se admite solo dentro de esta holgura (relojes de cliente). */
    static final Duration HOLGURA_DEL_PASADO = Duration.ofMinutes(5);

    private final CambioDePoliticaRepository cambios;
    private final Clock reloj;

    public PoliticasService(CambioDePoliticaRepository cambios, Clock reloj) {
        this.cambios = cambios;
        this.reloj = reloj;
    }

    /** Lo que pide quien publica. */
    public record Publicacion(String politica, String titulo, String descripcion, OffsetDateTime vigenteDesde,
                              String motivo) {
    }

    @Transactional
    public CambioDePolitica publicar(Actor actor, Publicacion pedido, String ipOrigen) {
        if (actor == null || !actor.puedeConfigurar()) {
            throw new ParametroRechazado(Motivo.PERMISO_INSUFICIENTE,
                    "Publicar un cambio en las políticas de uso es de la administración.");
        }
        Politica politica = politicaDe(pedido.politica());
        String titulo = texto(pedido.titulo(), TITULO_MINIMO, TITULO_MAXIMO, "El título");
        String descripcion = texto(pedido.descripcion(), DESCRIPCION_MINIMA, DESCRIPCION_MAXIMA,
                "Lo que cambia");
        String motivo = texto(pedido.motivo(), MOTIVO_MINIMO, MOTIVO_MAXIMO, "El motivo");
        // Microsegundos, la precision de TIMESTAMPTZ: la marca que se lee
        // despues como punto de lectura es la misma que se guardo.
        OffsetDateTime ahora = OffsetDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime vigencia = pedido.vigenteDesde() == null
                ? ahora : pedido.vigenteDesde().truncatedTo(ChronoUnit.MICROS);
        if (vigencia.isBefore(ahora.minus(HOLGURA_DEL_PASADO))) {
            throw new ParametroRechazado(Motivo.SOLICITUD_INVALIDA,
                    "La vigencia no puede estar en el pasado: los jugadores se enteran desde hoy.");
        }
        return cambios.save(new CambioDePolitica(UUID.randomUUID(), politica, titulo, descripcion, motivo,
                vigencia, actor.id(), ahora, ipOrigen));
    }

    /** El historial publicado, lo mas reciente primero. */
    @Transactional(readOnly = true)
    public List<CambioDePolitica> historial() {
        return cambios.findAllByOrderByPublicadoEnDescIdDesc(PageRequest.of(0, HISTORIAL));
    }

    /** Lo publicado despues de {@code desde}, en orden, hasta {@code limite}. */
    @Transactional(readOnly = true)
    public List<CambioDePolitica> posteriores(OffsetDateTime desde, int limite) {
        if (desde == null) {
            throw new ParametroRechazado(Motivo.SOLICITUD_INVALIDA, "Falta desde cuándo (desde).");
        }
        int tamano = Math.max(1, Math.min(limite, LOTE_MAXIMO));
        return cambios.posterioresA(desde, PageRequest.of(0, tamano));
    }

    private static Politica politicaDe(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ParametroRechazado(Motivo.SOLICITUD_INVALIDA, "Elige la política que cambia.");
        }
        try {
            return Politica.valueOf(valor.strip());
        } catch (IllegalArgumentException desconocida) {
            throw new ParametroRechazado(Motivo.SOLICITUD_INVALIDA, "Esa política no existe: " + valor + ".");
        }
    }

    private static String texto(String valor, int minimo, int maximo, String que) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.length() < minimo || limpio.length() > maximo) {
            throw new ParametroRechazado(Motivo.SOLICITUD_INVALIDA,
                    que + " debe tener entre " + minimo + " y " + maximo + " caracteres.");
        }
        return limpio;
    }
}

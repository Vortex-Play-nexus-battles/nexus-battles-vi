package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexusbattles.plataforma.comentarios.moderacion.ServicioDeModeracion.Aplicado;
import com.nexusbattles.plataforma.comentarios.moderacion.ServicioDeModeracion.DecisionIncompleta;
import com.nexusbattles.plataforma.comentarios.moderacion.ServicioDeModeracion.Resuelto;

/**
 * La moderacion en lote — HU-COM-008, CA-02: una accion sobre varios
 * comentarios, sin estados a medias y mostrando el resultado.
 *
 * <h2>Por que es una clase aparte y no otro metodo del servicio</h2>
 *
 * Porque el aviso y la auditoria tienen que ocurrir DESPUES de que la
 * transaccion del lote haya confirmado. Si {@code aplicarLote} los llamara
 * por dentro, un fallo al guardar el comentario 30 dejaria avisados y
 * auditados los 29 anteriores sobre algo que se deshizo. Aqui se llama a
 * {@code servicio.aplicarLote} a traves del bean (la transaccion es real, no
 * una llamada interna sin proxy) y solo cuando vuelve se avisa y se audita.
 *
 * <p>Aviso y auditoria son fail-open como en {@code resolver}: las interfaces
 * ya prometen no lanzar, pero aqui se atrapa lo inesperado de todos modos. El
 * lote ya esta confirmado; que el aviso 7 reviente no puede esconderle al
 * moderador el resultado ni dejar sin auditar los comentarios 8 en adelante.
 *
 * <h2>El corte de avisos</h2>
 *
 * El cliente que avisa espera hasta 2 s para conectar y 5 s para leer
 * ({@code comentarios.http}). Con notificaciones colgado, 50 avisos seguidos
 * serian minutos DESPUES de un commit que ya ocurrio, y el borde cortaria la
 * peticion: el moderador no veria un resultado que si se aplico. Por eso, tras
 * {@link #MAXIMO_AVISOS_FALLIDOS_SEGUIDOS} avisos fallidos SEGUIDOS (devolvio
 * {@code false} o lanzo) no se intentan mas avisos del lote y el resto queda
 * con {@code autorNotificado=false}. Un aviso que sale reinicia la cuenta.
 * MARCAR y DESMARCAR no avisan, asi que ni cuentan ni reinician.
 *
 * <p>Peor caso real tras el commit: hasta 3 llamadas de 7 s (2 s de conexion
 * mas 5 s de lectura, los del cliente {@code restClientComentarios} que usa el
 * aviso), unos 21 s.
 *
 * <p>La auditoria NO se corta, y hoy es cierto solo porque
 * {@code AUDITORIA_URL} esta sin poner en el despliegue
 * ({@code docker-compose.deploy.yml} la deja vacia): el adaptador solo escribe
 * la bitacora local y no espera a nadie. Cuando se configure, la auditoria
 * usara el mismo cliente y tendra el mismo riesgo que los avisos: habra que
 * cortarla igual.
 */
@Service
public class ModeracionEnLote {

    private static final Logger log = LoggerFactory.getLogger(ModeracionEnLote.class);

    /** Valor por omision del maximo y tope del contrato 1.8.0. */
    static final int MAXIMO_POR_OMISION = 50;

    /** Avisos fallidos seguidos tras los cuales el lote deja de avisar. No es configurable. */
    static final int MAXIMO_AVISOS_FALLIDOS_SEGUIDOS = 3;

    private final ServicioDeModeracion servicio;
    private final AvisoAlAutor aviso;
    private final RegistroDeAuditoria auditoria;
    private final int maximoPorLote;

    public ModeracionEnLote(
            ServicioDeModeracion servicio,
            AvisoAlAutor aviso,
            RegistroDeAuditoria auditoria,
            @Value("${comentarios.moderacion.maximo-por-lote:" + MAXIMO_POR_OMISION + "}") int maximoPorLote) {
        this.servicio = servicio;
        this.aviso = aviso;
        this.auditoria = auditoria;
        this.maximoPorLote = maximoPorLote;
    }

    /**
     * @throws DecisionIncompleta                    la peticion no cumple el contrato (400)
     * @throws ServicioDeModeracion.LoteRechazado    alguno no se puede resolver (409); no cambio nada
     */
    public Lote resolver(List<String> comentarioIds, String moderadorId, String apodoModerador,
            AccionDeModeracion accion, String motivo, String ipOrigen) {

        exigirPeticionValida(comentarioIds, accion, motivo);

        List<Aplicado> aplicados = servicio.aplicarLote(
                comentarioIds, moderadorId, apodoModerador, accion, motivo, ipOrigen);

        // A partir de aqui el lote esta confirmado: nada de lo que sigue lo deshace.
        // Con el servicio de avisos caido se corta tras 3 fallos seguidos (ver la clase).
        List<Resuelto> resultados = new ArrayList<>();
        int fallosSeguidos = 0;
        for (Aplicado a : aplicados) {
            boolean avisado = false;
            if (accion.seAvisaAlAutor() && fallosSeguidos < MAXIMO_AVISOS_FALLIDOS_SEGUIDOS) {
                avisado = avisar(a);
                fallosSeguidos = avisado ? 0 : fallosSeguidos + 1;
            }
            auditar(a);
            resultados.add(new Resuelto(a.comentario(), a.asiento(), avisado));
        }
        return new Lote(resultados.size(), resultados);
    }

    private boolean avisar(Aplicado a) {
        try {
            return aviso.notificar(a.comentario(), a.asiento());
        } catch (RuntimeException fallo) {
            log.warn("El aviso de moderacion en lote sobre {} no salio: {}",
                    a.comentario().id(), fallo.getMessage());
            return false;
        }
    }

    private void auditar(Aplicado a) {
        try {
            auditoria.registrar(a.asiento());
        } catch (RuntimeException fallo) {
            log.warn("La auditoria del lote sobre {} no salio: {}",
                    a.comentario().id(), fallo.getMessage());
        }
    }

    private void exigirPeticionValida(List<String> ids, AccionDeModeracion accion, String motivo) {
        if (accion == null) {
            throw new DecisionIncompleta("Falta la accion de moderacion");
        }
        if (accion == AccionDeModeracion.EDITAR) {
            throw new DecisionIncompleta(
                    "EDITAR no va en lote: cada comentario necesita su propio textoNuevo");
        }
        if (motivo == null || motivo.strip().length() < ServicioDeModeracion.MOTIVO_MINIMO
                || motivo.length() > ServicioDeModeracion.MOTIVO_MAXIMO) {
            throw new ServicioDeModeracion.MotivoRequerido();
        }
        if (ids == null || ids.isEmpty()) {
            throw new DecisionIncompleta("El lote necesita al menos un comentario");
        }
        if (ids.size() > maximoPorLote) {
            throw new DecisionIncompleta(
                    "Un lote admite hasta " + maximoPorLote + " comentarios y llegaron " + ids.size());
        }
        Set<String> vistos = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new DecisionIncompleta("El lote trae un identificador de comentario en blanco");
            }
            if (!vistos.add(id)) {
                throw new DecisionIncompleta("El comentario " + id + " esta repetido en el lote");
            }
        }
    }

    /** El lote resuelto: un resultado por comentario, en el orden pedido. */
    public record Lote(int total, List<Resuelto> resultados) {
    }
}

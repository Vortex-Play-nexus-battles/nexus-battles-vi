package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.analitica.PeriodoDelPanel;
import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import com.nexusbattles.ms_chatbot.chat.repository.BrechaConocimientoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.UUID;

// ms-chatbot.yaml 1.7.0 (7.4.5 «registro de preguntas frecuentes no
// cubiertas», «analisis de patrones de consulta»; RF-CHA-011): el
// administrador ve las preguntas sin respuesta y las atiende. La ruta
// (/chatbot/admin/**) ya exige rol ADMINISTRADOR o SUPER_ADMINISTRADOR en
// SecurityConfig.
//
// Las brechas las siguen creando y contando BrechaConocimientoService (al
// escalar o al calificar «no util»); aqui solo se leen y se les cambia el
// estado. Una ATENDIDA que se vuelve a preguntar la reabre la propia brecha
// (BrechaConocimiento.ocurrio), no este servicio.
@Service
public class BrechasAdminService {

    static final int TAMANO_MAXIMO = 100;
    private static final String NO_ENCONTRADA = "No existe una pregunta sin respuesta con ese identificador.";

    private final BrechaConocimientoRepository brechas;
    private final Clock reloj;

    public BrechasAdminService(BrechaConocimientoRepository brechas, Clock reloj) {
        this.brechas = brechas;
        this.reloj = reloj;
    }

    /**
     * Una página de las brechas con actividad en el periodo, las más repetidas
     * primero; con {@code estado} null, de todos los estados.
     */
    @Transactional(readOnly = true)
    public Page<BrechaConocimiento> listar(PeriodoDelPanel periodo, EstadoBrecha estado, int pagina, int tamano) {
        PeriodoDelPanel.validar(periodo.desde(), periodo.hasta());
        Pageable pedida = PageRequest.of(Math.max(0, pagina), Math.clamp(tamano, 1, TAMANO_MAXIMO));
        return estado == null
            ? brechas.buscarConActividadEntre(periodo.inicio(), periodo.fin(), pedida)
            : brechas.buscarConActividadEntre(estado, periodo.inicio(), periodo.fin(), pedida);
    }

    /**
     * Le pone {@code estado} a la brecha, con quién (el uid del token del
     * administrador, que resuelve el controlador) y cuándo. Con el mismo
     * estado que ya tiene no cambia nada.
     *
     * @throws ResponseStatusException 404 si no existe
     */
    @Transactional
    public BrechaConocimiento cambiarEstado(UUID brechaId, EstadoBrecha estado, String quien) {
        BrechaConocimiento brecha = brechas.findById(brechaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NO_ENCONTRADA));
        if (brecha.cambiarEstado(estado, quien, reloj.instant())) {
            return brechas.save(brecha);
        }
        return brecha;
    }
}

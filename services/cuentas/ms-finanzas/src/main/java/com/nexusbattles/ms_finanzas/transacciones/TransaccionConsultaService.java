package com.nexusbattles.ms_finanzas.transacciones;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lector del historial de transacciones (HU-PAG-002). Devuelve páginas ya
 * mapeadas a {@link ResumenTransaccion} para que la capa REST no acople sus
 * DTOs a la entidad JPA.
 *
 * <p>El controlador que consume este servicio se agrega en el PR siguiente,
 * junto con la integración JWT que aporta el {@code uidUsuario}.
 */
@Service
public class TransaccionConsultaService {

    private final TransaccionRepository repositorio;

    public TransaccionConsultaService(TransaccionRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Page<ResumenTransaccion> listarPorUsuario(String uidUsuario, Pageable pagina) {
        return repositorio.findByUidUsuarioOrderByCreadoDesc(uidUsuario, pagina)
                .map(ResumenTransaccion::desde);
    }
}

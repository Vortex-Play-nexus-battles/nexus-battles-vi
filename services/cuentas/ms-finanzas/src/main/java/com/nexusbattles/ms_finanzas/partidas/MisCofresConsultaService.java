package com.nexusbattles.ms_finanzas.partidas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lector del historial de cofres del jugador (HU-JUE-012). Alimenta la
 * pantalla "Mis cofres" accesible desde Mi Cuenta.
 */
@Service
public class MisCofresConsultaService {

    private final CofreEntregadoRepository repositorio;

    public MisCofresConsultaService(CofreEntregadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Page<ResumenCofre> listarPorUsuario(String uidJugador, Pageable pagina) {
        return repositorio.findByUidJugadorOrderByEntregadoEnDesc(uidJugador, pagina)
                .map(ResumenCofre::desde);
    }
}

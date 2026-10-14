package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * El resumen del historial de sanciones de una cuenta — SA-3 (§7.3.4, filtro
 * del directorio por historial de sanciones; ms-identidad-admin.yaml 1.7.0).
 *
 * <p>La fuente de verdad es moderacion-sanciones. Aqui se guarda lo que manda,
 * tal cual: el total y la fecha de la ultima, valores absolutos y no
 * incrementos, para que reintentar o reconciliar no cuente dos veces.
 */
@Service
public class HistorialDeSancionesService {

    private final UsuarioRepository usuarios;
    private final Clock reloj;

    @Autowired
    public HistorialDeSancionesService(UsuarioRepository usuarios) {
        this(usuarios, Clock.systemDefaultZone());
    }

    HistorialDeSancionesService(UsuarioRepository usuarios, Clock reloj) {
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /** {@code PUT /internal/usuarios/{uid}/historial-sanciones}. Idempotente. */
    @Transactional
    public HistorialDeSancionesResponse fijar(UUID uid, HistorialDeSancionesRequest historial) {
        if (historial == null || historial.total() == null || historial.total() < 0) {
            throw new ProyeccionInvalidaException("El total de sanciones es obligatorio y no puede ser negativo.");
        }
        if (historial.total() > 0 && historial.ultimaEn() == null) {
            throw new ProyeccionInvalidaException("Con sanciones, hace falta la fecha de la ultima ('ultimaEn').");
        }
        Usuario usuario = usuarios.findByPublicId(uid).orElseThrow(CuentaNoEncontradaException::new);
        usuario.setSancionesRegistradas(historial.total());
        usuario.setUltimaSancionEn(historial.total() == 0 ? null
                : historial.ultimaEn().atZoneSameInstant(reloj.getZone()).toLocalDateTime());
        usuarios.save(usuario);
        return new HistorialDeSancionesResponse(usuario.getPublicId(), usuario.getSancionesRegistradas(),
                historial.total() == 0 ? null : historial.ultimaEn());
    }

    /** Lo que quedo guardado. */
    public record HistorialDeSancionesResponse(UUID uid, int total, java.time.OffsetDateTime ultimaEn) {
    }
}

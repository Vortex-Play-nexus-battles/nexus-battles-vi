package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Intentos fallidos y bloqueo temporal (RF-AUT-009, 7.3.9).
 *
 * <p>Tras {@code umbral} fallos seguidos la cuenta queda bloqueada
 * {@code minutos} minutos. Un acceso correcto pone el contador a cero
 * (LoginService). Lo que se corrige aqui: cuando el bloqueo YA VENCIO sin que
 * hubiera un acceso correcto, el contador seguia en el umbral y el siguiente
 * fallo —uno solo— volvia a bloquear la cuenta otros quince minutos. Ahora un
 * bloqueo vencido empieza la cuenta de cero.
 */
@Service
public class IntentosFallidosService {

    @Value("${app.seguridad.umbral-intentos-fallidos:5}")
    private int umbralIntentosFallidos;

    @Value("${app.seguridad.minutos-bloqueo:15}")
    private int minutosBloqueo;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Clock reloj = Clock.systemDefaultZone();

    /** Para las pruebas: un reloj fijo y los umbrales sin contexto de Spring. */
    public void configurar(UsuarioRepository repositorio, int umbral, int minutos, Clock reloj) {
        this.usuarioRepository = repositorio;
        this.umbralIntentosFallidos = umbral;
        this.minutosBloqueo = minutos;
        this.reloj = reloj;
    }

    /**
     * Anota un fallo. REQUIRES_NEW: esta transaccion se confirma sola,
     * independiente de si el metodo que la llamo termina lanzando una
     * excepcion despues.
     *
     * @return {@code true} si este fallo acaba de bloquear la cuenta (para
     *         dejarlo en la auditoria una sola vez)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarIntentoFallido(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + usuarioId));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (usuario.getBloqueadoHasta() != null && !ahora.isBefore(usuario.getBloqueadoHasta())) {
            // El bloqueo anterior ya vencio: se empieza a contar de nuevo.
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
        }

        int intentos = usuario.getIntentosFallidos() + 1;
        usuario.setIntentosFallidos(intentos);

        boolean bloqueaAhora = false;
        if (intentos >= umbralIntentosFallidos && usuario.getBloqueadoHasta() == null) {
            usuario.setBloqueadoHasta(ahora.plusMinutes(minutosBloqueo));
            bloqueaAhora = true;
        }

        usuarioRepository.save(usuario);
        return bloqueaAhora;
    }
}

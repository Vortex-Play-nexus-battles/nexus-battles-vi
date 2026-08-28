package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class IntentosFallidosService {

    @Value("${app.seguridad.umbral-intentos-fallidos:5}")
    private int umbralIntentosFallidos;

    @Value("${app.seguridad.minutos-bloqueo:15}")
    private int minutosBloqueo;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // REQUIRES_NEW: esta transacción se confirma sola, independiente de si el
    // método que la llamó termina lanzando una excepción después.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntentoFallido(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + usuarioId));

        int intentos = usuario.getIntentosFallidos() + 1;
        usuario.setIntentosFallidos(intentos);

        if (intentos >= umbralIntentosFallidos) {
            usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(minutosBloqueo));
        }

        usuarioRepository.save(usuario);
    }
}

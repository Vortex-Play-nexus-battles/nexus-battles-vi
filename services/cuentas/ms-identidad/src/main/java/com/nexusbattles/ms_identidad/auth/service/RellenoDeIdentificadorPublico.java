package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Asigna el identificador publico a los usuarios que ya existian antes de que
 * el campo se anadiera.
 *
 * <p>Hace falta porque el servicio todavia no tiene Flyway (R8 pendiente) y
 * corre con {@code ddl-auto=update}: Hibernate puede crear la columna, pero no
 * rellenarla, y no puede declararla NOT NULL sobre una tabla con filas. Los
 * usuarios nuevos reciben el UUID en el {@code @PrePersist} de
 * {@link Usuario}; este componente cubre a los anteriores.
 *
 * <p>Es idempotente: si no queda ninguno sin identificador, no toca la base de
 * datos. Cuando exista Flyway, la migracion puede hacer el relleno y este
 * componente se retira.
 *
 * <p>Un fallo aqui no tumba el arranque. Sin identificador, un usuario antiguo
 * podra seguir entrando y usando su cuenta; lo unico que no podra es operar en
 * los servicios que lo exigen, y eso es preferible a dejar el servicio de
 * identidad entero sin arrancar.
 */
@Component
public class RellenoDeIdentificadorPublico {

    private static final Logger log = LoggerFactory.getLogger(RellenoDeIdentificadorPublico.class);

    private final UsuarioRepository usuarioRepository;

    public RellenoDeIdentificadorPublico(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void rellenarLosQueFaltan() {
        try {
            List<Usuario> sinIdentificador = usuarioRepository.findByPublicIdIsNull();
            if (sinIdentificador.isEmpty()) {
                return;
            }

            for (Usuario usuario : sinIdentificador) {
                usuario.setPublicId(UUID.randomUUID());
            }
            usuarioRepository.saveAll(sinIdentificador);

            log.info("Se asigno identificador publico a {} usuarios que no lo tenian", sinIdentificador.size());
        } catch (RuntimeException e) {
            log.error("No se pudo rellenar el identificador publico de los usuarios antiguos. "
                    + "Sus tokens saldran sin el claim 'uid' hasta que se resuelva: {}", e.getMessage(), e);
        }
    }
}

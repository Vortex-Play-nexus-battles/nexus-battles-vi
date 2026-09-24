package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.AdminUsuarioDirectorioResponse;
import com.nexusbattles.ms_identidad.admin.dto.PaginaAdminResponse;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.rbac.model.Action;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directorio de jugadores para la consola administrativa.
 *
 * ## Por que es un endpoint y no una consulta del navegador
 *
 * La consola necesita responder «¿quien esta registrado y como esta su
 * cuenta?». La alternativa perezosa seria que el panel pidiera la lista
 * entera y filtrara en el cliente; eso traeria a cada carga todos los
 * usuarios del sistema al navegador, incluidos los correos de gente que el
 * administrador no estaba buscando. Se filtra y se pagina en el servidor.
 *
 * ## Por que no esta en AdminGestionUsuarioController
 *
 * Aquel gestiona UNA cuenta -- editar perfil, suspender, restablecer -- y
 * todo lo suyo muta estado. Esto solo lee y lista. Mezclarlos obligaria a
 * revisar cada cambio futuro preguntandose cual de las dos cosas se esta
 * tocando.
 *
 * Mismo permiso que la gestion de cuentas: quien puede abrir la ficha de un
 * jugador puede buscarla. El guarda es {@code @RequirePermission}, el mismo
 * de siempre; no hay una segunda matriz que se pueda desincronizar.
 */
@RestController
@RequestMapping("/api/v1/admin/jugadores")
public class AdminDirectorioController {

    /** Techo del tamano de pagina. Pedir 10.000 filas no es paginar. */
    private static final int TAMANO_MAXIMO = 100;

    private final UsuarioRepository usuarioRepository;

    public AdminDirectorioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * @param buscar texto libre; compara con apodo y correo. Vacio = todos.
     * @param page   pagina, desde 0
     * @param size   filas por pagina, tope {@value #TAMANO_MAXIMO}
     */
    @GetMapping
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public PaginaAdminResponse<AdminUsuarioDirectorioResponse> listar(
            @RequestParam(name = "buscar", required = false) String buscar,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        String filtro = (buscar == null || buscar.isBlank()) ? null : buscar.trim();
        int pagina = Math.max(page, 0);
        int tamano = Math.min(Math.max(size, 1), TAMANO_MAXIMO);

        Page<Usuario> resultado = usuarioRepository.buscarParaDirectorio(
                filtro, PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));

        return PaginaAdminResponse.desde(resultado, AdminUsuarioDirectorioResponse::desde);
    }
}

package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.plataforma.moderacionsanciones.seguridad.JerarquiaDeRoles;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code POST /api/v1/lista-negra/verificar} — moderacion-lista-negra.yaml 2.0.x.
 *
 * <p>Es publica (el formulario de registro avisa del apodo antes de enviarlo),
 * pero el detalle solo lo ve quien trae token de servicio o de moderacion.
 */
@RestController
@RequestMapping("/api/v1/lista-negra")
public class ListaNegraVerificacionController {

    private final VerificacionListaNegraService service;

    public ListaNegraVerificacionController(VerificacionListaNegraService service) {
        this.service = service;
    }

    @PostMapping("/verificar")
    public VerificacionListaNegraResponse verificar(@RequestBody VerificacionListaNegraRequest request,
                                                    Authentication autenticacion) {
        var resultado = service.verificar(request.texto(), request.contexto(),
                JerarquiaDeRoles.puedeVerDetalleDeListaNegra(autenticacion));
        return new VerificacionListaNegraResponse(resultado.aprobado(), resultado.accion(), resultado.motivo(),
                resultado.categoria(), resultado.coincidencias());
    }

    /** {@code VerificacionListaNegraRequest}; sin {@code contexto} es {@code GENERICO}. */
    public record VerificacionListaNegraRequest(String texto, ContextoDeTexto contexto) {
    }

    /** {@code VerificacionListaNegraResponse}: lo que no aplica no viaja. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerificacionListaNegraResponse(boolean aprobado, AccionDeModeracion accion, String motivo,
                                                 CategoriaDeTermino categoria, List<String> coincidencias) {
    }
}

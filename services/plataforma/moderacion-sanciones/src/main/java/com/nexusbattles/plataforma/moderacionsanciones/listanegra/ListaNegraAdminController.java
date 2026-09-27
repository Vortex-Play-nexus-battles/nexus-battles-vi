package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code /api/v1/lista-negra/terminos} — moderacion-lista-negra.yaml 2.0.x.
 *
 * <p>Solo MODERADOR o superior (con jerarquia: {@code SecurityConfig}). Quien
 * da de alta sale del token (su apodo), nunca del cuerpo. Las reglas —forma
 * normalizada unica, modo por omision, que se conserva al editar— son de
 * {@link ListaNegraAdminService}.
 */
@RestController
@RequestMapping("/api/v1/lista-negra/terminos")
public class ListaNegraAdminController {

    private final ListaNegraAdminService service;

    public ListaNegraAdminController(ListaNegraAdminService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaDeTerminos listarTerminos(@RequestParam(required = false) CategoriaDeTermino categoria,
                                           @RequestParam(required = false) Boolean activo,
                                           @RequestParam(required = false) String buscar,
                                           @RequestParam(defaultValue = "0") int pagina,
                                           @RequestParam(defaultValue = "50") int tamano) {
        return PaginaDeTerminos.desde(service.listar(
                new ListaNegraAdminService.Filtro(categoria, activo, buscar, pagina, tamano)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TerminoResponse agregarTermino(@AuthenticationPrincipal Jwt quien, @RequestBody TerminoRequest request) {
        return TerminoResponse.desde(service.agregar(request.datos(), IdentidadDelToken.apodoDe(quien)));
    }

    @PutMapping("/{termino}")
    public TerminoResponse editarTermino(@AuthenticationPrincipal Jwt quien, @PathVariable String termino,
                                         @RequestBody TerminoRequest request) {
        return TerminoResponse.desde(service.editar(termino, request.datos(), IdentidadDelToken.apodoDe(quien)));
    }

    @DeleteMapping("/{termino}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTermino(@AuthenticationPrincipal Jwt quien, @PathVariable String termino) {
        service.eliminar(termino, IdentidadDelToken.apodoDe(quien));
    }

    /** {@code TerminoRequest}: solo {@code termino} es obligatorio. */
    public record TerminoRequest(String termino, CategoriaDeTermino categoria, ModoDeCoincidencia modo,
                                 Boolean activo) {
        ListaNegraAdminService.DatosDeTermino datos() {
            return new ListaNegraAdminService.DatosDeTermino(termino, categoria, modo, activo);
        }
    }

    /** {@code TerminoResponse}. */
    public record TerminoResponse(Long id, String termino, String normalizado, CategoriaDeTermino categoria,
                                  ModoDeCoincidencia modo, boolean activo, String creadoPor,
                                  OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {
        static TerminoResponse desde(TerminoProhibido t) {
            return new TerminoResponse(t.id(), t.termino(), t.normalizado(), t.categoria(), t.modo(), t.activo(),
                    t.creadoPor(), t.creadoEn(), t.actualizadoEn());
        }
    }

    /** {@code PaginaDeTerminos}. */
    public record PaginaDeTerminos(List<TerminoResponse> contenido, int pagina, int tamano, long total) {
        static PaginaDeTerminos desde(Page<TerminoProhibido> pagina) {
            return new PaginaDeTerminos(pagina.getContent().stream().map(TerminoResponse::desde).toList(),
                    pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
        }
    }
}

package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-013): gestion de la base de conocimiento desde el panel
// administrativo. Solo ADMINISTRADOR y SUPER_ADMINISTRADOR (SecurityConfig,
// regla de /chatbot/admin/**).
//
// "borrador" es la version candidata: la unica que se edita. La version en
// produccion nunca se toca desde aqui; se reemplaza al desplegar la
// candidata despues de evaluarla (RF-CHA-014).
@RestController
@RequestMapping("/chatbot/admin/base-conocimiento")
public class BaseConocimientoAdminController {

    private final BaseConocimientoAdminService baseConocimientoAdminService;

    public BaseConocimientoAdminController(BaseConocimientoAdminService baseConocimientoAdminService) {
        this.baseConocimientoAdminService = baseConocimientoAdminService;
    }

    // Todas las versiones, de la mas reciente a la mas antigua.
    @GetMapping("/versiones")
    public List<VersionResponse> listarVersiones() {
        return baseConocimientoAdminService.listarVersiones().stream()
            .map(VersionResponse::desde)
            .toList();
    }

    // Crea la candidata como copia de produccion. El cuerpo es opcional: sin
    // el, la candidata queda sin descripcion.
    @PostMapping("/borrador")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse crearCandidata(@Valid @RequestBody(required = false) CrearCandidataRequest datos) {
        String descripcion = datos == null ? null : datos.descripcion();
        return VersionResponse.desde(baseConocimientoAdminService.crearCandidata(descripcion));
    }

    @DeleteMapping("/borrador")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartarCandidata() {
        baseConocimientoAdminService.descartarCandidata();
    }

    @GetMapping("/borrador/temas")
    public List<TemaConocimientoResponse> listarTemasDeCandidata() {
        return baseConocimientoAdminService.listarTemasDeCandidata().stream()
            .map(TemaConocimientoResponse::desde)
            .toList();
    }

    @PostMapping("/borrador/temas")
    @ResponseStatus(HttpStatus.CREATED)
    public TemaConocimientoResponse agregarTema(@Valid @RequestBody TemaConocimientoRequest datos) {
        return TemaConocimientoResponse.desde(baseConocimientoAdminService.agregarTema(datos));
    }

    @PutMapping("/borrador/temas/{temaId}")
    public TemaConocimientoResponse editarTema(@PathVariable UUID temaId,
                                               @Valid @RequestBody TemaConocimientoRequest datos) {
        return TemaConocimientoResponse.desde(baseConocimientoAdminService.editarTema(temaId, datos));
    }

    @DeleteMapping("/borrador/temas/{temaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTema(@PathVariable UUID temaId) {
        baseConocimientoAdminService.eliminarTema(temaId);
    }

    // Descarga una version completa (cualquier estado) como archivo JSON.
    @GetMapping("/versiones/{versionId}/exportacion")
    public ResponseEntity<ExportacionBaseConocimiento> exportar(@PathVariable UUID versionId) {
        ExportacionBaseConocimiento exportacion = baseConocimientoAdminService.exportar(versionId);
        String nombreArchivo = "base-conocimiento-v" + exportacion.version() + ".json";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombreArchivo).build().toString())
            .body(exportacion);
    }

    // Reemplaza TODO el contenido de la candidata por los temas del archivo.
    @PutMapping("/borrador/importacion")
    public List<TemaConocimientoResponse> importar(@Valid @RequestBody ImportacionBaseConocimiento datos) {
        return baseConocimientoAdminService.importarEnCandidata(datos.temas()).stream()
            .map(TemaConocimientoResponse::desde)
            .toList();
    }

    public record CrearCandidataRequest(@Size(max = 500) String descripcion) {
    }

    // Mismo formato que ExportacionBaseConocimiento: el archivo que se
    // descarga en /exportacion se puede subir tal cual. De el solo se lee
    // 'temas'; version, estado y fechaExportacion se ignoran.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImportacionBaseConocimiento(@NotEmpty @Valid List<TemaIntercambio> temas) {
    }
}

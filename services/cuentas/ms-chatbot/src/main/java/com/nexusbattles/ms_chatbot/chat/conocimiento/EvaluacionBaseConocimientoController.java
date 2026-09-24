package com.nexusbattles.ms_chatbot.chat.conocimiento;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

// HU-CHA-012 (RF-CHA-014): "reentrenamiento" del chatbot desde el panel.
// Solo ADMINISTRADOR y SUPER_ADMINISTRADOR (SecurityConfig, /chatbot/admin/**).
//
//   POST /borrador/evaluacion    compara candidata vs produccion, sin desplegar
//   POST /borrador/despliegue    despliega solo si no rinde peor (si no, 409)
//   POST /produccion/reversion   vuelve a la version anterior
//   /casos-evaluacion            CRUD de los casos con los que se evalua
@RestController
@RequestMapping("/chatbot/admin/base-conocimiento")
public class EvaluacionBaseConocimientoController {

    private final EvaluacionBaseConocimientoService evaluacionService;
    private final CasoEvaluacionService casoEvaluacionService;

    public EvaluacionBaseConocimientoController(EvaluacionBaseConocimientoService evaluacionService,
                                                CasoEvaluacionService casoEvaluacionService) {
        this.evaluacionService = evaluacionService;
        this.casoEvaluacionService = casoEvaluacionService;
    }

    @PostMapping("/borrador/evaluacion")
    public ComparacionEvaluacion evaluarCandidata() {
        return evaluacionService.evaluarCandidata();
    }

    @PostMapping("/borrador/despliegue")
    public VersionResponse desplegarCandidata() {
        return VersionResponse.desde(evaluacionService.desplegarCandidata());
    }

    @PostMapping("/produccion/reversion")
    public VersionResponse revertir() {
        return VersionResponse.desde(evaluacionService.revertir());
    }

    @GetMapping("/casos-evaluacion")
    public List<CasoEvaluacionResponse> listarCasos() {
        return casoEvaluacionService.listar().stream()
            .map(CasoEvaluacionResponse::desde)
            .toList();
    }

    @PostMapping("/casos-evaluacion")
    @ResponseStatus(HttpStatus.CREATED)
    public CasoEvaluacionResponse crearCaso(@Valid @RequestBody CasoEvaluacionRequest datos) {
        return CasoEvaluacionResponse.desde(casoEvaluacionService.crear(datos));
    }

    @PutMapping("/casos-evaluacion/{casoId}")
    public CasoEvaluacionResponse editarCaso(@PathVariable UUID casoId,
                                             @Valid @RequestBody CasoEvaluacionRequest datos) {
        return CasoEvaluacionResponse.desde(casoEvaluacionService.editar(casoId, datos));
    }

    @DeleteMapping("/casos-evaluacion/{casoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCaso(@PathVariable UUID casoId) {
        casoEvaluacionService.eliminar(casoId);
    }
}

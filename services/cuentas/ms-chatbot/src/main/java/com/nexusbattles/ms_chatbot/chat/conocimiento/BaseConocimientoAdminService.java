package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-013): gestion de la base de conocimiento desde el panel.
//
// El administrador NUNCA edita la version en produccion: crea una version
// candidata (copia de produccion), la edita, y la despliega con la
// evaluacion del paso de reentrenamiento (RF-CHA-014). Todo lo de aqui opera
// sobre esa candidata; exportar es lo unico que acepta cualquier version.
@Service
public class BaseConocimientoAdminService {

    private static final String SIN_CANDIDATA =
        "No hay una version candidata. Crea una a partir de la version en produccion.";
    private static final String YA_HAY_CANDIDATA =
        "Ya existe una version candidata. Despliegala o descartala antes de crear otra.";
    private static final String TEMA_NO_ENCONTRADO = "No existe ese tema en la version candidata.";
    private static final String VARIANTE_CON_COMA =
        "Una variante de pregunta no puede contener ','. Escribela como dos variantes separadas.";

    private final VersionBaseConocimientoRepository versionRepository;
    private final TemaConocimientoRepository temaRepository;

    public BaseConocimientoAdminService(VersionBaseConocimientoRepository versionRepository,
                                        TemaConocimientoRepository temaRepository) {
        this.versionRepository = versionRepository;
        this.temaRepository = temaRepository;
    }

    @Transactional(readOnly = true)
    public List<VersionBaseConocimiento> listarVersiones() {
        return versionRepository.findAllByOrderByNumeroDesc();
    }

    @Transactional
    public VersionBaseConocimiento crearCandidata(String descripcion) {
        if (versionRepository.findByEstado(EstadoVersion.BORRADOR).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, YA_HAY_CANDIDATA);
        }
        VersionBaseConocimiento produccion = versionRepository.findByEstado(EstadoVersion.PRODUCCION)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "No hay una version en produccion de la cual partir."));

        VersionBaseConocimiento candidata;
        try {
            // saveAndFlush: si otro administrador crea una candidata al mismo
            // tiempo, el indice parcial de V4 lo frena AQUI y se traduce a 409.
            candidata = versionRepository.saveAndFlush(VersionBaseConocimiento.nuevaCandidata(
                versionRepository.buscarNumeroMaximo() + 1, descripcion));
        } catch (DataIntegrityViolationException excepcion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, YA_HAY_CANDIDATA);
        }

        List<TemaConocimiento> copias = temaRepository.findByVersionIdOrderByTituloAsc(produccion.getId())
            .stream()
            .map(tema -> tema.copiarA(candidata))
            .toList();
        temaRepository.saveAll(copias);
        return candidata;
    }

    @Transactional
    public void descartarCandidata() {
        VersionBaseConocimiento candidata = requerirCandidata();
        temaRepository.deleteByVersionId(candidata.getId());
        versionRepository.delete(candidata);
    }

    @Transactional(readOnly = true)
    public List<TemaConocimiento> listarTemasDeCandidata() {
        return temaRepository.findByVersionIdOrderByTituloAsc(requerirCandidata().getId());
    }

    @Transactional
    public TemaConocimiento agregarTema(TemaConocimientoRequest datos) {
        validarVariantes(datos.variantesEs(), datos.variantesEn());
        VersionBaseConocimiento candidata = requerirCandidata();
        return temaRepository.save(nuevoTema(candidata, UUID.randomUUID().toString(),
            datos.categoria(), datos.tipoRespuesta(), datos.titulo(), datos.variantesEs(), datos.variantesEn(),
            datos.respuestaEs(), datos.respuestaEn(), datos.prioridad(), datos.activo()));
    }

    @Transactional
    public TemaConocimiento editarTema(UUID temaId, TemaConocimientoRequest datos) {
        validarVariantes(datos.variantesEs(), datos.variantesEn());
        TemaConocimiento tema = requerirTemaDeCandidata(temaId);
        tema.actualizar(datos.categoria(), datos.tipoRespuesta(), datos.titulo(),
            VariantesDePregunta.unir(datos.variantesEs()), VariantesDePregunta.unir(datos.variantesEn()),
            datos.respuestaEs(), datos.respuestaEn(), datos.prioridad(), activoPorDefecto(datos.activo()));
        return tema;
    }

    @Transactional
    public void eliminarTema(UUID temaId) {
        temaRepository.delete(requerirTemaDeCandidata(temaId));
    }

    @Transactional(readOnly = true)
    public ExportacionBaseConocimiento exportar(UUID versionId) {
        VersionBaseConocimiento version = versionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa version."));
        List<TemaIntercambio> temas = temaRepository.findByVersionIdOrderByTituloAsc(versionId).stream()
            .map(TemaIntercambio::desde)
            .toList();
        return new ExportacionBaseConocimiento(version.getNumero(), version.getEstado(), Instant.now(), temas);
    }

    // Reemplaza TODO el contenido de la candidata por el del archivo.
    @Transactional
    public List<TemaConocimiento> importarEnCandidata(List<TemaIntercambio> temas) {
        Set<String> claves = new HashSet<>();
        for (TemaIntercambio tema : temas) {
            validarVariantes(tema.variantesEs(), tema.variantesEn());
            if (tema.clave() != null && !tema.clave().isBlank() && !claves.add(tema.clave())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La clave '" + tema.clave() + "' aparece mas de una vez en el archivo.");
            }
        }

        VersionBaseConocimiento candidata = requerirCandidata();
        temaRepository.deleteByVersionId(candidata.getId());
        // Hibernate ejecuta los INSERT antes que los DELETE al confirmar; sin
        // este flush, reimportar las mismas claves chocaria con el indice
        // unico (version_id, clave) de V4.
        temaRepository.flush();

        List<TemaConocimiento> nuevos = temas.stream()
            .map(t -> nuevoTema(candidata,
                t.clave() == null || t.clave().isBlank() ? UUID.randomUUID().toString() : t.clave(),
                t.categoria(), t.tipoRespuesta(), t.titulo(), t.variantesEs(), t.variantesEn(),
                t.respuestaEs(), t.respuestaEn(), t.prioridad(), t.activo()))
            .toList();
        return temaRepository.saveAll(nuevos);
    }

    private VersionBaseConocimiento requerirCandidata() {
        return versionRepository.findByEstado(EstadoVersion.BORRADOR)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_CANDIDATA));
    }

    private TemaConocimiento requerirTemaDeCandidata(UUID temaId) {
        return temaRepository.findByIdAndVersionId(temaId, requerirCandidata().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, TEMA_NO_ENCONTRADO));
    }

    private static void validarVariantes(List<String> variantesEs, List<String> variantesEn) {
        if (VariantesDePregunta.algunaContieneSeparador(variantesEs)
            || VariantesDePregunta.algunaContieneSeparador(variantesEn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, VARIANTE_CON_COMA);
        }
    }

    private static TemaConocimiento nuevoTema(VersionBaseConocimiento version, String clave, Categoria categoria,
                                              TipoRespuesta tipoRespuesta, String titulo,
                                              List<String> variantesEs, List<String> variantesEn,
                                              String respuestaEs, String respuestaEn,
                                              int prioridad, Boolean activo) {
        return new TemaConocimiento(version, clave, categoria, tipoRespuesta, titulo,
            VariantesDePregunta.unir(variantesEs), VariantesDePregunta.unir(variantesEn),
            respuestaEs, respuestaEn, prioridad, activoPorDefecto(activo));
    }

    private static boolean activoPorDefecto(Boolean activo) {
        return activo == null || activo;
    }
}

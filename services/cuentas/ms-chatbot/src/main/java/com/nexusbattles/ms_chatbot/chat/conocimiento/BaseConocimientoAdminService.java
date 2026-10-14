package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-013): gestion de la base de conocimiento desde el panel.
//
// El administrador NUNCA edita la version en produccion: crea una version
// candidata (copia de produccion), la edita, y la despliega con la
// evaluacion del paso de reentrenamiento (RF-CHA-014). Todo lo de aqui opera
// sobre esa candidata; exportar es lo unico que acepta cualquier version.
//
// ms-chatbot.yaml 1.6.0 (7.4.10):
//   * intenciones adicionales de un tema (hasta 5, sin repetir, nunca la
//     principal; si no, 400);
//   * un titulo repetido en la misma version (sin contar tildes, mayusculas
//     ni signos) es 409 TEMA_DUPLICADO; las variantes repetidas de un tema se
//     guardan una vez, sin error;
//   * la importacion trae como mucho 500 temas (413 IMPORTACION_DEMASIADO_GRANDE);
//   * cada operacion que cambia la candidata empieza bloqueandola, y crearla
//     bloquea produccion (BloqueoDeVersiones): una transicion en curso
//     termina primero, y la que llega con un estado viejo recibe 409
//     CAMBIO_CONCURRENTE.
@Service
public class BaseConocimientoAdminService {

    private static final String SIN_CANDIDATA =
        "No hay una version candidata. Crea una a partir de la version en produccion.";
    private static final String YA_HAY_CANDIDATA =
        "Ya existe una version candidata. Despliegala o descartala antes de crear otra.";
    private static final String SIN_PRODUCCION = "No hay una version en produccion de la cual partir.";
    private static final String TEMA_NO_ENCONTRADO = "No existe ese tema en la version candidata.";
    private static final String VARIANTE_CON_COMA =
        "Una variante de pregunta no puede contener ','. Escribela como dos variantes separadas.";

    private final VersionBaseConocimientoRepository versionRepository;
    private final TemaConocimientoRepository temaRepository;
    private final BloqueoDeVersiones bloqueo;

    public BaseConocimientoAdminService(VersionBaseConocimientoRepository versionRepository,
                                        TemaConocimientoRepository temaRepository) {
        this.versionRepository = versionRepository;
        this.temaRepository = temaRepository;
        this.bloqueo = new BloqueoDeVersiones(versionRepository);
    }

    @Transactional(readOnly = true)
    public List<VersionBaseConocimiento> listarVersiones() {
        return versionRepository.findAllByOrderByNumeroDesc();
    }

    // 1.6.0: produccion se bloquea antes de mirar nada. Un despliegue o una
    // reversion en curso terminan primero, y la candidata nace de la
    // produccion que queda, nunca de una que ya se retiro.
    @Transactional
    public VersionBaseConocimiento crearCandidata(String descripcion) {
        VersionBaseConocimiento produccion = bloqueo.bloquear(EstadoVersion.PRODUCCION)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_PRODUCCION));
        if (versionRepository.existsByEstado(EstadoVersion.BORRADOR)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, YA_HAY_CANDIDATA);
        }

        VersionBaseConocimiento candidata;
        try {
            // saveAndFlush: si otro administrador crea una candidata al mismo
            // tiempo, el indice parcial de V4 lo frena AQUI y se traduce a 409.
            candidata = versionRepository.saveAndFlush(VersionBaseConocimiento.nuevaCandidata(
                versionRepository.buscarNumeroMaximo() + 1, descripcion));
        } catch (DataIntegrityViolationException excepcion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, YA_HAY_CANDIDATA);
        }

        // 1.6.0: copiarA lleva tambien las intenciones adicionales.
        List<TemaConocimiento> copias = temaRepository.findByVersionIdOrderByTituloAsc(produccion.getId())
            .stream()
            .map(tema -> tema.copiarA(candidata))
            .toList();
        temaRepository.saveAll(copias);
        return candidata;
    }

    @Transactional
    public void descartarCandidata() {
        VersionBaseConocimiento candidata = bloquearCandidata();
        temaRepository.deleteByVersionId(candidata.getId());
        versionRepository.delete(candidata);
    }

    // Solo lectura: sin bloqueo (PostgreSQL no bloquea filas en una
    // transaccion de solo lectura, y listar no cambia nada).
    @Transactional(readOnly = true)
    public List<TemaConocimiento> listarTemasDeCandidata() {
        VersionBaseConocimiento candidata = versionRepository.findByEstado(EstadoVersion.BORRADOR)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_CANDIDATA));
        return temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId());
    }

    @Transactional
    public TemaConocimiento agregarTema(TemaConocimientoRequest datos) {
        validarVariantes(datos.variantesEs(), datos.variantesEn());
        List<Categoria> intenciones = intencionesValidas(datos.categoria(), datos.intencionesAdicionales(), "");
        VersionBaseConocimiento candidata = bloquearCandidata();
        exigirTituloLibre(candidata, datos.titulo(), datos.tituloEn(), null);
        TemaConocimiento tema = nuevoTema(candidata, UUID.randomUUID().toString(),
            datos.categoria(), intenciones, datos.tipoRespuesta(), datos.titulo(), datos.variantesEs(),
            datos.variantesEn(), datos.respuestaEs(), datos.respuestaEn(), datos.prioridad(), datos.activo());
        tema.asignarTituloEn(datos.tituloEn());
        return temaRepository.save(tema);
    }

    @Transactional
    public TemaConocimiento editarTema(UUID temaId, TemaConocimientoRequest datos) {
        validarVariantes(datos.variantesEs(), datos.variantesEn());
        List<Categoria> intenciones = intencionesValidas(datos.categoria(), datos.intencionesAdicionales(), "");
        VersionBaseConocimiento candidata = bloquearCandidata();
        TemaConocimiento tema = requerirTemaDe(candidata, temaId);
        exigirTituloLibre(candidata, datos.titulo(), datos.tituloEn(), tema);
        tema.actualizar(datos.categoria(), datos.tipoRespuesta(), datos.titulo(),
            VariantesDePregunta.unirSinRepetir(datos.variantesEs()),
            VariantesDePregunta.unirSinRepetir(datos.variantesEn()),
            datos.respuestaEs(), datos.respuestaEn(), datos.prioridad(), activoPorDefecto(datos.activo()),
            intenciones);
        tema.asignarTituloEn(datos.tituloEn());
        return tema;
    }

    @Transactional
    public void eliminarTema(UUID temaId) {
        VersionBaseConocimiento candidata = bloquearCandidata();
        temaRepository.delete(requerirTemaDe(candidata, temaId));
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
    //
    // 1.6.0: como el archivo reemplaza la candidata entera (no se suma a lo
    // que ya tiene), un titulo duplicado solo puede estar DENTRO del archivo:
    // dos temas del archivo con el mismo titulo normalizado son 409
    // TEMA_DUPLICADO, igual que al agregarlos uno a uno. Todo se comprueba
    // antes de tocar la candidata: con cualquier error no se escribe nada.
    @Transactional
    public List<TemaConocimiento> importarEnCandidata(List<TemaIntercambio> temas) {
        if (temas.size() > ImportacionDemasiadoGrandeException.MAXIMO_DE_TEMAS) {
            throw ImportacionDemasiadoGrandeException.porTemas(temas.size());
        }
        Set<String> claves = new HashSet<>();
        Map<String, Integer> posicionPorTitulo = new HashMap<>();
        List<List<Categoria>> intenciones = new ArrayList<>();
        for (int i = 0; i < temas.size(); i++) {
            TemaIntercambio tema = temas.get(i);
            int posicion = i + 1;
            validarVariantes(tema.variantesEs(), tema.variantesEn());
            intenciones.add(intencionesValidas(tema.categoria(), tema.intencionesAdicionales(),
                "Tema " + posicion + " del archivo ('" + tema.titulo() + "'): "));
            if (tema.clave() != null && !tema.clave().isBlank() && !claves.add(tema.clave())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La clave '" + tema.clave() + "' aparece mas de una vez en el archivo.");
            }
            // 1.17.0: el titulo en ingles tampoco puede repetir uno de otro tema.
            for (String titulo : titulosDe(tema.titulo(), tema.tituloEn())) {
                Integer anterior = posicionPorTitulo.putIfAbsent(titulo, posicion);
                if (anterior != null) {
                    throw new TemaDuplicadoException("Los temas " + anterior + " y " + posicion
                        + " del archivo tienen el mismo titulo ('" + temas.get(anterior - 1).titulo() + "' y '"
                        + tema.titulo() + "', sin contar tildes, mayusculas ni signos, en espanol o en ingles). "
                        + "Una version no puede tener dos temas con el mismo titulo: cambia uno o quitalo del "
                        + "archivo.");
                }
            }
        }

        VersionBaseConocimiento candidata = bloquearCandidata();
        temaRepository.deleteByVersionId(candidata.getId());
        // Hibernate ejecuta los INSERT antes que los DELETE al confirmar; sin
        // este flush, reimportar las mismas claves chocaria con el indice
        // unico (version_id, clave) de V4.
        temaRepository.flush();

        List<TemaConocimiento> nuevos = new ArrayList<>();
        for (int i = 0; i < temas.size(); i++) {
            TemaIntercambio t = temas.get(i);
            TemaConocimiento nuevo = nuevoTema(candidata,
                t.clave() == null || t.clave().isBlank() ? UUID.randomUUID().toString() : t.clave(),
                t.categoria(), intenciones.get(i), t.tipoRespuesta(), t.titulo(), t.variantesEs(), t.variantesEn(),
                t.respuestaEs(), t.respuestaEn(), t.prioridad(), t.activo());
            nuevo.asignarTituloEn(t.tituloEn());
            nuevos.add(nuevo);
        }
        return temaRepository.saveAll(nuevos);
    }

    /**
     * 1.6.0 (7.4.10 «asociar respuestas con múltiples intenciones»): las
     * intenciones adicionales de un tema, en el orden del enum. Null o vacía =
     * ninguna. Hasta {@value TemaConocimiento#MAXIMO_DE_INTENCIONES_ADICIONALES},
     * sin repetir y nunca la categoría principal; si no, 400 con el motivo.
     *
     * @param prefijo con qué empieza el mensaje (en la importación, qué tema)
     */
    static List<Categoria> intencionesValidas(Categoria principal, List<Categoria> adicionales, String prefijo) {
        if (adicionales == null || adicionales.isEmpty()) {
            return List.of();
        }
        if (adicionales.size() > TemaConocimiento.MAXIMO_DE_INTENCIONES_ADICIONALES) {
            throw peticionInvalida(prefijo + "un tema atiende como maximo "
                + TemaConocimiento.MAXIMO_DE_INTENCIONES_ADICIONALES + " intenciones ademas de su categoria y llegaron "
                + adicionales.size() + ".");
        }
        Set<Categoria> distintas = EnumSet.noneOf(Categoria.class);
        for (Categoria intencion : adicionales) {
            if (intencion == null) {
                throw peticionInvalida(prefijo + "una intencion adicional viene vacia; usa una de las categorias.");
            }
            if (intencion == principal) {
                throw peticionInvalida(prefijo + "la intencion " + intencion
                    + " ya es la categoria principal del tema; no se repite como adicional.");
            }
            if (!distintas.add(intencion)) {
                throw peticionInvalida(prefijo + "la intencion " + intencion
                    + " esta repetida en las intenciones adicionales.");
            }
        }
        return List.copyOf(distintas);
    }

    // 1.6.0 (RF-CHA-013 «entrada duplicada»): ningun otro tema de la
    // candidata con el mismo titulo, sin contar tildes, mayusculas ni signos.
    // Se comprueba con la candidata bloqueada, asi que dos administradores que
    // agregan el mismo tema a la vez no lo duplican: el segundo recibe el 409.
    // `excepto` es el tema que se edita: en la misma transaccion JPA devuelve
    // la misma instancia para el mismo tema, asi que basta compararla.
    // 1.17.0: lo mismo con el titulo en ingles: ninguno de los dos titulos
    // del tema puede ser el de otro tema, en espanol o en ingles (pulsar una
    // pregunta relacionada responde el tema de ese titulo).
    private void exigirTituloLibre(VersionBaseConocimiento candidata, String titulo, String tituloEn,
                                   TemaConocimiento excepto) {
        String buscado = NormalizadorTexto.normalizar(titulo);
        String buscadoEn = tituloEn == null || tituloEn.isBlank() ? null : NormalizadorTexto.normalizar(tituloEn);
        for (TemaConocimiento otro : temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId())) {
            if (otro == excepto) {
                continue;
            }
            Set<String> delOtro = titulosDe(otro.getTitulo(), otro.getTituloEn());
            if (delOtro.contains(buscado)) {
                // El mensaje de siempre si chocan los dos titulos en espanol.
                boolean conElDeIngles = !buscado.equals(NormalizadorTexto.normalizar(otro.getTitulo()));
                throw duplicado("El titulo '" + titulo.strip() + "'", otro, conElDeIngles);
            }
            if (buscadoEn != null && delOtro.contains(buscadoEn)) {
                throw duplicado("El titulo en ingles '" + tituloEn.strip() + "'", otro, true);
            }
        }
    }

    private static TemaDuplicadoException duplicado(String cual, TemaConocimiento otro, boolean enLosDosIdiomas) {
        return new TemaDuplicadoException(cual + " es el mismo que el del tema '" + otro.getTitulo()
            + "' de la version candidata (sin contar tildes, mayusculas ni signos"
            + (enLosDosIdiomas ? ", en espanol o en ingles" : "") + "). Cambialo o edita ese tema.");
    }

    // Los titulos de un tema, normalizados (el de ingles, si lo tiene).
    private static Set<String> titulosDe(String titulo, String tituloEn) {
        Set<String> titulos = new HashSet<>();
        titulos.add(NormalizadorTexto.normalizar(titulo));
        if (tituloEn != null && !tituloEn.isBlank()) {
            titulos.add(NormalizadorTexto.normalizar(tituloEn));
        }
        return titulos;
    }

    private VersionBaseConocimiento bloquearCandidata() {
        return bloqueo.bloquear(EstadoVersion.BORRADOR)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_CANDIDATA));
    }

    private TemaConocimiento requerirTemaDe(VersionBaseConocimiento candidata, UUID temaId) {
        return temaRepository.findByIdAndVersionId(temaId, candidata.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, TEMA_NO_ENCONTRADO));
    }

    private static void validarVariantes(List<String> variantesEs, List<String> variantesEn) {
        if (VariantesDePregunta.algunaContieneSeparador(variantesEs)
            || VariantesDePregunta.algunaContieneSeparador(variantesEn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, VARIANTE_CON_COMA);
        }
    }

    private static ResponseStatusException peticionInvalida(String mensaje) {
        String conMayuscula = Character.toUpperCase(mensaje.charAt(0)) + mensaje.substring(1);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, conMayuscula);
    }

    private static TemaConocimiento nuevoTema(VersionBaseConocimiento version, String clave, Categoria categoria,
                                              List<Categoria> intencionesAdicionales,
                                              TipoRespuesta tipoRespuesta, String titulo,
                                              List<String> variantesEs, List<String> variantesEn,
                                              String respuestaEs, String respuestaEn,
                                              int prioridad, Boolean activo) {
        TemaConocimiento tema = new TemaConocimiento(version, clave, categoria, tipoRespuesta, titulo,
            VariantesDePregunta.unirSinRepetir(variantesEs), VariantesDePregunta.unirSinRepetir(variantesEn),
            respuestaEs, respuestaEn, prioridad, activoPorDefecto(activo));
        tema.asignarIntencionesAdicionales(intencionesAdicionales);
        return tema;
    }

    private static boolean activoPorDefecto(Boolean activo) {
        return activo == null || activo;
    }
}

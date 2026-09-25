package com.nexusbattles.plataforma.correo.template;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PlantillaCorreoService {

    private final TemplateEngine templateEngine;

    public PlantillaCorreoService() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
        this.templateEngine.addDialect(new LayoutDialect());
    }

    /**
     * Plantillas que este servicio puede servir.
     *
     * <p>
     * El nombre de la plantilla nunca debe poder llegar desde fuera: cargar
     * una arbitraria del classpath permitiria leer plantillas ajenas o inyectar
     * contenido en el correo. Hoy todos los llamantes pasan constantes, pero el
     * metodo es publico y nada lo impedia.
     *
     * <p>
     * Salen de {@link Plantilla}, el catalogo de lo que la API sabe enviar,
     * mas la plantilla de prueba de la integracion (HU-COR-001). Una plantilla
     * nueva se declara alli; si no, falla al primer intento con un mensaje claro.
     */
    private static final Set<String> PLANTILLAS_PERMITIDAS = Stream.concat(
                    Arrays.stream(Plantilla.values()).map(Plantilla::ruta),
                    Stream.of("email/plantilla-prueba"))
            .collect(Collectors.toUnmodifiableSet());

    public String renderizar(String nombrePlantilla, Map<String, Object> variables) {
        if (!PLANTILLAS_PERMITIDAS.contains(nombrePlantilla)) {
            throw new IllegalArgumentException(
                    "Plantilla no registrada: '" + nombrePlantilla + "'. "
                            + "Declarala en Plantilla. Disponibles: " + PLANTILLAS_PERMITIDAS);
        }

        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(nombrePlantilla, context);
    }
}

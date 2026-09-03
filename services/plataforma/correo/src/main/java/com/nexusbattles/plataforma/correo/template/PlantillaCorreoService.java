package com.nexusbattles.plataforma.correo.template;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;
import java.util.Set;

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
     * Al anadir una plantilla nueva hay que declararla aqui; si no, falla al
     * primer intento con un mensaje claro.
     */
    private static final Set<String> PLANTILLAS_PERMITIDAS = Set.of(
            "email/bienvenida",
            "email/aviso-acceso",
            "email/recuperacion-clave",
            "email/plantilla-prueba");

    public String renderizar(String nombrePlantilla, Map<String, Object> variables) {
        if (!PLANTILLAS_PERMITIDAS.contains(nombrePlantilla)) {
            throw new IllegalArgumentException(
                    "Plantilla no registrada: '" + nombrePlantilla + "'. "
                            + "Declarala en PLANTILLAS_PERMITIDAS. Disponibles: " + PLANTILLAS_PERMITIDAS);
        }

        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(nombrePlantilla, context);
    }
}

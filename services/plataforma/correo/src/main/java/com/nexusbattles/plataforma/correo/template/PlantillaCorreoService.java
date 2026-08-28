package com.nexusbattles.plataforma.correo.template;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

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

    public String renderizar(String nombrePlantilla, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(nombrePlantilla, context);
    }
}

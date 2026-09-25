package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * La politica contexto -> accion de la lista negra, leida de
 * {@code lista-negra.accion.*} ({@code application.yml}, cada entrada con su
 * variable de entorno {@code LISTA_NEGRA_ACCION_<CONTEXTO>}). Lo que no se
 * configura conserva la tabla del contrato.
 */
@Configuration
public class ConfiguracionDeListaNegra {

    static final String PREFIJO = "lista-negra.accion";

    @Bean
    public PoliticaDeModeracion politicaDeModeracion(Environment entorno) {
        Map<String, AccionDeModeracion> leidas = Binder.get(entorno)
                .bind(PREFIJO, Bindable.mapOf(String.class, AccionDeModeracion.class))
                .orElse(Map.of());
        return new PoliticaDeModeracion(porContexto(leidas));
    }

    /** {@code nombre-equipo} -> {@code NOMBRE_EQUIPO}; una clave que no es un contexto no se ignora. */
    static Map<ContextoDeTexto, AccionDeModeracion> porContexto(Map<String, AccionDeModeracion> leidas) {
        Map<ContextoDeTexto, AccionDeModeracion> tabla = new EnumMap<>(ContextoDeTexto.class);
        leidas.forEach((clave, accion) -> {
            String nombre = clave.strip().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                tabla.put(ContextoDeTexto.valueOf(nombre), accion);
            } catch (IllegalArgumentException desconocido) {
                throw new IllegalArgumentException(
                        "'" + PREFIJO + "." + clave + "' no es un contexto de la lista negra", desconocido);
            }
        });
        return tabla;
    }
}

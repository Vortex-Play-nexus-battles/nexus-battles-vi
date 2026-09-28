package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El perfil y el estado de combate como texto JSON en su columna (V14).
 *
 * <p>Por que JSON y no columnas: son datos que este servicio guarda pero no
 * interpreta —los numeros y los efectos los decide el motor de combate— y que
 * cambian con el reglamento. Una columna por campo convertiria cada efecto
 * nuevo de la Tabla 7 en una migracion.
 *
 * <p>Las claves de los mapas se escriben ordenadas: la ficha de la sala se
 * compara por valor (ver {@code SalaEntidad.FichaEmbebida}) y dos textos del
 * mismo estado tienen que ser iguales.
 *
 * <p>Un texto que no se puede leer —una fila corrupta, un formato de otra
 * version— se lee como {@code null} y se anota: el combate degrada a lo que
 * haria el motor sin ese dato (catalogo en nivel 1, poder maximo), en vez de
 * dejar la partida ilegible.
 */
final class JsonDeCombate {

    private static final Logger BITACORA = LoggerFactory.getLogger(JsonDeCombate.class);

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonDeCombate() {
    }

    static String escribir(Object valor) {
        return valor == null ? null : JSON.writeValueAsString(valor);
    }

    static PerfilDeCombate perfil(String texto) {
        return leer(texto, PerfilDeCombate.class);
    }

    static EstadoDeCombate estado(String texto) {
        return leer(texto, EstadoDeCombate.class);
    }

    private static <T> T leer(String texto, Class<T> tipo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(texto, tipo);
        } catch (JacksonException | IllegalArgumentException ilegible) {
            BITACORA.warn("No se pudo leer un {} guardado; se trata como ausente: {}", tipo.getSimpleName(),
                    ilegible.getMessage());
            return null;
        }
    }
}

package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class DetectorTerminosProhibidosService {

    /**
     * Deteccion anti-evasion (contexto del issue HU-ADM-002): un termino prohibido
     * se detecta incluso escondido dentro de otra palabra o pegado a otros
     * caracteres (ej. "malapalabrota", "xmalapalabrax"), no solo como palabra
     * exacta. Ignora mayusculas/minusculas y tildes.
     */
    public boolean contieneTermino(String texto, List<String> terminosProhibidos) {
        if (texto == null || terminosProhibidos == null || terminosProhibidos.isEmpty()) {
            return false;
        }

        String textoNormalizado = normalizar(texto);

        return terminosProhibidos.stream()
                .map(this::normalizar)
                .anyMatch(textoNormalizado::contains);
    }

    /**
     * Quita tildes/diacriticos y mayusculas (regla de negocio: "el filtro ignora
     * mayusculas, minusculas y tildes"), sin alterar el resto del texto.
     */
    private String normalizar(String valor) {
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}

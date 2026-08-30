package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VerificacionListaNegraService {

    private final ListaNegraAdminService listaNegraAdminService;
    private final DetectorTerminosProhibidosService detector;

    public VerificacionListaNegraService(ListaNegraAdminService listaNegraAdminService,
                                          DetectorTerminosProhibidosService detector) {
        this.listaNegraAdminService = listaNegraAdminService;
        this.detector = detector;
    }

    public ResultadoVerificacion verificar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El texto no puede estar vacio");
        }

        List<String> terminosProhibidos = listaNegraAdminService.listarTerminos();
        boolean contieneTermino = detector.contieneTermino(texto, terminosProhibidos);

        if (contieneTermino) {
            return new ResultadoVerificacion(false, "termino ofensivo detectado");
        }
        return new ResultadoVerificacion(true, null);
    }

    public record ResultadoVerificacion(boolean aprobado, String motivo) {
    }
}

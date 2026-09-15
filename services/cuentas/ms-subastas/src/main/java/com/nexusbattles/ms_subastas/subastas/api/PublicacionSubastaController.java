package com.nexusbattles.ms_subastas.subastas.api;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaRequest;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import com.nexusbattles.ms_subastas.subastas.service.PublicarSubastaApplicationService;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.*;

@RestController
// Sin /api/v1: lo antepone server.servlet.context-path, igual que en el resto
// de controladores del servicio. Con el prefijo repetido, este endpoint
// quedaba publicado en /api/v1/api/v1/subastas y la ruta que declara
// ms-subastas-publicar.yaml respondia 405.
@RequestMapping("/subastas")
public class PublicacionSubastaController {
    private final ObjectProvider<PublicarSubastaApplicationService> servicio;
    private final IdentidadClient identidad;

    public PublicacionSubastaController(ObjectProvider<PublicarSubastaApplicationService> servicio, IdentidadClient identidad) {
        this.servicio = servicio;
        this.identidad = identidad;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicarSubastaResponse publicar(@RequestHeader("Idempotency-Key") String clave,
            @Valid @RequestBody PublicarSubastaRequest solicitud) {
        identidad.actual(); // Exige JWT y uid incluso mientras faltan adapters.
        if (clave.isBlank() || clave.length() > 100) {
            throw new PublicacionSubastaException(SOLICITUD_INVALIDA, "Idempotency-Key debe tener entre 1 y 100 caracteres");
        }
        var disponible = servicio.getIfAvailable();
        if (disponible == null) {
            throw new PublicacionSubastaException(DEPENDENCIA_NO_DISPONIBLE, "Publicacion pendiente de integraciones de inventario y finanzas");
        }
        return disponible.publicar(solicitud, clave);
    }
}

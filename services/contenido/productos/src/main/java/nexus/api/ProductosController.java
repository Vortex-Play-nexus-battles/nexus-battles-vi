package nexus.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductosController {

        @PostMapping
        public ResponseEntity<Void> crear(
                        @Valid @RequestBody SolicitudCrearProducto solicitud) {

                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .build();
        }
}
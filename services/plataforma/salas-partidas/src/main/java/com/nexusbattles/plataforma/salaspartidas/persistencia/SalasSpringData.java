package com.nexusbattles.plataforma.salaspartidas.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Acceso a la tabla de salas por Spring Data.
 *
 * <p>Es un detalle de infraestructura y por eso no es publica: el resto del
 * servicio habla con {@code RepositorioDeSalas}, el puerto del dominio. Si
 * manana esto dejara de ser JPA, nada fuera de este paquete se enteraria.
 */
interface SalasSpringData extends JpaRepository<SalaEntidad, UUID> {
}

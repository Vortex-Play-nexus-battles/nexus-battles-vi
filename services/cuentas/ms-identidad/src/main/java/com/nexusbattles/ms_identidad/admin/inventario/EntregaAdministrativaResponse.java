package com.nexusbattles.ms_identidad.admin.inventario;

import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.ElementoEntregado;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.Entrega;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lo que se entrego: la entrega del inventario, sus elementos y si la creo
 * esta peticion. {@code nueva} falso es una repeticion con la misma clave: la
 * entrega original, sin duplicar nada.
 */
public record EntregaAdministrativaResponse(String entregaId, UUID uid, String productoId, int cantidad,
                                            List<ElementoEntregado> elementos, OffsetDateTime entregadaEn,
                                            boolean nueva) {

    static EntregaAdministrativaResponse de(Entrega entrega, String productoId) {
        List<ElementoEntregado> elementos = entrega.elementos() == null ? List.of() : entrega.elementos();
        return new EntregaAdministrativaResponse(entrega.id(), entrega.uid(), productoId, elementos.size(),
                elementos, entrega.entregadaEn(), entrega.nueva());
    }
}

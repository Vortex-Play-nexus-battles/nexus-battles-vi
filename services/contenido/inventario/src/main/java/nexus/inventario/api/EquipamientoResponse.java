package nexus.inventario.api;

import java.util.List;
import java.util.Map;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.ParteArmadura;

public record EquipamientoResponse(
        String heroeId,
        List<String> armas,
        Map<ParteArmadura, String> armaduras,
        List<String> items) {

    static EquipamientoResponse de(EquipamientoHeroe equipamiento) {
        return new EquipamientoResponse(
                equipamiento.heroeId(), equipamiento.armas(),
                equipamiento.armaduras(), equipamiento.items());
    }
}

package nexus.combate;

import java.util.List;

public interface TransferidorEquipo {

    void transferir(String operacionId, List<TransferenciaEquipo> transferencias);
}

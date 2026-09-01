package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;

import java.util.Objects;

/**
 * Listado paginado de salas disponibles — HU-SAL-002, RF-JUE-002.
 *
 * <p>Su unico trabajo es decidir los valores por defecto y delegar. Que estados
 * entran en el listado no se decide aqui: lo garantiza el puerto
 * {@code listar}, para que ningun otro camino pueda saltarselo.
 */
public class ListarSalas {

    private final RepositorioDeSalas repositorio;

    public ListarSalas(RepositorioDeSalas repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
    }

    /**
     * @param pagina    numero de pagina base 0; {@code null} es la primera
     * @param tamano    elementos por pagina; {@code null} usa el valor de diseno, 16
     * @param modalidad filtro opcional por modalidad (RF-JUE-004)
     * @param estado    filtro opcional por estado de la sala
     */
    public PaginaDeSalas ejecutar(Integer pagina, Integer tamano,
                                  Modalidad modalidad, EstadoSala estado) {

        // Se acotan en vez de rechazar: una pagina negativa o un tamano absurdo
        // llegan de una URL escrita a mano, no de un error del jugador. El
        // maximo de 50 es el que declara el contrato.
        int paginaPedida = pagina == null || pagina < 0 ? 0 : pagina;
        int tamanoPedido = tamano == null ? PaginaDeSalas.TAMANO_POR_DEFECTO : tamano;
        tamanoPedido = Math.clamp(tamanoPedido, 1, 50);

        return repositorio.listar(modalidad, estado, paginaPedida, tamanoPedido);
    }
}

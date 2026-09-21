package com.nexusbattles.plataforma.adminparametros.parametros;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParametroRepository extends JpaRepository<Parametro, String> {

    List<Parametro> findAllByOrderByOrdenAscClaveAsc();
}

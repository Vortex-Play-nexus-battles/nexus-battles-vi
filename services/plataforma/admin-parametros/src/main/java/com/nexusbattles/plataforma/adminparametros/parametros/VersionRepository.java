package com.nexusbattles.plataforma.adminparametros.parametros;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionRepository extends JpaRepository<Version, Long> {

    List<Version> findByClaveOrderByVersionDesc(String clave);
}

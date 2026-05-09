package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.YaVoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface YaVotoRepository extends JpaRepository<YaVoto, Long> {
    boolean existsByCedulaAndIdEleccion(String cedula, Long idEleccion);
    Optional<YaVoto> findByCedulaAndIdEleccion(String cedula, Long idEleccion);
}

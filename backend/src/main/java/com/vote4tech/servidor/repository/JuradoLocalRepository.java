package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.JuradoLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JuradoLocalRepository extends JpaRepository<JuradoLocal, Long> {
    Optional<JuradoLocal> findByCedulaAndActivoTrue(String cedula);
}

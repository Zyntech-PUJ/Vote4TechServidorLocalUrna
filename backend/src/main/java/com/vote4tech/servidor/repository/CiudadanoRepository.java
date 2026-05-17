package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.Ciudadano;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CiudadanoRepository extends JpaRepository<Ciudadano, Long> {
    Optional<Ciudadano> findByCedula(String cedula);
    List<Ciudadano> findByHabilitadoDomicilioTrue();
    List<Ciudadano> findByHabilitadoDomicilioFalse();
}

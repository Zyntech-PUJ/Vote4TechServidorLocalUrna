package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.Eleccion;
import com.vote4tech.servidor.enums.EstadoEleccion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EleccionRepository extends JpaRepository<Eleccion, Long> {
    List<Eleccion> findByEstado(EstadoEleccion estado);
    List<Eleccion> findByEstadoIn(List<EstadoEleccion> estados);
}

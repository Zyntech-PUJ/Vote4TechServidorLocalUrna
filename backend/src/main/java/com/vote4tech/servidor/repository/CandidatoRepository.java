package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.Candidato;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {
    List<Candidato> findByLista_Eleccion_IdEleccionAndActivoTrue(Long idEleccion);
}

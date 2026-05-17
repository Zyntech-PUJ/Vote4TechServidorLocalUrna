package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.CentroVotacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentroVotacionRepository extends JpaRepository<CentroVotacion, Long> {
}

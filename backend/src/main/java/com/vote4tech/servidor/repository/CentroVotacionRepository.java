package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.CentroVotacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CentroVotacionRepository extends JpaRepository<CentroVotacion, Long> {

    Optional<CentroVotacion> findByServidorId(String servidorId);

    @Modifying
    @Query("UPDATE CentroVotacion c SET c.servidorId = null WHERE c.servidorId = :servidorId")
    void releaseByServidorId(String servidorId);
}

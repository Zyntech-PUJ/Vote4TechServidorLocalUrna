package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.Partido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartidoRepository extends JpaRepository<Partido, Long> {
    Optional<Partido> findBySigla(String sigla);
}

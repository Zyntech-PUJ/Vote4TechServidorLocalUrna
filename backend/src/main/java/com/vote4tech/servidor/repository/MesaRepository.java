package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    List<Mesa> findByActivoTrue();
}

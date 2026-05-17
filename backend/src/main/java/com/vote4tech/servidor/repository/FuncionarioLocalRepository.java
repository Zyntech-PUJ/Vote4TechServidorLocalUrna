package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.FuncionarioLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FuncionarioLocalRepository extends JpaRepository<FuncionarioLocal, Long> {
    List<FuncionarioLocal> findByActivoTrue();
}

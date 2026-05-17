package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.Lista;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListaRepository extends JpaRepository<Lista, Long> {
}

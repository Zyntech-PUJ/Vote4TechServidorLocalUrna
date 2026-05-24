package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.ServidorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServidorConfigRepository extends JpaRepository<ServidorConfig, Long> {
}

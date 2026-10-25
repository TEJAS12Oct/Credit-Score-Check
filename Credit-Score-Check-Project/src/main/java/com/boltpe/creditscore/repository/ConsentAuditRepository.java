package com.boltpe.creditscore.repository;

import com.boltpe.creditscore.entity.ConsentAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentAuditRepository extends JpaRepository<ConsentAuditEntity, Long> {
}

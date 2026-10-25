package com.boltpe.creditscore.repository;

import com.boltpe.creditscore.entity.VendorApiLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorApiLogRepository extends JpaRepository<VendorApiLogEntity, Long> {
    long deleteByUserId(String userId);
}

// src/main/java/com/contractanalysis/repository/ContractRepository.java
package com.contractanalysis.repository;

import com.contractanalysis.model.ContractAnalysis;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends MongoRepository<ContractAnalysis, String> {
    List<ContractAnalysis> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<ContractAnalysis> findByIdAndUserId(String id, String userId);
}

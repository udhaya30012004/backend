// src/main/java/com/contractanalysis/repository/UserRepository.java
package com.contractanalysis.repository;

import com.contractanalysis.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByEmail(String email);
}
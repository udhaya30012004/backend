// src/main/java/com/contractanalysis/model/User.java
package com.contractanalysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String googleId;
    
    @Indexed(unique = true)
    private String email;
    
    private String displayName;
    private String profilePicture;
    private boolean isPremium;
}
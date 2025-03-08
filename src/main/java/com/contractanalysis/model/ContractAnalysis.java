// src/main/java/com/contractanalysis/model/ContractAnalysis.java
package com.contractanalysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "contract_analysis")
public class ContractAnalysis {
    @Id
    private String id;
    
    private String userId;
    private String contractText;
    private List<Risk> risks;
    private List<Opportunity> opportunities;
    private String summary;
    private List<String> recommendations;
    private List<String> keyClauses;
    private String legalCompliance;
    private List<String> negotiationPoints;
    private String contractDuration;
    private String terminationConditions;
    private Integer overallScore;
    private CompensationStructure compensationStructure;
    private List<String> performanceMetrics;
    private Object intellectualPropertyClauses; // Can be String or List<String>
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    private Integer version;
    private UserFeedback userFeedback;
    private Map<String, String> customFields;
    private Date expirationDate;
    private String language;
    private String aiModel;
    private String contractType;
    private FinancialTerms financialTerms;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Risk {
        private String risk;
        private String explanation;
        private String severity; // low, medium, high
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Opportunity {
        private String opportunity;
        private String explanation;
        private String impact; // low, medium, high
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompensationStructure {
        private String baseSalary;
        private String bonuses;
        private String equity;
        private String otherBenefits;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserFeedback {
        private Integer rating;
        private String comments;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialTerms {
        private String description;
        private List<String> details;
    }
}
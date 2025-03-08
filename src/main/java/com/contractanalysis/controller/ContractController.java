package com.contractanalysis.controller;

import com.contractanalysis.model.ContractAnalysis;
import com.contractanalysis.repository.ContractRepository;
import com.contractanalysis.security.UserPrincipal;
import com.contractanalysis.service.AiService;
import com.contractanalysis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Slf4j
public class ContractController {

    private final ContractRepository contractRepository;
    private final AiService aiService;
    private final RedisService redisService;

    @GetMapping
    public ResponseEntity<List<ContractAnalysis>> getAllContracts(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = userPrincipal.getUser().getId();
        List<ContractAnalysis> contracts = contractRepository.findByUserIdOrderByCreatedAtDesc(userId);
        
        return ResponseEntity.ok(contracts);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ContractAnalysis> getContract(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = userPrincipal.getUser().getId();
        Optional<ContractAnalysis> contractOpt = contractRepository.findByIdAndUserId(id, userId);
        
        return contractOpt
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeContract(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            String userId = userPrincipal.getUser().getId();
            String contractText;
            
            // Get contract text from either file or direct text input
            if (file != null && !file.isEmpty()) {
                // Store file in Redis temporarily
                String fileKey = "file:" + UUID.randomUUID().toString();
                redisService.set(fileKey, file.getBytes(), Duration.ofHours(1));
                
                // Extract text from PDF
                contractText = aiService.extractTextFromPDF(fileKey);
            } else if (text != null && !text.isBlank()) {
                contractText = text;
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("error", "No contract content provided");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Detect contract type using AI
            String contractType = aiService.detectContractType(contractText);
            
            // Determine user tier based on premium status
            String tier = userPrincipal.getUser().isPremium() ? "premium" : "free";
            
            // Create initial contract analysis record
            ContractAnalysis initialAnalysis = ContractAnalysis.builder()
                    .userId(userId)
                    .contractText(contractText)
                    .contractType(contractType)
                    .createdAt(LocalDateTime.now())
                    .version(1)
                    .language("en")
                    .aiModel("gemini-1.5-pro")
                    .build();
            
            ContractAnalysis savedAnalysis = contractRepository.save(initialAnalysis);
            
            // Start async analysis with AI
            CompletableFuture<Map<String, Object>> analysisFuture = 
                    aiService.analyzeContractWithAI(contractText, tier, contractType);
            
            analysisFuture.thenAccept(analysisResults -> {
                try {
                    updateAnalysisWithResults(savedAnalysis.getId(), analysisResults);
                } catch (Exception e) {
                    log.error("Error updating analysis with results", e);
                }
            });
            
            // Return initial response to client
            Map<String, Object> response = new HashMap<>();
            response.put("analysisId", savedAnalysis.getId());
            response.put("status", "processing");
            response.put("contractType", contractType);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("Error analyzing contract", e);
            Map<String, String> response = new HashMap<>();
            response.put("error", "Error processing contract: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PutMapping("/{id}/feedback")
    public ResponseEntity<?> updateFeedback(
            @PathVariable String id,
            @RequestBody ContractAnalysis.UserFeedback feedback,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = userPrincipal.getUser().getId();
        Optional<ContractAnalysis> contractOpt = contractRepository.findByIdAndUserId(id, userId);
        
        if (contractOpt.isPresent()) {
            ContractAnalysis contract = contractOpt.get();
            contract.setUserFeedback(feedback);
            contractRepository.save(contract);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteContract(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = userPrincipal.getUser().getId();
        Optional<ContractAnalysis> contractOpt = contractRepository.findByIdAndUserId(id, userId);
        
        if (contractOpt.isPresent()) {
            contractRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/status/{id}")
    public ResponseEntity<?> getAnalysisStatus(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userId = userPrincipal.getUser().getId();
        Optional<ContractAnalysis> contractOpt = contractRepository.findByIdAndUserId(id, userId);
        
        if (contractOpt.isPresent()) {
            ContractAnalysis contract = contractOpt.get();
            Map<String, Object> response = new HashMap<>();
            
            // Determine analysis status
            boolean isComplete = contract.getSummary() != null && !contract.getSummary().isEmpty();
            
            response.put("analysisId", contract.getId());
            response.put("status", isComplete ? "complete" : "processing");
            
            if (isComplete) {
                response.put("contractType", contract.getContractType());
                response.put("summary", contract.getSummary());
                response.put("overallScore", contract.getOverallScore());
            }
            
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void updateAnalysisWithResults(String analysisId, Map<String, Object> results) {
        Optional<ContractAnalysis> analysisOpt = contractRepository.findById(analysisId);
        
        if (analysisOpt.isPresent()) {
            ContractAnalysis analysis = analysisOpt.get();
            
            // Update basic fields
            if (results.containsKey("summary")) {
                analysis.setSummary((String) results.get("summary"));
            }
            
            if (results.containsKey("overallScore")) {
                Object score = results.get("overallScore");
                if (score instanceof Integer) {
                    analysis.setOverallScore((Integer) score);
                } else if (score instanceof String) {
                    try {
                        analysis.setOverallScore(Integer.parseInt((String) score));
                    } catch (NumberFormatException e) {
                        log.error("Error parsing overall score", e);
                    }
                }
            }
            
            // Update risks
            if (results.containsKey("risks") && results.get("risks") instanceof List) {
                List<Map<String, String>> risksData = (List<Map<String, String>>) results.get("risks");
                List<ContractAnalysis.Risk> risks = new ArrayList<>();
                
                for (Map<String, String> riskData : risksData) {
                    ContractAnalysis.Risk risk = new ContractAnalysis.Risk(
                            riskData.get("risk"),
                            riskData.get("explanation"),
                            riskData.get("severity")
                    );
                    risks.add(risk);
                }
                
                analysis.setRisks(risks);
            }
            
            // Update opportunities
            if (results.containsKey("opportunities") && results.get("opportunities") instanceof List) {
                List<Map<String, String>> oppsData = (List<Map<String, String>>) results.get("opportunities");
                List<ContractAnalysis.Opportunity> opportunities = new ArrayList<>();
                
                for (Map<String, String> oppData : oppsData) {
                    ContractAnalysis.Opportunity opportunity = new ContractAnalysis.Opportunity(
                            oppData.get("opportunity"),
                            oppData.get("explanation"),
                            oppData.get("impact")
                    );
                    opportunities.add(opportunity);
                }
                
                analysis.setOpportunities(opportunities);
            }
            
            // Premium-specific fields
            if (results.containsKey("recommendations") && results.get("recommendations") instanceof List) {
                analysis.setRecommendations((List<String>) results.get("recommendations"));
            }
            
            if (results.containsKey("keyClauses") && results.get("keyClauses") instanceof List) {
                analysis.setKeyClauses((List<String>) results.get("keyClauses"));
            }
            
            if (results.containsKey("legalCompliance")) {
                analysis.setLegalCompliance((String) results.get("legalCompliance"));
            }
            
            if (results.containsKey("negotiationPoints") && results.get("negotiationPoints") instanceof List) {
                analysis.setNegotiationPoints((List<String>) results.get("negotiationPoints"));
            }
            
            if (results.containsKey("contractDuration")) {
                analysis.setContractDuration((String) results.get("contractDuration"));
            }
            
            if (results.containsKey("terminationConditions")) {
                analysis.setTerminationConditions((String) results.get("terminationConditions"));
            }
            
            if (results.containsKey("performanceMetrics") && results.get("performanceMetrics") instanceof List) {
                analysis.setPerformanceMetrics((List<String>) results.get("performanceMetrics"));
            }
            
            if (results.containsKey("intellectualPropertyClauses")) {
                analysis.setIntellectualPropertyClauses(results.get("intellectualPropertyClauses"));
            }
            
            // Financial terms
            if (results.containsKey("financialTerms") && results.get("financialTerms") instanceof Map) {
                Map<String, Object> financialData = (Map<String, Object>) results.get("financialTerms");
                String description = (String) financialData.get("description");
                List<String> details = (List<String>) financialData.get("details");
                
                ContractAnalysis.FinancialTerms financialTerms = new ContractAnalysis.FinancialTerms(
                        description,
                        details
                );
                
                analysis.setFinancialTerms(financialTerms);
            }
            
            // Save the updated analysis
            contractRepository.save(analysis);
            log.info("Analysis updated successfully: {}", analysisId);
        } else {
            log.error("Analysis not found for ID: {}", analysisId);
        }
    }
}
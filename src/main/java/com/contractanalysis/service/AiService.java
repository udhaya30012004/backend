// src/main/java/com/contractanalysis/service/AiService.java
package com.contractanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final RedisService redisService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${ai.gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${ai.model.name}")
    private String modelName;
    
    public String extractTextFromPDF(String fileKey) throws Exception {
        var pdfData = redisService.get(fileKey)
                .orElseThrow(() -> new Exception("File not found"));
                
        // In a real application, you would use a PDF extraction library here
        // For example: Apache PDFBox or iText
        
        // Placeholder implementation
        log.info("Extracting text from PDF with key: {}", fileKey);
        
        // Example implementation using PDFBox would be:
        // PDDocument document = PDDocument.load((byte[]) pdfData);
        // PDFTextStripper stripper = new PDFTextStripper();
        // String text = stripper.getText(document);
        // document.close();
        // return text;
        
        // For this example, we'll just return a placeholder text
        return "This is placeholder text representing the content extracted from the PDF. " +
               "In a real implementation, this would be the actual text content from the PDF document.";
    }
    
    public String detectContractType(String contractText) {
        try {
            log.info("Detecting contract type. Text length: {} chars", contractText.length());
            
            String prompt = String.format("""
                Analyze the following contract text and determine the type of contract it is.
                Provide only the contract type as a single string (e.g., "Employment", "Non-Disclosure Agreement", "Sales", "Lease", etc.).
                Do not include any additional explanation or text.
                
                Contract text:
                %s
                """, 
                contractText.substring(0, Math.min(contractText.length(), 2000))
            );
            
            JsonNode response = callGeminiAPI(prompt);
            if (response != null && response.has("candidates") && response.get("candidates").isArray() && 
                    response.get("candidates").size() > 0) {
                return response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText().trim();
            }
            
            return "Unknown Contract";
        } catch (Exception e) {
            log.error("Error detecting contract type", e);
            return "Unknown Contract";
        }
    }
    
    public CompletableFuture<Map<String, Object>> analyzeContractWithAI(String contractText, String tier, String contractType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Analyzing {} contract for {} tier user. Text length: {} chars", 
                        contractType, tier, contractText.length());
                
                String prompt;
                if ("premium".equals(tier)) {
                    prompt = createPremiumPrompt(contractType, contractText);
                } else {
                    prompt = createFreePrompt(contractType, contractText);
                }
                
                JsonNode response = callGeminiAPI(prompt);
                if (response != null && response.has("candidates") && response.get("candidates").isArray() && 
                        response.get("candidates").size() > 0) {
                    String responseText = response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
                    
                    // Clean the response text and parse JSON
                    responseText = responseText.replaceAll("```json\\s*|\\s*```", "").trim();
                    
                    try {
                        return objectMapper.readValue(responseText, Map.class);
                    } catch (Exception e) {
                        log.error("Error parsing JSON response", e);
                        return createFallbackAnalysis();
                    }
                }
                
                return createFallbackAnalysis();
            } catch (Exception e) {
                log.error("Contract analysis error", e);
                return createFallbackAnalysis();
            }
        });
    }
    
    private JsonNode callGeminiAPI(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + geminiApiKey;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            
            content.put("parts", new Object[]{part});
            requestBody.put("contents", new Object[]{content});
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String responseBody = restTemplate.postForObject(url, request, String.class);
            
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return null;
        }
    }
    
    private String createPremiumPrompt(String contractType, String contractText) {
        return String.format("""
            Analyze the following %s contract and provide:
            1. A list of at least 10 potential risks for the party receiving the contract, each with a brief explanation and severity level (low, medium, high).
            2. A list of at least 10 potential opportunities or benefits for the receiving party, each with a brief explanation and impact level (low, medium, high).
            3. A comprehensive summary of the contract, including key terms and conditions.
            4. Any recommendations for improving the contract from the receiving party's perspective.
            5. A list of key clauses in the contract.
            6. An assessment of the contract's legal compliance.
            7. A list of potential negotiation points.
            8. The contract duration or term, if applicable.
            9. A summary of termination conditions, if applicable.
            10. A breakdown of any financial terms or compensation structure, if applicable.
            11. Any performance metrics or KPIs mentioned, if applicable.
            12. A summary of any specific clauses relevant to this type of contract (e.g., intellectual property for employment contracts, warranties for sales contracts).
            13. An overall score from 1 to 100, with 100 being the highest. This score represents the overall favorability of the contract based on the identified risks and opportunities.
            
            Format your response as a JSON object with the following structure:
            {
              "risks": [{"risk": "Risk description", "explanation": "Brief explanation", "severity": "low|medium|high"}],
              "opportunities": [{"opportunity": "Opportunity description", "explanation": "Brief explanation", "impact": "low|medium|high"}],
              "summary": "Comprehensive summary of the contract",
              "recommendations": ["Recommendation 1", "Recommendation 2", ...],
              "keyClauses": ["Clause 1", "Clause 2", ...],
              "legalCompliance": "Assessment of legal compliance",
              "negotiationPoints": ["Point 1", "Point 2", ...],
              "contractDuration": "Duration of the contract, if applicable",
              "terminationConditions": "Summary of termination conditions, if applicable",
              "overallScore": "Overall score from 1 to 100",
              "financialTerms": {
                "description": "Overview of financial terms",
                "details": ["Detail 1", "Detail 2", ...]
              },
              "performanceMetrics": ["Metric 1", "Metric 2", ...],
              "specificClauses": "Summary of clauses specific to this contract type"
            }
            
            Important: Provide only the JSON object in your response, without any additional text or formatting.
            
            Contract text:
            %s
            """, 
            contractType, contractText
        );
    }
    
    private String createFreePrompt(String contractType, String contractText) {
        return String.format("""
            Analyze the following %s contract and provide:
            1. A list of at least 5 potential risks for the party receiving the contract, each with a brief explanation and severity level (low, medium, high).
            2. A list of at least 5 potential opportunities or benefits for the receiving party, each with a brief explanation and impact level (low, medium, high).
            3. A brief summary of the contract
            4. An overall score from 1 to 100, with 100 being the highest. This score represents the overall favorability of the contract based on the identified risks and opportunities.
            
            {
              "risks": [{"risk": "Risk description", "explanation": "Brief explanation", "severity": "low|medium|high"}],
              "opportunities": [{"opportunity": "Opportunity description", "explanation": "Brief explanation", "impact": "low|medium|high"}],
              "summary": "Brief summary of the contract",
              "overallScore": "Overall score from 1 to 100"
            }
            
            Important: Provide only the JSON object in your response, without any additional text or formatting.
            
            Contract text:
            %s
            """, 
            contractType, contractText
        );
    }
    
    private Map<String, Object> createFallbackAnalysis() {
        Map<String, Object> fallbackAnalysis = new HashMap<>();
        fallbackAnalysis.put("risks", new Object[]{
            Map.of(
                "risk", "Error analyzing contract", 
                "explanation", "The analysis service encountered an error", 
                "severity", "high"
            )
        });
        fallbackAnalysis.put("opportunities", new Object[]{
            Map.of(
                "opportunity", "Try again later", 
                "explanation", "The service may be temporarily unavailable", 
                "impact", "medium"
            )
        });
        fallbackAnalysis.put("summary", "Error analyzing contract. Please try again later.");
        fallbackAnalysis.put("overallScore", 50);
        
        return fallbackAnalysis;
    }
}
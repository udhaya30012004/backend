// src/main/java/com/contractanalysis/service/EmailService.java
package com.contractanalysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${resend.api.key}")
    private String resendApiKey;
    
    public void sendPremiumConfirmationEmail(String userEmail, String userName) {
        try {
            String url = "https://api.resend.com/emails";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + resendApiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("from", "Acme <onboarding@resend.dev>");
            requestBody.put("to", userEmail);
            requestBody.put("subject", "Welcome to Premium");
            requestBody.put("html", String.format("<p>Hi %s,</p><p>Welcome to Premium. You're now a Premium user!</p>", userName));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForObject(url, request, String.class);
            
            log.info("Premium confirmation email sent to {}", userEmail);
        } catch (Exception e) {
            log.error("Error sending premium confirmation email", e);
        }
    }
}
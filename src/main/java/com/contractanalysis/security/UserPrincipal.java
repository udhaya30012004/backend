// src/main/java/com/contractanalysis/security/UserPrincipal.java
package com.contractanalysis.security;

import com.contractanalysis.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UserPrincipal implements OAuth2User {

    @Getter
    private final User user;
    private final Map<String, Object> attributes;

    public UserPrincipal(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER"));
        
        if (user.isPremium()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PREMIUM"));
        }
        
        return authorities;
    }

    @Override
    public String getName() {
        return user.getId();
    }
}
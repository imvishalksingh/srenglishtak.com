package com.readiq.backend.auth;
import org.springframework.security.core.Authentication;
import java.util.UUID;
public record ApiPrincipal(UUID id,boolean admin){
    public static ApiPrincipal from(Authentication auth){return (ApiPrincipal)auth.getPrincipal();}
}

package com.readiq.backend.api;
import com.readiq.backend.auth.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.readiq.backend.data.Database;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;private final Database db;
    public AuthController(AuthService auth,Database db){this.auth=auth;this.db=db;}
    @PostMapping("/login")public Object login(@RequestBody Map<String,Object>b){return auth.login(b);}
    @PostMapping("/google")public Object google(@RequestBody Map<String,Object>b){return auth.google(b);}
    @PostMapping("/refresh")public Object refresh(@RequestBody Map<String,Object>b){return auth.refresh(b);}
    @PostMapping("/register")public ResponseEntity<?>register(@RequestBody Map<String,Object>b){return ResponseEntity.status(201).body(auth.register(b));}
    @PostMapping("/forgot-password")public Object forgot(@RequestBody Map<String,Object>b){return auth.forgot(b);}
    @PostMapping("/logout")public Object logout(@RequestHeader("Authorization")String token){return auth.logout(token.substring(7));}
    @GetMapping("/me")public Object me(Authentication a){return db.byId("profiles",ApiPrincipal.from(a).id());}
}

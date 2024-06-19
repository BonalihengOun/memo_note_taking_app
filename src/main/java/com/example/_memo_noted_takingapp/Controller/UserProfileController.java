package com.example._memo_noted_takingapp.Controller;


import com.example._memo_noted_takingapp.Model.dto.Response.UserResponse;
import com.example._memo_noted_takingapp.Repositority.OtpRepository;
import com.example._memo_noted_takingapp.Repositority.UserRepository;
import com.example._memo_noted_takingapp.Service.AuthService;
import com.example._memo_noted_takingapp.Service.UserProfile;
import com.example._memo_noted_takingapp.Service.UserService;
import com.example._memo_noted_takingapp.config.PasswordConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:8080")
@AllArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {
    private final UserProfile userProfile;

    @PutMapping("/api/memo/notes/Auth/changeUsername")
    public ResponseEntity<UserResponse> changeUsername(@RequestParam String email, @RequestBody @Valid String changeUsername) {
        UserResponse userResponse = userProfile.changeUsername(email, changeUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }


}

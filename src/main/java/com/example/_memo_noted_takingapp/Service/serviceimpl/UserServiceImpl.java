package com.example._memo_noted_takingapp.Service.serviceimpl;

import com.example._memo_noted_takingapp.Exception.InvalidInputException;
import com.example._memo_noted_takingapp.Exception.NotFoundException;
import com.example._memo_noted_takingapp.Jwt.JwtService;
import com.example._memo_noted_takingapp.Model.Otp;
import com.example._memo_noted_takingapp.Model.User;
import com.example._memo_noted_takingapp.Model.dto.Request.ForgetRequest;
import com.example._memo_noted_takingapp.Model.dto.Request.LoginRequest;
import com.example._memo_noted_takingapp.Model.dto.Request.RegisterRequest;
import com.example._memo_noted_takingapp.Model.dto.Response.AuthResponse;
import com.example._memo_noted_takingapp.Model.dto.Response.UserResponse;
import com.example._memo_noted_takingapp.Repositority.OtpRepository;
import com.example._memo_noted_takingapp.Repositority.UserRepository;
import com.example._memo_noted_takingapp.Service.AuthService;
import com.example._memo_noted_takingapp.Service.UserService;
import com.example._memo_noted_takingapp.config.PasswordConfig;
import com.example._memo_noted_takingapp.util.EmailUtil;
import com.example._memo_noted_takingapp.util.OtpUtil;
import jakarta.mail.MessagingException;

import lombok.AllArgsConstructor;

import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpUtil otpUtil;
    private final EmailUtil emailUtil;
    private final AuthService authService;
    private final PasswordConfig passwordConfig;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public UserResponse register(RegisterRequest registerRequest) throws MessagingException {
        User checkEmail = userRepository.getUserByEmail(registerRequest.getEmail());
        if (checkEmail != null) throw new InvalidInputException("Email already register, Please enter another email");

        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword()) || registerRequest.getPassword().length() < 8)
            throw new InvalidInputException("Passwords do not match or have at least 8 characters");
        registerRequest.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        String otpCode = otpUtil.generateOtp();
        User user = new User();
        user.setName(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(registerRequest.getPassword());

        System.out.println(user);
        User savedUser = userRepository.save(user);
        Otp otp = new Otp();
        otp.setUserId(savedUser.getUserId());
        otp.setOtpCode(otpCode);
        otp.setIssuedAt(new Timestamp(System.currentTimeMillis()));
        otp.setExpirationTime(calculateExpirationTime());
        otpRepository.insertOtp(otp);
        emailUtil.sendOtpEmail(savedUser.getEmail(), otpCode);
        return new UserResponse(savedUser.getUserId(), savedUser.getName(), savedUser.getEmail());
    }
    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        UserDetails userDetails = authService.loadUserByUsername(loginRequest.getEmail());
        if (userDetails != null) {
            User user = userRepository.getUserByEmail(loginRequest.getEmail());
            if (user != null) {
                Otp latestOtp = otpRepository.getOtpByUserId(user.getUserId());
                if (!passwordConfig.passwordEncoder().matches(loginRequest.getPassword(), userDetails.getPassword())) throw new NotFoundException(
                        "Your password is Invalid, please try again :).");
                if (latestOtp == null || !latestOtp.isVerified()) throw new InvalidInputException("Your account is not verified yet, please try again.");


                String token = jwtService.generateToken(userDetails.getUsername());
                return new AuthResponse(
                        user.getUserId(), user.getName(), user.getEmail(), token);
            }
        } throw new NotFoundException("User not found with email " + loginRequest.getEmail());

    }

    @Override
    public boolean verifyOtp(String  otpCode) {
        System.out.println("OTP code is: " + otpCode);
        Otp latestOtp = otpRepository.getLatestOtpByCode(otpCode);
        if (latestOtp != null && !latestOtp.isVerified()) {
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            Timestamp expirationTime = latestOtp.getExpirationTime();
            if (expirationTime.after(currentTime)) {
                if (latestOtp.getOtpCode().equals(otpCode)) {
                    if (!latestOtp.isVerified()) {
                        latestOtp.setVerified(true);
                        otpRepository.updateOtp(latestOtp);
                    }
                    return true;
                }
            } throw new NotFoundException("OTP code is Invalid or Expiration, please try again.");
        }
        throw new NotFoundException("Your account is already verified");
    }

    @Override
    public String resendOtp(String email) {
        User user = userRepository.getUserByEmail(email);
        if (user == null) throw new NotFoundException("User with " + email + " not found.");
        String newOtpCode = otpUtil.generateOtp();
        try {
            emailUtil.sendOtpEmail(email, newOtpCode);
        } catch (MessagingException e) {
            return "Failed to send OTP email. Please try again later.";
        }
        Otp existingOtp = otpRepository.getLatestUnverifiedOtpByEmail(email);
        if (existingOtp == null) throw new NotFoundException("Failed to Resend OTP, Your Account are already Verify");
        existingOtp.setOtpCode(newOtpCode);
        existingOtp.setIssuedAt(new Timestamp(System.currentTimeMillis()));
        existingOtp.setExpirationTime(calculateExpirationTime());
        otpRepository.updateOtp(existingOtp);
        return "OTP resent successfully.";
    }

    @Override
    public UserResponse ResetPassword(ForgetRequest forgetRequest, String email) {
        // Check if the email exists in the database
        User user = userRepository.getUserByEmail(email);
        if (user == null) throw new NotFoundException("Email not found for update password");

        // Check if the email is verified using OTP
        Otp latestOtp = otpRepository.getOtpByUserId(user.getUserId());
        if (latestOtp == null || !latestOtp.isVerified()) throw new InvalidInputException("This Email is not verified for updating the password, please verify your email first.");

        // Validate the new password and confirm password
        if (!forgetRequest.getPassword().equals(forgetRequest.getConfirmPassword()) || forgetRequest.getPassword().length() < 8)   throw new InvalidInputException("Your confirm password does not match with your password");

        // Update the user's password
        forgetRequest.setPassword(passwordEncoder.encode(forgetRequest.getPassword()));
        User userPassword = userRepository.updatePassword(forgetRequest,email);
        return modelMapper.map(userPassword, UserResponse.class);
    }



    private Timestamp calculateExpirationTime() {
        long currentTimeMillis = System.currentTimeMillis();
        long expirationTimeMillis = currentTimeMillis + (1 * 60 * 1000);
        return new Timestamp(expirationTimeMillis);
    }
    @Override
    public Long getUsernameOfCurrentUser() {
        User userDetails = (User) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        Long userId = userDetails.getUserId();
        System.out.println(userId);
        return userId;
    }

    @Override
    public UserResponse getUserDetailsById(Long userId) {
        UserResponse userResponse = userRepository.getUserById(userId);
        if (userResponse == null) {
            throw new NotFoundException("User not found with ID: " + userId);
        }
        return userResponse;
    }

    @Override
    public Boolean requestOtp(String email) throws MessagingException {
        User user = userRepository.getUserByEmail(email);
        if (user == null) throw new NotFoundException("User with " + email + " not found.");
        String newOtpCode = otpUtil.generateOtp();
        try {
            emailUtil.sendOtpEmail(email, newOtpCode);
        } catch (MessagingException e) {
            throw new InvalidInputException("Failed to send OTP email. Please try again later.");
        }
        Otp existingOtp = otpRepository.getLatestOtpByEmailAndVerified(email);
        if (existingOtp == null) throw new NotFoundException("Failed to send OTP, Your Account are already Verify");
        existingOtp.setOtpCode(newOtpCode);
        existingOtp.setIssuedAt(new Timestamp(System.currentTimeMillis()));
        existingOtp.setExpirationTime(calculateExpirationTime());
        otpRepository.updateOtp(existingOtp);
        return true;
    }

    @Override
    public UserResponse verifyOtpForgetPassword(String email, ForgetRequest forgetRequest) {
        User user = userRepository.getUserByEmail(email);
        if (user == null) {
            throw new NotFoundException("Email not found");
        }
//        Otp otp = otpRepository.getLatestOtpByCodeAndVerified(otpCode);
//        if (otp == null || !otp.getOtpCode().equals(otpCode) || otp.getExpirationTime().before(new Timestamp(System.currentTimeMillis()))) {
//            throw new InvalidInputException("Invalid or expired OTP");
//        }
        // Validate the new password and confirm password
        if (!forgetRequest.getPassword().equals(forgetRequest.getConfirmPassword()) || forgetRequest.getPassword().length() < 8) {
            throw new InvalidInputException("Your confirm password does not match with your password or password is too short");
        }
        // Update the user's password
        forgetRequest.setPassword(passwordEncoder.encode(forgetRequest.getPassword()));
        User userPassword = userRepository.updatePassword(forgetRequest, email);
        return modelMapper.map(userPassword, UserResponse.class);
    }

    @Override
    public boolean verifyOtpForgetPassword(String  otpCode) {
        System.out.println("OTP code is: " + otpCode);
        Otp otp = otpRepository.getLatestOtpByCodeAndVerified(otpCode);
        if (otp == null || !otp.getOtpCode().equals(otpCode) || otp.getExpirationTime().before(new Timestamp(System.currentTimeMillis()))) {
            throw new InvalidInputException("Invalid or expired OTP");
        }
        return true;
    }


}

package com.backend.auth.service;

import com.backend.auth.domain.dto.response.OtpResponse;
import com.backend.auth.domain.entity.OtpCode;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final JwtService jwtService;

    // SecureRandom is thread-safe and cryptographically strong.
    // Never use java.util.Random for security-sensitive values.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // private static final int OTP_LENGTH = 6;
    private static final long OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final int MAX_SENDS_PER_HOUR = 3;

    // -----------------------------------------------------------
    // Send OTP
    // -----------------------------------------------------------

    @Transactional
    public OtpResponse sendOtp(String phone, OtpCode.Purpose purpose) {
        // Rate limit: max 3 OTPs per phone per hour
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        long recentCount = otpCodeRepository.countRecentByPhone(phone, purpose, oneHourAgo);

        if (recentCount >= MAX_SENDS_PER_HOUR) {
            throw new AppException(ErrorCode.OTP_SEND_RATE_LIMIT_EXCEEDED);
        }

        // Generate a 6-digit OTP
        String plainOtp = generateOtp();

        // Store hashed OTP — never the plain value
        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .codeHash(hashOtp(plainOtp))
                .purpose(purpose)
                .expiresAt(OffsetDateTime.now().plusSeconds(OTP_EXPIRY_SECONDS))
                .build();

        otpCodeRepository.save(otpCode);

        // TODO: integrate real SMS gateway here (Twilio, VNPT SMS, etc.)
        // For now, log the OTP — REMOVE THIS in production
        log.info("OTP for {} [{}]: {}", phone, purpose, plainOtp);

        return OtpResponse.builder()
                .success(true)
                .message("OTP sent successfully")
                .expiresInSeconds(OTP_EXPIRY_SECONDS)
                .build();
    }

    // -----------------------------------------------------------
    // Verify OTP
    // Returns a short-lived verification token if successful.
    // Client must include this token in the registration request.
    // -----------------------------------------------------------

    @Transactional
    public OtpResponse verifyOtp(String phone, String plainCode, OtpCode.Purpose purpose) {
        OtpCode otpCode = otpCodeRepository
                .findLatestUsable(phone, purpose, OffsetDateTime.now())
                .orElseThrow(() -> new AppException(ErrorCode.OTP_NOT_FOUND));

        // Increment attempts before checking — prevents timing attacks where
        // the attacker checks if incrementing happens only on wrong attempts
        otpCode.incrementAttempts();
        otpCodeRepository.save(otpCode);

        if (otpCode.isExceededAttempts()) {
            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }

        // Constant-time comparison to prevent timing attacks
        boolean isMatch = MessageDigest.isEqual(
                hashOtp(plainCode).getBytes(StandardCharsets.UTF_8),
                otpCode.getCodeHash().getBytes(StandardCharsets.UTF_8));

        if (!isMatch) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        // Mark as verified so it cannot be reused
        otpCode.markVerified();
        otpCodeRepository.save(otpCode);

        // Issue a verification token — proves this phone was verified right now
        String verificationToken = jwtService.generateOtpVerificationToken(phone, purpose);

        return OtpResponse.builder()
                .success(true)
                .message("OTP verified successfully")
                .expiresInSeconds(300) // verification token valid for 5 minutes
                .verificationToken(verificationToken)
                .build();
    }

    // -----------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------

    private String generateOtp() {
        // Generate number in range [100000, 999999] — always 6 digits
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }

    // SHA-256 hash of the OTP code
    public String hashOtp(String plainCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
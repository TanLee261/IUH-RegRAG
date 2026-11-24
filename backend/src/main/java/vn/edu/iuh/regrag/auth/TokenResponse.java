package vn.edu.iuh.regrag.auth;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}

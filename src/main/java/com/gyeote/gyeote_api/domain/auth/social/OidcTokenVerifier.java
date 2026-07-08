package com.gyeote.gyeote_api.domain.auth.social;

import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

abstract class OidcTokenVerifier implements SocialProviderTokenVerifier {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

    private final JwtDecoder jwtDecoder;
    private final Set<String> clientIds;

    protected OidcTokenVerifier(String issuer, String jwkSetUri, String clientIdsCsv) {
        this.clientIds = parseClientIds(clientIdsCsv);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(tokenValidator(issuer));
        this.jwtDecoder = decoder;
    }

    @Override
    public String verify(String providerToken) {
        if (clientIds.isEmpty()) {
            throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_CLIENT_ID_NOT_CONFIGURED);
        }

        try {
            Jwt jwt = jwtDecoder.decode(providerToken);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new BusinessException(AuthErrorCode.AUTH_PROVIDER_TOKEN_UNAUTHORIZED);
            }
            return subject;
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException e) {
            throw new BusinessException(AuthErrorCode.AUTH_PROVIDER_TOKEN_UNAUTHORIZED);
        }
    }

    private OAuth2TokenValidator<Jwt> tokenValidator(String issuer) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(60));
        return jwt -> {
            OAuth2TokenValidatorResult timestampResult = timestampValidator.validate(jwt);
            if (timestampResult.hasErrors()) {
                return timestampResult;
            }
            if (jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            if (jwt.getAudience().stream().noneMatch(clientIds::contains)) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private Set<String> parseClientIds(String clientIdsCsv) {
        if (clientIdsCsv == null || clientIdsCsv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(clientIdsCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}

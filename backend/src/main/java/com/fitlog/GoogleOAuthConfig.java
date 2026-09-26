package com.fitlog;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;

@Configuration
@ConditionalOnProperty(name="fitlog.google.enabled",havingValue="true")
public class GoogleOAuthConfig {
 @Bean ClientRegistrationRepository googleRegistration(@Value("${GOOGLE_CLIENT_ID}") String id,@Value("${GOOGLE_CLIENT_SECRET}") String secret,@Value("${GOOGLE_REDIRECT_URI:http://127.0.0.1:8080/api/auth/google/callback/google}") String redirect){
  if(id.isBlank()||secret.isBlank())throw new IllegalStateException("Google OAuth credentials are required");
  return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(id).clientSecret(secret).scope("openid","email","profile").redirectUri(redirect).build());
 }
}

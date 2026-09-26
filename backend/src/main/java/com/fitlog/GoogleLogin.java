package com.fitlog;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Component
public class GoogleLogin {
 static final String PENDING="FITLOG_GOOGLE_LINK";
 public record Pending(String memberId,String subject,String email,Instant expires,int attempts) implements java.io.Serializable {}
 private final GoogleMembers members;
 final boolean enabled;
 public GoogleLogin(GoogleMembers members,@Value("${fitlog.google.enabled:false}") boolean enabled){this.members=members;this.enabled=enabled;}
 static void session(HttpServletRequest request,HttpServletResponse response,String memberId){
  var context=SecurityContextHolder.createEmptyContext();
  if(memberId!=null){request.getSession();request.changeSessionId();new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository().saveToken(null,request,response);context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(memberId,null,List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));}
  SecurityContextHolder.setContext(context);
  new HttpSessionSecurityContextRepository().saveContext(context,request,response);
 }
 public void success(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException {
  request.getSession().removeAttribute(PENDING);
  try {
   OidcUser user=(OidcUser)authentication.getPrincipal();
   String email=user.getEmail(),subject=user.getSubject();
   if(!Boolean.TRUE.equals(user.getEmailVerified())||email==null||email.isBlank()||email.length()>254||subject==null||subject.isBlank()||subject.length()>255)throw new IllegalArgumentException();
   var result=members.resolve(subject,email,user.getFullName());
   if(result.linkRequired()){
    session(request,response,null);
    request.getSession().setAttribute(PENDING,new Pending(result.memberId(),subject,email,Instant.now().plusSeconds(300),0));
    response.sendRedirect("/?google=link_required");
   } else {session(request,response,result.memberId());response.sendRedirect("/");}
  } catch(Exception ex){session(request,response,null);response.sendRedirect("/?google=failed");}
 }
}

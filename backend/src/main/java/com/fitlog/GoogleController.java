package com.fitlog;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleController {
 private final GoogleLogin login;
 private final GoogleMembers members;
 public GoogleController(GoogleLogin login,GoogleMembers members){this.login=login;this.members=members;}
 private GoogleLogin.Pending pending(HttpServletRequest request){
  var session=request.getSession(false);if(session==null)return null;
  var p=(GoogleLogin.Pending)session.getAttribute(GoogleLogin.PENDING);
  if(p!=null&&!p.expires().isAfter(Instant.now())){session.removeAttribute(GoogleLogin.PENDING);return null;}return p;
 }
 @GetMapping("/status") public Map<String,Object> status(HttpServletRequest request){var p=pending(request);return Map.of("enabled",login.enabled,"linkEmail",p==null?"":p.email());}
 public record Link(@NotNull @Size(min=1,max=72) String password){}
 @PostMapping("/link") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void link(@Valid @RequestBody Link input,HttpServletRequest request,HttpServletResponse response){
  if(!login.enabled)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Google 登入尚未啟用");
  var p=pending(request);if(p==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"驗證已過期，請重新使用 Google 登入");
  // Consume before checking credentials so the same pending identity is never reusable.
  request.getSession().removeAttribute(GoogleLogin.PENDING);
  if(!members.link(p.memberId(),p.subject(),input.password())){
   if(p.attempts()<4)request.getSession().setAttribute(GoogleLogin.PENDING,new GoogleLogin.Pending(p.memberId(),p.subject(),p.email(),p.expires(),p.attempts()+1));
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"無法連結，請確認既有帳號密碼；多次失敗請重新使用 Google 登入");
  }
  GoogleLogin.session(request,response,p.memberId());
 }
 @PostMapping("/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void cancel(HttpServletRequest request){if(request.getSession(false)!=null)request.getSession(false).removeAttribute(GoogleLogin.PENDING);}
}

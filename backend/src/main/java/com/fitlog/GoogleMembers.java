package com.fitlog;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleMembers {
 private final JdbcTemplate jdbc;
 private final PasswordEncoder passwords;
 public GoogleMembers(JdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
 public record Result(String memberId,boolean linkRequired){}
 @Transactional public Result resolve(String subject,String email,String name){
  boolean claimed=jdbc.queryForObject("SELECT legacy_claimed FROM membership_state WHERE id=1 FOR UPDATE",Boolean.class);
  var known=jdbc.query("SELECT id FROM members WHERE google_subject=?",(r,n)->r.getString(1),subject);
  if(!known.isEmpty())return new Result(known.get(0),false);
  email=email.trim().toLowerCase(Locale.ROOT);
  var existing=jdbc.query("SELECT id FROM members WHERE email=?",(r,n)->r.getString(1),email);
  if(!existing.isEmpty())return new Result(existing.get(0),true);
  String id=UUID.randomUUID().toString();
  String display=name==null||name.isBlank()?"Google 會員":name.strip();
  if(display.length()>60)display=display.substring(0,60);
  // Unknowable random password: Google-only accounts cannot use password login.
  jdbc.update("INSERT INTO members(id,email,display_name,password_hash,google_subject) VALUES(?,?,?,?,?)",id,email,display,passwords.encode(UUID.randomUUID().toString()+UUID.randomUUID()),subject);
  if(!claimed){for(String table:List.of("workouts","templates","custom_exercises"))jdbc.update("UPDATE "+table+" SET owner_id=? WHERE owner_id IS NULL",id);jdbc.update("UPDATE membership_state SET legacy_claimed=TRUE WHERE id=1");}
  return new Result(id,false);
 }
 @Transactional public boolean link(String memberId,String subject,String password){
  jdbc.queryForObject("SELECT legacy_claimed FROM membership_state WHERE id=1 FOR UPDATE",Boolean.class);
  var hashes=jdbc.query("SELECT password_hash FROM members WHERE id=?",(r,n)->r.getString(1),memberId);
  if(hashes.isEmpty()||!passwords.matches(password,hashes.get(0)))return false;
  if(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE google_subject=? AND id<>?",Integer.class,subject,memberId)>0)return false;
  return jdbc.update("UPDATE members SET google_subject=? WHERE id=? AND (google_subject IS NULL OR google_subject=?)",subject,memberId,subject)==1;
 }
}

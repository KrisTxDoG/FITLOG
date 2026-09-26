package com.fitlog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
 private final JdbcTemplate jdbc;
 public ScheduleController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Input(@NotBlank String templateId,@NotNull @FutureOrPresent LocalDate date){}
 public record Entry(String id,String templateId,String title,LocalDate date){}
 @GetMapping public List<Entry> list(){return jdbc.query("SELECT s.*,t.title FROM scheduled_workouts s JOIN templates t ON t.id=s.template_id WHERE s.owner_id=? AND t.owner_id=? ORDER BY s.scheduled_date,s.id",(r,n)->new Entry(r.getString("id"),r.getString("template_id"),r.getString("title"),r.getObject("scheduled_date",LocalDate.class)),SecurityConfig.memberId(),SecurityConfig.memberId());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Entry add(@Valid @RequestBody Input input){
  var titles=jdbc.query("SELECT title FROM templates WHERE id=? AND owner_id=? FOR UPDATE",(r,n)->r.getString(1),input.templateId(),SecurityConfig.memberId());
  if(titles.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"找不到課表");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM scheduled_workouts WHERE owner_id=? AND template_id=? AND scheduled_date=?",Integer.class,SecurityConfig.memberId(),input.templateId(),input.date())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"這天已安排相同課表");
  String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO scheduled_workouts(id,owner_id,template_id,scheduled_date) VALUES(?,?,?,?)",id,SecurityConfig.memberId(),input.templateId(),input.date());return new Entry(id,input.templateId(),titles.get(0),input.date());
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable String id){if(jdbc.update("DELETE FROM scheduled_workouts WHERE id=? AND owner_id=?",id,SecurityConfig.memberId())==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"找不到安排");}
}

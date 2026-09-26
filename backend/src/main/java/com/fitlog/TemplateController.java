package com.fitlog;

import jakarta.validation.Valid;
import com.fitlog.WorkoutController.SetInput;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
 private final JdbcTemplate jdbc;
 private final WorkoutController workouts;
 public TemplateController(JdbcTemplate jdbc, WorkoutController workouts) {this.jdbc=jdbc;this.workouts=workouts;}
 public record Input(@NotBlank @Size(max=80) String title,@Min(1) @Max(600) int duration,
  @NotNull @Size(max=1000) String notes,@NotEmpty @Size(max=200) List<@NotNull @Valid SetInput> sets) {}
 public record Template(String id,String title,int duration,String notes,List<WorkoutController.SetInput> sets) {}
 @GetMapping public List<Template> list() {
  Map<String,List<WorkoutController.SetInput>> sets=new HashMap<>();
  jdbc.query("SELECT s.* FROM template_sets s JOIN templates t ON t.id=s.template_id WHERE t.owner_id=? ORDER BY s.id",rs->{sets.computeIfAbsent(rs.getString("template_id"),k->new ArrayList<>()).add(new WorkoutController.SetInput(rs.getString("exercise_id"),rs.getInt("reps"),rs.getBigDecimal("weight"),false,rs.getBigDecimal("rpe"),rs.getString("superset_id")));},SecurityConfig.memberId());
  return jdbc.query("SELECT * FROM templates WHERE owner_id=? ORDER BY title,id",(rs,n)->new Template(rs.getString("id"),rs.getString("title"),rs.getInt("duration_minutes"),rs.getString("notes"),sets.getOrDefault(rs.getString("id"),List.of())),SecurityConfig.memberId());
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Template create(@Valid @RequestBody Input input) {return save(UUID.randomUUID().toString(),input,false);}
 @PutMapping("/{id}") @Transactional
 public Template update(@PathVariable String id,@Valid @RequestBody Input input) {return save(id,input,true);}
 private Template save(String id,Input input,boolean update) {
  SetRules.validate(input.sets());
  var catalog=workouts.exercises();
  if(input.sets().stream().anyMatch(s->catalog.stream().noneMatch(e->e.id().equals(s.exerciseId()))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"無效的訓練動作");
  if(update) {
   if(jdbc.update("UPDATE templates SET title=?,duration_minutes=?,notes=? WHERE id=? AND owner_id=?",input.title().trim(),input.duration(),input.notes(),id,SecurityConfig.memberId())==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"找不到課表");
   jdbc.update("DELETE FROM template_sets WHERE template_id=?",id);
  } else jdbc.update("INSERT INTO templates(id,title,duration_minutes,notes,owner_id) VALUES(?,?,?,?,?)",id,input.title().trim(),input.duration(),input.notes(),SecurityConfig.memberId());
  var sets=input.sets().stream().map(s->new WorkoutController.SetInput(s.exerciseId(),s.reps(),s.weight(),false,s.rpe(),s.supersetId())).toList();
  for(var s:sets)jdbc.update("INSERT INTO template_sets(template_id,exercise_id,reps,weight,rpe,superset_id) VALUES(?,?,?,?,?,?)",id,s.exerciseId(),s.reps(),s.weight(),s.rpe(),s.supersetId());
  return new Template(id,input.title().trim(),input.duration(),input.notes(),sets);
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
 public void delete(@PathVariable String id) {
  if(jdbc.update("DELETE FROM templates WHERE id=? AND owner_id=?",id,SecurityConfig.memberId())==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"找不到課表");
 }
}

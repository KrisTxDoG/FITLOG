package com.fitlog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goal")
public class GoalController {
 private final JdbcTemplate jdbc;
 public GoalController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Goal(@Min(1) @Max(7) int days) {}
 @GetMapping public Goal get(){return new Goal(jdbc.queryForObject("SELECT weekly_goal FROM members WHERE id=?",Integer.class,SecurityConfig.memberId()));}
 @PutMapping public Goal save(@Valid @RequestBody Goal goal){jdbc.update("UPDATE members SET weekly_goal=? WHERE id=?",goal.days(),SecurityConfig.memberId());return goal;}
}

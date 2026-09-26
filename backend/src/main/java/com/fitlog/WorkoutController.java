package com.fitlog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class WorkoutController {
    private final JdbcTemplate jdbc;
    public WorkoutController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Exercise(String id, String name, String muscle, String equipment, int restSeconds) {
        public Exercise(String id, String name, String muscle, String equipment) { this(id,name,muscle,equipment,90); }
    }
    public record ExerciseInput(@NotBlank @Size(max=80) String name, @NotBlank @Size(max=40) String muscle,
        @NotBlank @Size(max=60) String equipment, @Min(5) @Max(900) int restSeconds) {}
    public static final List<Exercise> EXERCISES = List.of(
        new Exercise("squat", "槓鈴深蹲", "腿部", "槓鈴"),
        new Exercise("bench", "槓鈴臥推", "胸部", "槓鈴"),
        new Exercise("deadlift", "硬舉", "背部", "槓鈴"),
        new Exercise("row", "啞鈴划船", "背部", "啞鈴"),
        new Exercise("press", "啞鈴肩推", "肩部", "啞鈴"),
        new Exercise("curl", "啞鈴彎舉", "手臂", "啞鈴"),
        new Exercise("pullup", "引體向上", "背部", "自體重量"),
        new Exercise("pushup", "伏地挺身", "胸部", "自體重量"),
        new Exercise("lunge", "弓箭步", "腿部", "啞鈴"),
        new Exercise("crunch", "捲腹", "核心", "自體重量"),
        new Exercise("legpress", "腿推機", "腿部", "機械器材"),
        new Exercise("pulldown", "滑輪下拉", "背部", "滑輪器材"),
        new Exercise("chestpress", "坐姿胸推機", "胸部", "機械器材"),
        new Exercise("cablefly", "滑輪夾胸", "胸部", "滑輪器材"),
        new Exercise("legcurl", "腿後勾機", "腿部", "機械器材"),
        new Exercise("triceps", "滑輪三頭下壓", "手臂", "滑輪器材")
    );
    public record SetInput(@NotBlank String exerciseId, @Min(1) @Max(1000) int reps,
        @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4, fraction=2) BigDecimal weight, boolean completed,
        @DecimalMin("1") @DecimalMax("10") @Digits(integer=2,fraction=1) BigDecimal rpe,
        @Size(max=36) @Pattern(regexp="[A-Za-z0-9-]+") String supersetId) {
        public SetInput(String exerciseId,int reps,BigDecimal weight,boolean completed){this(exerciseId,reps,weight,completed,null,null);}
    }
    public record WorkoutInput(@NotBlank @Size(max=80) String title,
        @NotNull @PastOrPresent LocalDate date, @Min(1) @Max(600) int duration,
        @NotNull @Size(max=1000) String notes, @NotEmpty @Size(max=200) List<@NotNull @Valid SetInput> sets) {}
    public record Workout(String id, String title, LocalDate date, int duration, String notes, List<SetInput> sets) {}

    @GetMapping("/exercises") public List<Exercise> exercises() {
        var result = new ArrayList<>(EXERCISES);
        result.addAll(jdbc.query("SELECT * FROM custom_exercises WHERE owner_id=? ORDER BY name,id", (rs,n)->new Exercise(
            rs.getString("id"),rs.getString("name"),rs.getString("muscle"),rs.getString("equipment"),rs.getInt("rest_seconds")),SecurityConfig.memberId()));
        return result;
    }
    @PostMapping("/exercises") @ResponseStatus(HttpStatus.CREATED)
    public Exercise addExercise(@Valid @RequestBody ExerciseInput input) {
        var result = new Exercise(UUID.randomUUID().toString(),input.name().trim(),input.muscle().trim(),input.equipment().trim(),input.restSeconds());
        jdbc.update("INSERT INTO custom_exercises(id,name,muscle,equipment,rest_seconds,owner_id) VALUES (?,?,?,?,?,?)",result.id(),result.name(),result.muscle(),result.equipment(),result.restSeconds(),SecurityConfig.memberId());
        return result;
    }

    @GetMapping("/workouts") public List<Workout> list() {
        var sets = new HashMap<String, List<SetInput>>();
        jdbc.query("SELECT s.* FROM workout_sets s JOIN workouts w ON w.id=s.workout_id WHERE w.owner_id=? ORDER BY s.id", rs -> {
            sets.computeIfAbsent(rs.getString("workout_id"), k -> new ArrayList<>()).add(
                new SetInput(rs.getString("exercise_id"), rs.getInt("reps"), rs.getBigDecimal("weight"),rs.getBoolean("completed"),rs.getBigDecimal("rpe"),rs.getString("superset_id")));
        },SecurityConfig.memberId());
        return jdbc.query("SELECT * FROM workouts WHERE owner_id=? ORDER BY workout_date DESC, created_order DESC", (rs, n) -> new Workout(
            rs.getString("id"), rs.getString("title"), rs.getObject("workout_date", LocalDate.class),
            rs.getInt("duration_minutes"), rs.getString("notes"), sets.getOrDefault(rs.getString("id"), List.of())),SecurityConfig.memberId());
    }

    @PostMapping("/workouts") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Workout create(@Valid @RequestBody WorkoutInput input) { return save(UUID.randomUUID().toString(), input, false); }

    @PutMapping("/workouts/{id}") @Transactional
    public Workout update(@PathVariable String id, @Valid @RequestBody WorkoutInput input) { return save(id, input, true); }

    private Workout save(String id, WorkoutInput input, boolean update) {
        SetRules.validate(input.sets());
        var catalog = exercises();
        if (input.sets().stream().anyMatch(s -> catalog.stream().noneMatch(e -> e.id().equals(s.exerciseId()))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "無效的訓練動作");
        if (update) {
            if (jdbc.update("UPDATE workouts SET title=?,workout_date=?,duration_minutes=?,notes=? WHERE id=? AND owner_id=?",
                input.title().trim(), input.date(), input.duration(), input.notes(), id,SecurityConfig.memberId()) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到訓練紀錄");
            jdbc.update("DELETE FROM workout_sets WHERE workout_id=?", id);
        } else {
            jdbc.update("INSERT INTO workouts(id,title,workout_date,duration_minutes,notes,owner_id) VALUES (?,?,?,?,?,?)", id, input.title().trim(), input.date(), input.duration(), input.notes(),SecurityConfig.memberId());
        }
        for (var s : input.sets()) jdbc.update("INSERT INTO workout_sets(workout_id,exercise_id,reps,weight,completed,rpe,superset_id) VALUES(?,?,?,?,?,?,?)", id, s.exerciseId(), s.reps(), s.weight(),s.completed(),s.rpe(),s.supersetId());
        return new Workout(id, input.title().trim(), input.date(), input.duration(), input.notes(), input.sets());
    }

    @DeleteMapping("/workouts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void delete(@PathVariable String id) {
        if (jdbc.update("DELETE FROM workouts WHERE id=? AND owner_id=?", id,SecurityConfig.memberId()) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到訓練紀錄");
    }
}

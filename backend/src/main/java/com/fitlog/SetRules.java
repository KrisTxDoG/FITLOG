package com.fitlog;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
final class SetRules {
 static void validate(List<WorkoutController.SetInput> sets){
  var groups=new HashMap<String,List<Integer>>();
  for(int i=0;i<sets.size();i++){
   var s=sets.get(i);
   if(s.rpe()!=null && s.rpe().remainder(new BigDecimal("0.5")).signum()!=0)fail("RPE 請使用 1–10，間隔 0.5");
   if(s.supersetId()!=null)groups.computeIfAbsent(s.supersetId(),k->new ArrayList<>()).add(i);
  }
  for(var positions:groups.values()){
   if(positions.size()!=2 || positions.get(1)!=positions.get(0)+1)fail("超級組每輪必須為相鄰的兩組");
   var a=sets.get(positions.get(0));var b=sets.get(positions.get(1));
   if(a.exerciseId().equals(b.exerciseId()))fail("超級組需選擇兩個不同動作");
  }
 }
 private static void fail(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}

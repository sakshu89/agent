package com.ultron.core.agent;
import java.util.*;import java.util.concurrent.ConcurrentHashMap;
/** Thread-safe shared skill catalogue. */
public final class SkillRegistry{private final Map<String,Skill> skills=new ConcurrentHashMap<>();public void register(Skill s){Objects.requireNonNull(s);if(skills.putIfAbsent(s.id(),s)!=null)throw new IllegalStateException("Skill already registered: "+s.id());}public Optional<Skill> find(String id){return Optional.ofNullable(skills.get(id));}public List<Skill> all(){return skills.values().stream().sorted(Comparator.comparing(Skill::id)).toList();}}

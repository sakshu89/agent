package com.ultron.core.agent;
/** Local/cloud model adapters implement this provider-neutral planning contract. */
@FunctionalInterface public interface PlanProvider { AgentPlan createPlan(String goal,String observation,String availableTools)throws Exception; }

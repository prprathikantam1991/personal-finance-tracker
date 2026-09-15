package com.pradeep.finance.assistant;

/** A concise, user-safe record of one validated read-only tool step. */
public record AgentStep(int number, String tool, String outcome) { }

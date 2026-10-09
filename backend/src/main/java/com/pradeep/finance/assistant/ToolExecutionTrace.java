package com.pradeep.finance.assistant;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Request-local tool names used to retain the Assistant's user-visible evidence
 * while Spring AI owns the provider-specific tool-calling loop.
 */
@Component
public class ToolExecutionTrace {
    private final ThreadLocal<TraceSession> active = new ThreadLocal<>();

    public TraceSession open() {
        TraceSession session = new TraceSession();
        active.set(session);
        return session;
    }

    public void record(String toolName) {
        TraceSession session = active.get();
        if (session != null) session.record(toolName);
    }

    public final class TraceSession implements AutoCloseable {
        private final List<String> tools = new ArrayList<>();

        private void record(String toolName) {
            if (!tools.contains(toolName)) tools.add(toolName);
        }

        public List<String> toolsUsed() {
            return List.copyOf(tools);
        }

        @Override
        public void close() {
            active.remove();
        }
    }
}

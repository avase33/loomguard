package com.loomguard.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomguard.metrics.Metrics;
import com.loomguard.model.FraudScore;
import com.loomguard.window.CardWindows;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes fraud alerts and per-second throughput ticks to connected dashboards.
 *
 * <p>Sends are best-effort: a browser that cannot keep up is skipped rather
 * than allowed to slow the scoring pipeline.
 */
@Component
public class AlertSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper;
    private final Metrics metrics;
    private final CardWindows windows;

    public AlertSocketHandler(ObjectMapper mapper, Metrics metrics, CardWindows windows) {
        this.mapper = mapper;
        this.metrics = metrics;
        this.windows = windows;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcastAlert(FraudScore score) {
        send(Map.of("type", "alert", "score", score));
    }

    @Scheduled(fixedRate = 1000)
    public void broadcastTick() {
        if (sessions.isEmpty()) {
            return;
        }
        Metrics.Snapshot s = metrics.snapshot();
        send(Map.of(
                "type", "tick",
                "ts", System.currentTimeMillis(),
                "ingestPerSec", s.perSecond(),
                "p50LatencyMs", s.p50(),
                "p99LatencyMs", s.p99(),
                "processed", s.processed(),
                "alerts", s.alerts(),
                "activeCards", windows.activeCards()));
    }

    private void send(Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (IOException e) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException | IllegalStateException e) {
                sessions.remove(session);
            }
        }
    }

    public int clientCount() {
        return sessions.size();
    }
}

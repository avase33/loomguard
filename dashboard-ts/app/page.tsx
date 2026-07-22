"use client";

import { useEffect, useRef, useState } from "react";

const WS = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080";

type Score = {
  txnId: string;
  cardId: string;
  ts: number;
  probability: number;
  decision: string;
  reasons: string[];
  latencyMs: number;
};

type Tick = {
  type: "tick";
  ts: number;
  ingestPerSec: number;
  p50LatencyMs: number;
  p99LatencyMs: number;
  processed: number;
  alerts: number;
  activeCards: number;
};

type AlertMsg = { type: "alert"; score: Score };

const MAX_POINTS = 120;
const MAX_ALERTS = 40;

function decisionColor(d: string): string {
  if (d === "BLOCK") return "#ff5c6c";
  if (d === "REVIEW") return "#ffb454";
  return "#4ec9b0";
}

export default function Dashboard() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [connected, setConnected] = useState(false);
  const [tick, setTick] = useState<Tick | null>(null);
  const [alerts, setAlerts] = useState<Score[]>([]);
  const [history, setHistory] = useState<number[]>([]);

  useEffect(() => {
    const ws = new WebSocket(`${WS}/ws/alerts`);
    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onerror = () => setConnected(false);
    ws.onmessage = (e: MessageEvent) => {
      try {
        const msg = JSON.parse(e.data) as Tick | AlertMsg;
        if (msg.type === "tick") {
          setTick(msg);
          setHistory((h) => {
            const next = [...h, msg.p99LatencyMs];
            return next.length > MAX_POINTS ? next.slice(-MAX_POINTS) : next;
          });
        } else if (msg.type === "alert") {
          setAlerts((a) => [msg.score, ...a].slice(0, MAX_ALERTS));
        }
      } catch {
        /* ignore malformed frames */
      }
    };
    return () => ws.close();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const W = canvas.width;
    const H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    const maxV = Math.max(12, ...history);
    const yFor = (v: number) => H - (v / maxV) * (H - 8) - 4;

    ctx.strokeStyle = "#30363d";
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(0, yFor(10));
    ctx.lineTo(W, yFor(10));
    ctx.stroke();
    ctx.setLineDash([]);

    if (history.length > 1) {
      ctx.strokeStyle = "#58a6ff";
      ctx.lineWidth = 2;
      ctx.beginPath();
      history.forEach((v, i) => {
        const x = (i / (MAX_POINTS - 1)) * W;
        const y = yFor(v);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
    }
  }, [history]);

  return (
    <main style={{ padding: 24, maxWidth: 1100, margin: "0 auto" }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h1 style={{ fontSize: 22, margin: 0 }}>loomguard · fraud operations</h1>
        <span style={{ color: connected ? "#4ec9b0" : "#ff5c6c" }}>
          {connected ? "● live" : "○ offline"}
        </span>
      </header>

      <section
        style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 12, marginTop: 20 }}
      >
        <Metric label="ingest / sec" value={(tick?.ingestPerSec ?? 0).toLocaleString()} />
        <Metric label="p50 latency" value={`${(tick?.p50LatencyMs ?? 0).toFixed(1)} ms`} />
        <Metric
          label="p99 latency"
          value={`${(tick?.p99LatencyMs ?? 0).toFixed(1)} ms`}
          warn={(tick?.p99LatencyMs ?? 0) > 10}
        />
        <Metric label="active cards" value={(tick?.activeCards ?? 0).toLocaleString()} />
        <Metric label="alerts" value={(tick?.alerts ?? 0).toLocaleString()} />
      </section>

      <section style={{ marginTop: 24 }}>
        <h2 style={{ fontSize: 14, color: "#8b949e" }}>
          p99 scoring latency (dashed line = 10 ms budget)
        </h2>
        <canvas
          ref={canvasRef}
          width={1050}
          height={140}
          style={{
            width: "100%",
            background: "#0d1117",
            border: "1px solid #30363d",
            borderRadius: 8,
          }}
        />
      </section>

      <section style={{ marginTop: 24 }}>
        <h2 style={{ fontSize: 14, color: "#8b949e" }}>fraud alerts ({alerts.length})</h2>
        <div style={{ border: "1px solid #30363d", borderRadius: 8 }}>
          {alerts.length === 0 && (
            <p style={{ padding: 16, color: "#8b949e" }}>
              no alerts yet — waiting for REVIEW/BLOCK decisions…
            </p>
          )}
          {alerts.map((a, i) => (
            <div
              key={`${a.txnId}-${i}`}
              style={{
                display: "grid",
                gridTemplateColumns: "80px 90px 60px 1fr 70px",
                gap: 8,
                padding: "8px 12px",
                borderTop: i ? "1px solid #21262d" : "none",
                alignItems: "center",
              }}
            >
              <span style={{ color: decisionColor(a.decision), fontWeight: 700 }}>{a.decision}</span>
              <span>{a.cardId}</span>
              <span>{(a.probability * 100).toFixed(0)}%</span>
              <span style={{ color: "#8b949e", fontSize: 13 }}>{a.reasons.join("  ·  ")}</span>
              <span style={{ color: "#8b949e", textAlign: "right" }}>{a.latencyMs.toFixed(1)}ms</span>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}

function Metric({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
  return (
    <div
      style={{
        background: "#0d1117",
        border: `1px solid ${warn ? "#ff5c6c" : "#30363d"}`,
        borderRadius: 8,
        padding: 16,
      }}
    >
      <div style={{ color: "#8b949e", fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 24, marginTop: 6, color: warn ? "#ff5c6c" : "#e6edf3" }}>{value}</div>
    </div>
  );
}

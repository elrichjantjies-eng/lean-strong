import { useEffect, useRef } from "react";
import type { SkyKind } from "@/weather/codes";

export type SkyPhase = "day" | "night" | "golden";

export type SkyTheme = { kind: SkyKind; phase: SkyPhase; intensity: number };

const PALETTE: Record<string, [string, string]> = {
  "clear-day": ["#1f63cf", "#7bb7f2"],
  "clear-golden": ["#36458a", "#f0a06c"],
  "clear-night": ["#060d24", "#1b2a55"],
  "partly-day": ["#3471c4", "#93b9e2"],
  "partly-golden": ["#414c80", "#d99a7a"],
  "partly-night": ["#0d1633", "#2a3759"],
  "cloudy-day": ["#55687f", "#9cadc0"],
  "cloudy-golden": ["#4f5872", "#a58f8a"],
  "cloudy-night": ["#161d2c", "#363f52"],
  "rain-day": ["#3b4d63", "#6c7f96"],
  "rain-golden": ["#3b4560", "#76788c"],
  "rain-night": ["#0f1624", "#283343"],
  "storm-day": ["#232a39", "#4a5468"],
  "storm-golden": ["#232a39", "#4a5468"],
  "storm-night": ["#0c1019", "#262d3c"],
  "snow-day": ["#7f95b2", "#c6d2e1"],
  "snow-golden": ["#6f7c9c", "#c2b8c4"],
  "snow-night": ["#252f44", "#4a576e"],
  "fog-day": ["#7f8b9b", "#b7c0cb"],
  "fog-golden": ["#7d7f8f", "#b5a9a5"],
  "fog-night": ["#272d37", "#47505c"],
};

export function skyColors(t: SkyTheme): [string, string] {
  return PALETTE[`${t.kind}-${t.phase}`] ?? PALETTE["clear-day"];
}

/** The page-filling sky gradient, plus animated weather on a canvas. */
export function Sky({ theme, animate }: { theme: SkyTheme; animate: boolean }) {
  const [top, bottom] = skyColors(theme);
  const canvas = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    // Keep the browser chrome / overscroll in step with the sky.
    document.documentElement.style.setProperty("--sky-top", top);
    document.documentElement.style.setProperty("--sky-bottom", bottom);
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", top);
  }, [top, bottom]);

  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    const reduce = !animate || window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    return runScene(el, theme, reduce);
  }, [theme, animate]);

  return (
    <div aria-hidden className="fixed inset-0 -z-10 overflow-hidden">
      <div
        key={`${top}${bottom}`}
        className="absolute inset-0"
        style={{
          background: `linear-gradient(180deg, ${top} 0%, ${bottom} 100%)`,
          animation: "backdrop-in 0.8s ease both",
        }}
      />
      <canvas ref={canvas} className="absolute inset-0 h-full w-full" />
    </div>
  );
}

// ---- The animated scene --------------------------------------------------------------------------

type Drop = { x: number; y: number; len: number; speed: number; alpha: number };
type Flake = { x: number; y: number; r: number; speed: number; phase: number };
type Star = { x: number; y: number; r: number; phase: number; speed: number };
type Cloud = { x: number; y: number; r: number; speed: number; alpha: number };

function runScene(canvas: HTMLCanvasElement, theme: SkyTheme, still: boolean): () => void {
  const ctx = canvas.getContext("2d");
  if (!ctx) return () => {};
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  let w = 0;
  let h = 0;
  const resize = () => {
    w = canvas.clientWidth;
    h = canvas.clientHeight;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  };
  resize();
  window.addEventListener("resize", resize);

  const rand = (a: number, b: number) => a + Math.random() * (b - a);
  const { kind, phase, intensity } = theme;
  const night = phase === "night";

  const raining = kind === "rain" || kind === "storm";
  const drops: Drop[] = raining
    ? Array.from({ length: 40 + intensity * 70 }, () => ({
        x: rand(0, w),
        y: rand(-h, h),
        len: rand(10, 22),
        speed: rand(700, 1100),
        alpha: rand(0.15, 0.4),
      }))
    : [];
  const flakes: Flake[] =
    kind === "snow"
      ? Array.from({ length: 40 + intensity * 40 }, () => ({
          x: rand(0, w),
          y: rand(-h, h),
          r: rand(1, 3.2),
          speed: rand(30, 80),
          phase: rand(0, Math.PI * 2),
        }))
      : [];
  const stars: Star[] =
    night && (kind === "clear" || kind === "partly")
      ? Array.from({ length: kind === "clear" ? 110 : 50 }, () => ({
          x: rand(0, w),
          y: rand(0, h * 0.65),
          r: rand(0.4, 1.4),
          phase: rand(0, Math.PI * 2),
          speed: rand(0.6, 2),
        }))
      : [];
  const cloudCount = { clear: 0, partly: 4, cloudy: 7, fog: 0, rain: 6, storm: 6, snow: 5 }[kind];
  const clouds: Cloud[] = Array.from({ length: cloudCount }, (_, i) => ({
    x: rand(-0.2, 1.1) * w,
    y: rand(0.02, kind === "partly" ? 0.32 : 0.45) * h,
    r: rand(90, 190),
    speed: rand(4, 12) * (i % 2 ? 1 : 0.7),
    alpha: kind === "partly" ? rand(0.1, 0.2) : rand(0.08, 0.16),
  }));
  let flash = 0;
  let nextFlash = performance.now() + rand(2500, 7000);

  let last = performance.now();
  let raf = 0;

  const draw = (t: number) => {
    const dt = Math.min(0.05, (t - last) / 1000);
    last = t;
    ctx.clearRect(0, 0, w, h);

    // Sun glow (day) or moon glow (clear night).
    if ((kind === "clear" || kind === "partly") && !night) {
      const pulse = 1 + Math.sin(t / 2400) * 0.04;
      const g = ctx.createRadialGradient(
        w * 0.82,
        h * 0.06,
        0,
        w * 0.82,
        h * 0.06,
        w * 0.75 * pulse,
      );
      g.addColorStop(0, phase === "golden" ? "rgba(255,190,120,0.45)" : "rgba(255,244,200,0.42)");
      g.addColorStop(
        0.35,
        phase === "golden" ? "rgba(255,160,100,0.14)" : "rgba(255,240,200,0.12)",
      );
      g.addColorStop(1, "rgba(255,255,255,0)");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
    }

    for (const s of stars) {
      const a = 0.35 + 0.65 * Math.abs(Math.sin(s.phase + (t / 1000) * s.speed));
      ctx.globalAlpha = a * 0.9;
      ctx.fillStyle = "#fff";
      ctx.beginPath();
      ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;

    for (const c of clouds) {
      c.x += c.speed * dt;
      if (c.x - c.r > w) c.x = -c.r * 1.5;
      const g = ctx.createRadialGradient(c.x, c.y, 0, c.x, c.y, c.r);
      const tone = night ? "200,210,230" : "255,255,255";
      g.addColorStop(0, `rgba(${tone},${c.alpha})`);
      g.addColorStop(1, `rgba(${tone},0)`);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.ellipse(c.x, c.y, c.r * 1.6, c.r, 0, 0, Math.PI * 2);
      ctx.fill();
    }

    if (kind === "fog") {
      for (let i = 0; i < 5; i++) {
        const y = h * (0.15 + i * 0.17);
        const x = ((t / 60) * (i % 2 ? 1 : -1) + i * 200) % (w * 2);
        const g = ctx.createLinearGradient(0, y - 60, 0, y + 60);
        g.addColorStop(0, "rgba(255,255,255,0)");
        g.addColorStop(0.5, "rgba(255,255,255,0.09)");
        g.addColorStop(1, "rgba(255,255,255,0)");
        ctx.fillStyle = g;
        ctx.fillRect(x - w, y - 60, w * 3, 120);
      }
    }

    if (drops.length) {
      ctx.lineCap = "round";
      ctx.lineWidth = 1.2;
      for (const d of drops) {
        d.y += d.speed * dt;
        d.x -= d.speed * dt * 0.12;
        if (d.y > h) {
          d.y = rand(-60, -10);
          d.x = rand(0, w * 1.15);
        }
        ctx.strokeStyle = `rgba(200,225,255,${d.alpha})`;
        ctx.beginPath();
        ctx.moveTo(d.x, d.y);
        ctx.lineTo(d.x - d.len * 0.12, d.y + d.len);
        ctx.stroke();
      }
    }

    for (const f of flakes) {
      f.y += f.speed * dt;
      f.x += Math.sin(f.phase + t / 900) * 0.4;
      if (f.y > h) {
        f.y = -5;
        f.x = rand(0, w);
      }
      ctx.fillStyle = "rgba(255,255,255,0.85)";
      ctx.beginPath();
      ctx.arc(f.x, f.y, f.r, 0, Math.PI * 2);
      ctx.fill();
    }

    if (kind === "storm") {
      if (t > nextFlash) {
        flash = 1;
        nextFlash = t + rand(3500, 9000);
      }
      if (flash > 0) {
        ctx.fillStyle = `rgba(220,230,255,${flash * 0.35})`;
        ctx.fillRect(0, 0, w, h);
        flash = Math.max(0, flash - dt * 3.2);
      }
    }

    if (!still) raf = requestAnimationFrame(draw);
  };

  const onVisibility = () => {
    cancelAnimationFrame(raf);
    if (!document.hidden && !still) {
      last = performance.now();
      raf = requestAnimationFrame(draw);
    }
  };
  document.addEventListener("visibilitychange", onVisibility);
  raf = requestAnimationFrame(draw);

  return () => {
    cancelAnimationFrame(raf);
    window.removeEventListener("resize", resize);
    document.removeEventListener("visibilitychange", onVisibility);
  };
}

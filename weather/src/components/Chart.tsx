import { useId, useRef, useState } from "react";

export type ChartPoint = { t: number; v: number | null };

/**
 * A smooth line/area chart over time with an optional bar series, a "now" marker and
 * touch-to-scrub. Times are Unix seconds; `offset` turns them into the place's local hours.
 */
export function Chart({
  points,
  bars,
  barMax,
  offset,
  now,
  color = "#ffffff",
  gradient,
  format,
  height = 180,
  yMin,
  yMax,
  bands,
  onScrub,
}: {
  points: ChartPoint[];
  bars?: ChartPoint[];
  barMax?: number;
  offset: number;
  now?: number;
  color?: string;
  /** CSS colours from bottom to top of the value range, for the line. */
  gradient?: string[];
  format: (v: number) => string;
  height?: number;
  yMin?: number;
  yMax?: number;
  /** Background bands such as UV categories: [from, to, colour]. */
  bands?: [number, number, string][];
  onScrub?: (p: ChartPoint | null) => void;
}) {
  const width = 340;
  const padL = 4;
  const padR = 34;
  const padT = 14;
  const padB = 22;
  const ref = useRef<SVGSVGElement>(null);
  const [scrub, setScrub] = useState<number | null>(null);
  const id = useId().replace(/[^a-zA-Z0-9]/g, "");

  const valid = points.filter((p) => p.v != null) as { t: number; v: number }[];
  const t0 = points[0]?.t ?? 0;
  const t1 = points[points.length - 1]?.t ?? 1;
  const vals = valid.map((p) => p.v);
  let lo = yMin ?? Math.min(...vals);
  let hi = yMax ?? Math.max(...vals);
  if (yMin == null || yMax == null) {
    const pad = Math.max(1, (hi - lo) * 0.15);
    if (yMin == null) lo = Math.floor(lo - pad);
    if (yMax == null) hi = Math.ceil(hi + pad);
  }
  if (hi - lo < 1e-6) hi = lo + 1;
  const x = (t: number) => padL + ((t - t0) / Math.max(1, t1 - t0)) * (width - padL - padR);
  const y = (v: number) => padT + (1 - (v - lo) / (hi - lo)) * (height - padT - padB);

  const path = smooth(valid.map((p) => [x(p.t), y(p.v)]));
  const area = valid.length
    ? `${path} L${x(valid[valid.length - 1].t)},${height - padB} L${x(valid[0].t)},${height - padB} Z`
    : "";

  const ticks: number[] = [];
  for (let t = t0; t <= t1; t += 3600) {
    const h = new Date((t + offset) * 1000).getUTCHours();
    if (h % 6 === 0) ticks.push(t);
  }
  const yTicks = [lo, (lo + hi) / 2, hi];

  const pick = (clientX: number) => {
    const svg = ref.current;
    if (!svg || valid.length === 0) return;
    const r = svg.getBoundingClientRect();
    const sx = ((clientX - r.left) / r.width) * width;
    const t = t0 + ((sx - padL) / (width - padL - padR)) * (t1 - t0);
    let best = valid[0];
    for (const p of valid) if (Math.abs(p.t - t) < Math.abs(best.t - t)) best = p;
    setScrub(best.t);
    onScrub?.(best);
  };
  const end = () => {
    setScrub(null);
    onScrub?.(null);
  };
  const scrubPoint = scrub != null ? valid.find((p) => p.t === scrub) : undefined;
  const bmax = barMax ?? Math.max(1, ...(bars ?? []).map((b) => b.v ?? 0));

  return (
    <svg
      ref={ref}
      viewBox={`0 0 ${width} ${height}`}
      className="w-full touch-none select-none"
      role="img"
      aria-label="Chart"
      onPointerDown={(e) => {
        (e.target as Element).setPointerCapture?.(e.pointerId);
        pick(e.clientX);
      }}
      onPointerMove={(e) => e.buttons && pick(e.clientX)}
      onPointerUp={end}
      onPointerCancel={end}
    >
      <defs>
        <linearGradient id={`a${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.35" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
        {gradient && (
          <linearGradient
            id={`l${id}`}
            gradientUnits="userSpaceOnUse"
            x1="0"
            y1={y(lo)}
            x2="0"
            y2={y(hi)}
          >
            {gradient.map((c, i) => (
              <stop key={i} offset={i / Math.max(1, gradient.length - 1)} stopColor={c} />
            ))}
          </linearGradient>
        )}
      </defs>
      {bands?.map(([a, b, c], i) => (
        <rect
          key={i}
          x={padL}
          width={width - padL - padR}
          y={y(Math.min(b, hi))}
          height={Math.max(0, y(Math.max(a, lo)) - y(Math.min(b, hi)))}
          fill={c}
          opacity={0.12}
        />
      ))}
      {yTicks.map((v, i) => (
        <g key={i}>
          <line
            x1={padL}
            x2={width - padR}
            y1={y(v)}
            y2={y(v)}
            stroke="white"
            strokeOpacity="0.12"
            strokeDasharray="2 4"
          />
          <text x={width - padR + 6} y={y(v) + 4} fontSize="10" fill="white" fillOpacity="0.6">
            {format(v)}
          </text>
        </g>
      ))}
      {ticks.map((t) => (
        <g key={t}>
          <line
            x1={x(t)}
            x2={x(t)}
            y1={padT}
            y2={height - padB}
            stroke="white"
            strokeOpacity="0.08"
          />
          <text
            x={x(t)}
            y={height - 6}
            fontSize="10"
            fill="white"
            fillOpacity="0.6"
            textAnchor="middle"
          >
            {String(new Date((t + offset) * 1000).getUTCHours()).padStart(2, "0")}
          </text>
        </g>
      ))}
      {bars?.map((b) =>
        b.v ? (
          <rect
            key={b.t}
            x={x(b.t) - 3}
            width={6}
            y={height - padB - (b.v / bmax) * (height - padT - padB) * 0.5}
            height={(b.v / bmax) * (height - padT - padB) * 0.5}
            rx={2}
            fill="#5ac8fa"
            opacity={0.75}
          />
        ) : null,
      )}
      {area && <path d={area} fill={`url(#a${id})`} />}
      {path && (
        <path
          d={path}
          fill="none"
          stroke={gradient ? `url(#l${id})` : color}
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      )}
      {now != null && now >= t0 && now <= t1 && (
        <g>
          <line
            x1={x(now)}
            x2={x(now)}
            y1={padT}
            y2={height - padB}
            stroke="white"
            strokeOpacity="0.5"
            strokeDasharray="3 3"
          />
        </g>
      )}
      {scrubPoint && (
        <g>
          <line
            x1={x(scrubPoint.t)}
            x2={x(scrubPoint.t)}
            y1={padT - 4}
            y2={height - padB}
            stroke="white"
            strokeOpacity="0.8"
          />
          <circle
            cx={x(scrubPoint.t)}
            cy={y(scrubPoint.v)}
            r={5}
            fill="white"
            stroke="rgba(0,0,0,.3)"
          />
        </g>
      )}
    </svg>
  );
}

/** Catmull-Rom → cubic Bézier for a gentle curve through every point. */
function smooth(pts: [number, number][]): string {
  if (pts.length === 0) return "";
  if (pts.length < 3) return `M${pts.map((p) => p.join(",")).join(" L")}`;
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] ?? p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += ` C${c1[0].toFixed(1)},${c1[1].toFixed(1)} ${c2[0].toFixed(1)},${c2[1].toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;
  }
  return d;
}

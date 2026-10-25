/**
 * Small hand-written SVG charts. Line charts support several series, a shaded band, dashed
 * reference lines and point markers (for the weight chart's on-track band, goal and checkpoint).
 */
import { useId } from "react";

export interface Point {
  x: number;
  y: number | null;
}
export interface Series {
  id: string;
  label: string;
  color: string;
  points: Point[];
  dashed?: boolean;
  dots?: boolean;
  width?: number;
}
export interface RefLine {
  y: number;
  label: string;
  color: string;
}
export interface Marker {
  x: number;
  y: number;
  label: string;
  color: string;
}

const W = 340;
const PAD = { top: 12, right: 12, bottom: 26, left: 40 };

function niceTicks(min: number, max: number, count = 4): number[] {
  if (min === max) return [min];
  const raw = (max - min) / count;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? raw;
  const start = Math.ceil(min / step) * step;
  const ticks: number[] = [];
  for (let v = start; v <= max + 1e-9; v += step) ticks.push(Number(v.toFixed(6)));
  return ticks;
}

export function LineChart({
  series,
  band,
  refLines = [],
  markers = [],
  xTicks,
  xLabel = (x) => String(x),
  yFormat = (y) => String(Math.round(y * 10) / 10),
  height = 200,
  title,
  yPadding = 0.08,
}: {
  series: Series[];
  band?: { x: number; lo: number; hi: number }[];
  refLines?: RefLine[];
  markers?: Marker[];
  xTicks?: number[];
  xLabel?: (x: number) => string;
  yFormat?: (y: number) => string;
  height?: number;
  title: string;
  yPadding?: number;
}) {
  const clipId = useId();
  const xs = [
    ...series.flatMap((s) => s.points.map((p) => p.x)),
    ...(band ?? []).map((b) => b.x),
    ...markers.map((m) => m.x),
  ];
  const ys = [
    ...series.flatMap((s) => s.points.map((p) => p.y).filter((y): y is number => y !== null)),
    ...(band ?? []).flatMap((b) => [b.lo, b.hi]),
    ...refLines.map((r) => r.y),
    ...markers.map((m) => m.y),
  ];
  if (xs.length === 0 || ys.length === 0) return null;
  const [x0, x1] = [Math.min(...xs), Math.max(...xs)];
  let [y0, y1] = [Math.min(...ys), Math.max(...ys)];
  const span = y1 - y0 || Math.abs(y1) * 0.1 || 1;
  y0 -= span * yPadding;
  y1 += span * yPadding;

  const iw = W - PAD.left - PAD.right;
  const ih = height - PAD.top - PAD.bottom;
  const sx = (x: number) => PAD.left + (x1 === x0 ? iw / 2 : ((x - x0) / (x1 - x0)) * iw);
  const sy = (y: number) => PAD.top + ih - ((y - y0) / (y1 - y0)) * ih;

  const path = (pts: Point[]) => {
    let d = "";
    let pen = false;
    for (const p of pts) {
      if (p.y === null) {
        pen = false;
        continue;
      }
      d += `${pen ? "L" : "M"}${sx(p.x).toFixed(1)},${sy(p.y).toFixed(1)}`;
      pen = true;
    }
    return d;
  };
  const yTicks = niceTicks(y0, y1);
  const ticksX = xTicks ?? [...new Set(xs)].sort((a, b) => a - b);

  return (
    <figure>
      <svg viewBox={`0 0 ${W} ${height}`} className="w-full" role="img" aria-label={title}>
        <title>{title}</title>
        <defs>
          <clipPath id={clipId}>
            <rect x={PAD.left} y={PAD.top} width={iw} height={ih} />
          </clipPath>
        </defs>
        {yTicks.map((t) => (
          <g key={t}>
            <line
              x1={PAD.left}
              x2={W - PAD.right}
              y1={sy(t)}
              y2={sy(t)}
              stroke="var(--app-line)"
              strokeWidth="1"
            />
            <text
              x={PAD.left - 6}
              y={sy(t) + 4}
              textAnchor="end"
              fontSize="10"
              fill="var(--app-muted)"
            >
              {yFormat(t)}
            </text>
          </g>
        ))}
        {ticksX.map((t) => (
          <text
            key={t}
            x={sx(t)}
            y={height - 8}
            textAnchor="middle"
            fontSize="10"
            fill="var(--app-muted)"
          >
            {xLabel(t)}
          </text>
        ))}
        <g clipPath={`url(#${clipId})`}>
          {band && band.length > 1 && (
            <path
              d={`${band.map((b, i) => `${i ? "L" : "M"}${sx(b.x)},${sy(b.hi)}`).join("")}${[
                ...band,
              ]
                .reverse()
                .map((b) => `L${sx(b.x)},${sy(b.lo)}`)
                .join("")}Z`}
              fill="var(--app-accent)"
              opacity="0.14"
            />
          )}
          {refLines.map((r) => (
            <g key={r.label}>
              <line
                x1={PAD.left}
                x2={W - PAD.right}
                y1={sy(r.y)}
                y2={sy(r.y)}
                stroke={r.color}
                strokeWidth="1.5"
                strokeDasharray="5 4"
              />
              <text
                x={W - PAD.right - 2}
                y={sy(r.y) - 4}
                textAnchor="end"
                fontSize="10"
                fontWeight="600"
                fill={r.color}
              >
                {r.label}
              </text>
            </g>
          ))}
          {series.map((s) => (
            <g key={s.id}>
              <path
                d={path(s.points)}
                fill="none"
                stroke={s.color}
                strokeWidth={s.width ?? 2.5}
                strokeLinejoin="round"
                strokeLinecap="round"
                strokeDasharray={s.dashed ? "4 4" : undefined}
              />
              {s.dots &&
                s.points.map((p) =>
                  p.y === null ? null : (
                    <circle
                      key={p.x}
                      cx={sx(p.x)}
                      cy={sy(p.y)}
                      r="3.5"
                      fill={s.color}
                      stroke="var(--app-surface)"
                      strokeWidth="1.5"
                    />
                  ),
                )}
            </g>
          ))}
          {markers.map((m) => (
            <g key={m.label}>
              <path
                d={`M${sx(m.x)},${sy(m.y) - 7} L${sx(m.x) + 7},${sy(m.y)} L${sx(m.x)},${sy(m.y) + 7} L${sx(m.x) - 7},${sy(m.y)} Z`}
                fill={m.color}
                stroke="var(--app-surface)"
                strokeWidth="1.5"
              />
            </g>
          ))}
        </g>
      </svg>
      <figcaption className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
        {series.map((s) => (
          <span key={s.id} className="inline-flex items-center gap-1.5">
            <span
              className="inline-block h-0.5 w-4"
              style={{
                background: s.color,
                borderTop: s.dashed ? `2px dashed ${s.color}` : undefined,
              }}
            />
            {s.label}
          </span>
        ))}
        {band && (
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-4 rounded-sm bg-accent opacity-25" />
            On track
          </span>
        )}
        {markers.map((m) => (
          <span key={m.label} className="inline-flex items-center gap-1.5">
            <span className="inline-block size-2.5 rotate-45" style={{ background: m.color }} />
            {m.label}
          </span>
        ))}
      </figcaption>
    </figure>
  );
}

export function BarChart({
  bars,
  target,
  height = 180,
  title,
  yFormat = (v) => v.toLocaleString(),
}: {
  bars: { label: string; value: number | null; color?: string }[];
  target?: { value: number; label: string };
  height?: number;
  title: string;
  yFormat?: (v: number) => string;
}) {
  const values = bars.map((b) => b.value ?? 0);
  const max = Math.max(...values, target?.value ?? 0, 1) * 1.1;
  const iw = W - PAD.left - PAD.right;
  const ih = height - PAD.top - PAD.bottom;
  const bw = iw / bars.length;
  const sy = (v: number) => PAD.top + ih - (v / max) * ih;
  return (
    <svg viewBox={`0 0 ${W} ${height}`} className="w-full" role="img" aria-label={title}>
      <title>{title}</title>
      {niceTicks(0, max).map((t) => (
        <g key={t}>
          <line x1={PAD.left} x2={W - PAD.right} y1={sy(t)} y2={sy(t)} stroke="var(--app-line)" />
          <text
            x={PAD.left - 6}
            y={sy(t) + 4}
            textAnchor="end"
            fontSize="10"
            fill="var(--app-muted)"
          >
            {yFormat(t)}
          </text>
        </g>
      ))}
      {bars.map((b, i) => (
        <g key={i}>
          {b.value !== null && b.value > 0 && (
            <rect
              x={PAD.left + i * bw + bw * 0.18}
              y={sy(b.value)}
              width={bw * 0.64}
              height={PAD.top + ih - sy(b.value)}
              rx="3"
              fill={b.color ?? "var(--app-accent)"}
            />
          )}
          <text
            x={PAD.left + i * bw + bw / 2}
            y={height - 8}
            textAnchor="middle"
            fontSize="10"
            fill="var(--app-muted)"
          >
            {b.label}
          </text>
        </g>
      ))}
      {target && (
        <g>
          <line
            x1={PAD.left}
            x2={W - PAD.right}
            y1={sy(target.value)}
            y2={sy(target.value)}
            stroke="var(--app-ink)"
            strokeWidth="1.5"
            strokeDasharray="5 4"
          />
          <text
            x={W - PAD.right - 2}
            y={sy(target.value) - 4}
            textAnchor="end"
            fontSize="10"
            fontWeight="600"
            fill="var(--app-ink)"
          >
            {target.label}
          </text>
        </g>
      )}
    </svg>
  );
}

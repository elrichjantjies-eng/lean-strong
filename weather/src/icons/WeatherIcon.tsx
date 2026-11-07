import { useId } from "react";
import { ICONS, gradientsUsed, type IconKey, type Shape } from "./defs";

/** Renders one of the shared weather icons (see defs.ts) as inline SVG. */
export function WeatherIcon({
  icon,
  size = 32,
  className,
  title,
}: {
  icon: IconKey;
  size?: number;
  className?: string;
  title?: string;
}) {
  const uid = useId().replace(/[^a-zA-Z0-9]/g, "");
  const def = ICONS[icon] ?? ICONS.cloudy;
  const paint = (p: string) => p.replace(/^url\(#(\w+)\)$/, `url(#${uid}$1)`);
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={className}
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      <defs>
        {gradientsUsed(def).map((g) => (
          <linearGradient
            key={g.id}
            id={uid + g.id}
            gradientUnits="userSpaceOnUse"
            x1={g.x1}
            y1={g.y1}
            x2={g.x2}
            y2={g.y2}
          >
            <stop offset="0" stopColor={g.from} />
            <stop offset="1" stopColor={g.to} />
          </linearGradient>
        ))}
      </defs>
      {def.shapes.map((s, i) => (
        <ShapeEl key={i} s={s} paint={paint} />
      ))}
    </svg>
  );
}

function ShapeEl({ s, paint }: { s: Shape; paint: (p: string) => string }) {
  switch (s.kind) {
    case "circle":
      return <circle cx={s.cx} cy={s.cy} r={s.r} fill={paint(s.fill)} />;
    case "rect":
      return <rect x={s.x} y={s.y} width={s.w} height={s.h} rx={s.r} fill={paint(s.fill)} />;
    case "line":
      return (
        <line
          x1={s.x1}
          y1={s.y1}
          x2={s.x2}
          y2={s.y2}
          stroke={paint(s.stroke)}
          strokeWidth={s.width}
          strokeLinecap="round"
        />
      );
    case "path":
      return <path d={s.d} fill={paint(s.fill)} />;
  }
}

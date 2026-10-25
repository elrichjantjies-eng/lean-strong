import { Link } from "react-router";
import type { Program } from "@/content/types";
import { weekdayName, weekdayOf } from "@/domain/dates";
import type { WeekDay } from "@/domain/schedule";
import { dayColor } from "./labels";

const statusLabel = {
  done: "done",
  missed: "missed",
  today: "today",
  upcoming: "upcoming",
  rest: "rest day",
} as const;

export function WeekStrip({ program, days }: { program: Program; days: WeekDay[] }) {
  return (
    <ol className="grid grid-cols-7 gap-1" aria-label="This week">
      {days.map(({ date, day, status }) => {
        const color = dayColor(program, day);
        const letter = day.kind === "workout" ? day.sessionId : day.kind === "walk" ? "W" : "–";
        return (
          <li key={date}>
            <Link
              to={`/plan?date=${date}`}
              aria-label={`${weekdayName(weekdayOf(date))}: ${day.title}, ${statusLabel[status]}`}
              className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl py-1.5 ${
                status === "today" ? "bg-surface shadow-sm ring-2 ring-[var(--app-accent)]" : ""
              }`}
            >
              <span className="text-xs font-medium text-muted uppercase">
                {weekdayOf(date).slice(0, 2)}
              </span>
              <span
                className="flex size-8 items-center justify-center rounded-full text-sm font-bold"
                style={
                  status === "done"
                    ? { background: color, color: "#fff" }
                    : { boxShadow: `inset 0 0 0 2px ${color}`, color: "var(--app-ink)" }
                }
              >
                {status === "done" ? "✓" : letter}
              </span>
              <span
                className={`size-1.5 rounded-full ${
                  status === "missed"
                    ? "bg-[#d92d20]"
                    : status === "today"
                      ? "bg-accent"
                      : "bg-transparent"
                }`}
                aria-hidden="true"
              />
            </Link>
          </li>
        );
      })}
    </ol>
  );
}

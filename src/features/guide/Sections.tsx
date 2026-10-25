import type { PlaceholderValues } from "@/content/text";
import type { Phase, Program, Section } from "@/content/types";
import { weekdayName } from "@/domain/dates";
import { RichText } from "@/components/RichText";
import { Callout, Card } from "@/components/ui";
import { dayColor, themeColor } from "@/features/today/labels";

const PHASE_ROW_LABELS: Record<keyof Phase["summary"], string> = {
  sets: "Sets",
  effort: "Effort",
  rest: "Rest",
  warmup: "Warm-up",
  waistCircuit: "Waist circuit",
  walkAfter: "Walk after",
};

/** The phase table as one card per phase (readable on a phone); the current phase stands out. */
export function PhaseTable({
  program,
  rows,
  rowLabels,
  currentPhase,
}: {
  program: Program;
  rows?: (keyof Phase["summary"])[];
  rowLabels?: string[];
  currentPhase?: number;
}) {
  const keys =
    rows ??
    (Object.keys(PHASE_ROW_LABELS) as (keyof Phase["summary"])[]).filter((k) =>
      program.phases.some((p) => p.summary[k]),
    );
  return (
    <div className="space-y-3">
      {program.phases.map((phase) => {
        const current = phase.number === currentPhase;
        const color = program.theme.phaseColors[phase.number - 1]!;
        return (
          <div
            key={phase.number}
            className={`overflow-hidden rounded-2xl bg-surface ring-1 ${current ? "ring-2" : "ring-line"}`}
            style={current ? { boxShadow: `0 0 0 2px ${color}` } : undefined}
            aria-current={current ? "true" : undefined}
          >
            <div
              className="flex items-baseline justify-between gap-2 px-4 py-2"
              style={{ background: color }}
            >
              <p className="font-bold text-white">
                Phase {phase.number} · {phase.name}
              </p>
              <p className="text-sm font-semibold text-white">
                Weeks {phase.weeks[0]}–{phase.weeks[1]}
                {current ? " · now" : ""}
              </p>
            </div>
            <dl className="divide-y divide-line px-4">
              {keys.map((k, i) =>
                phase.summary[k] ? (
                  <div key={k} className="grid grid-cols-[7rem_1fr] gap-3 py-2 text-sm">
                    <dt className="font-semibold text-muted">
                      {rowLabels?.[i] ?? PHASE_ROW_LABELS[k]}
                    </dt>
                    <dd className="text-ink">
                      <RichText text={phase.summary[k]!} />
                    </dd>
                  </div>
                ) : null,
              )}
            </dl>
          </div>
        );
      })}
    </div>
  );
}

function Title({ children }: { children: React.ReactNode }) {
  return <h3 className="mb-2 text-lg font-bold text-ink">{children}</h3>;
}

function Note({ text, values }: { text?: string; values: PlaceholderValues }) {
  if (!text) return null;
  return (
    <p className="mt-2 text-sm text-muted">
      <RichText text={text} values={values} />
    </p>
  );
}

export function SectionView({
  section,
  program,
  values,
  currentPhase,
}: {
  section: Section;
  program: Program;
  values: PlaceholderValues;
  currentPhase?: number;
}) {
  const t = (text: string) => <RichText text={text} values={values} />;
  switch (section.type) {
    case "text":
      return (
        <div>
          {section.title && <Title>{section.title}</Title>}
          {section.body.split(/\n\n+/).map((para, i) => (
            <p key={i} className="mb-2 leading-relaxed text-ink last:mb-0">
              {t(para)}
            </p>
          ))}
        </div>
      );

    case "weekStrip":
      return (
        <div>
          {section.title && <Title>{section.title}</Title>}
          <ul className="space-y-1.5">
            {program.weekTemplate.map((d) => (
              <li
                key={d.weekday}
                className="flex items-center gap-3 rounded-xl bg-surface px-3 py-2 ring-1 ring-line"
                style={{ borderLeft: `6px solid ${dayColor(program, d)}` }}
              >
                <span className="w-10 font-bold text-ink">
                  {weekdayName(d.weekday).slice(0, 3)}
                </span>
                <span>
                  <span className="block font-semibold text-ink">{d.title}</span>
                  <span className="block text-sm text-muted">{d.subtitle}</span>
                </span>
              </li>
            ))}
          </ul>
          <Note text={section.note} values={values} />
        </div>
      );

    case "powerSets":
      return (
        <div>
          <ol className="grid grid-cols-2 gap-2">
            {section.boxes.map(([code, title, sub], i) => (
              <li
                key={i}
                className={`rounded-xl p-3 text-center ring-1 ring-line ${
                  /^[A-Z]\d$/.test(code) ? "bg-surface" : "bg-accent-soft"
                }`}
              >
                <p className="text-xl font-bold text-accent-ink">{code}</p>
                <p className="font-semibold text-ink">{title}</p>
                <p className="text-sm text-muted">{sub}</p>
              </li>
            ))}
          </ol>
          <Note text={section.note} values={values} />
        </div>
      );

    case "sessionFlow":
      return (
        <div>
          {section.title && <Title>{section.title}</Title>}
          <ol className="space-y-1.5">
            {section.steps.map(([name, time], i) => {
              const hi = section.highlight.includes(i + 1);
              return (
                <li
                  key={i}
                  className={`flex items-center gap-3 rounded-xl px-3 py-2 ${hi ? "bg-accent-soft" : "bg-surface ring-1 ring-line"}`}
                >
                  <span
                    className={`flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                      hi ? "bg-accent-strong text-on-accent" : "bg-surface-2 text-ink"
                    }`}
                  >
                    {i + 1}
                  </span>
                  <span className={`flex-1 ${hi ? "font-bold" : "font-semibold"} text-ink`}>
                    {name}
                  </span>
                  <span className="text-sm text-muted">{time}</span>
                </li>
              );
            })}
          </ol>
        </div>
      );

    case "callout":
      return (
        <Callout tone={section.tone} title={section.title}>
          {section.items.map((item, i) => (
            <p key={i}>{t(item)}</p>
          ))}
        </Callout>
      );

    case "phaseTable":
      return (
        <div>
          <PhaseTable
            program={program}
            rows={section.rows}
            rowLabels={section.rowLabels}
            currentPhase={currentPhase}
          />
          <Note text={section.note} values={values} />
        </div>
      );

    case "steps":
      return (
        <div>
          {section.title && <Title>{section.title}</Title>}
          <ol className="space-y-2">
            {section.items.map((item, i) => (
              <li key={i} className="flex gap-3">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-accent-soft text-sm font-bold text-accent-ink">
                  {i + 1}
                </span>
                <span className="pt-0.5 text-ink">{t(item)}</span>
              </li>
            ))}
          </ol>
        </div>
      );

    case "cards":
      return (
        <div className="grid gap-3 min-[600px]:grid-cols-2">
          {section.cards.map((card) => (
            <div
              key={card.title}
              className="rounded-2xl bg-surface p-4 ring-1 ring-line"
              style={{ borderTop: `5px solid ${themeColor(program, card.color)}` }}
            >
              <p className="mb-2 font-bold text-ink">{card.title}</p>
              <ul className="space-y-1.5">
                {card.items.map((item, i) => (
                  <li key={i} className="flex gap-2 text-sm text-ink">
                    <span
                      aria-hidden="true"
                      className="mt-2 size-1.5 shrink-0 rounded-full"
                      style={{ background: themeColor(program, card.color) }}
                    />
                    <span>{t(item)}</span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      );

    case "table":
      return (
        <div>
          <div className="space-y-2">
            {section.rows.map((row, i) => (
              <div key={i} className="rounded-xl bg-surface p-3 ring-1 ring-line">
                <p className="mb-1 font-bold text-ink">{row[0]}</p>
                <dl className="space-y-1">
                  {row.slice(1).map((cell, j) => (
                    <div key={j} className="grid grid-cols-[8.5rem_1fr] gap-2 text-sm">
                      <dt className="text-muted">{section.columns[j + 1]}</dt>
                      <dd className="text-ink">{t(cell)}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            ))}
          </div>
          <Note text={section.note} values={values} />
        </div>
      );

    case "sampleDay":
      return (
        <Card>
          <Title>{section.title}</Title>
          <p className="mb-3 text-sm text-muted">{t(section.intro)}</p>
          <dl className="divide-y divide-line">
            {section.rows.map(([meal, food]) => (
              <div key={meal} className="py-2">
                <dt className="text-sm font-semibold text-accent-ink">{meal}</dt>
                <dd className="text-ink">{t(food)}</dd>
              </div>
            ))}
          </dl>
        </Card>
      );

    case "disclaimer":
      return <p className="rounded-xl bg-surface-2 p-3 text-sm text-muted">{t(section.body)}</p>;
  }
}

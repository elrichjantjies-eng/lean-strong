import { Link, useNavigate, useParams } from "react-router";
import { useActive, useToday } from "@/app/active";
import type { Interval, Phase, Program } from "@/content/types";
import { programPosition } from "@/domain/schedule";
import { resolveCircuit } from "@/domain/session";
import { RichText } from "@/components/RichText";
import { Screen } from "@/components/Screen";
import { Button, Callout, Card } from "@/components/ui";

const phaseWeeks = (program: Program) =>
  program.phases.map((p) => `${p.weeks[0]}–${p.weeks[1]}`).join(" / ");

function ropeText(rope: "phase" | Interval, phases: Phase[]): string {
  if (rope !== "phase") return `${rope.activity}: ${rope.rounds} × ${rope.workSec} s`;
  return phases.map((p) => `${p.ropeWarmup.rounds} rounds`).join(" / ");
}

/** A session page like the PDF day pages. */
export function SessionPageScreen() {
  const { sessionId = "" } = useParams();
  const { profile, program } = useActive();
  const today = useToday();
  const navigate = useNavigate();
  const s = program.sessions[sessionId];
  if (!s) {
    return (
      <Screen title="Session" back="/plan">
        <p className="text-muted">That session isn't in your program.</p>
      </Screen>
    );
  }
  const pos = programPosition(program, profile.startDate, today);
  const p = pos.status === "active" ? pos.p : 0;
  const color = program.theme.dayColors[s.id] ?? s.color;
  const exLink = (id: string) => (
    <Link
      to={`/exercises/${id}`}
      className="font-bold underline-offset-2 hover:underline"
      style={{ color }}
    >
      {program.exercises[id]?.name ?? id}
    </Link>
  );

  return (
    <Screen title={s.title} kicker={s.weekdayLabel} back="/plan" hideTitle>
      <div className="space-y-5">
        <div className="-mx-4 px-4 py-4 text-white" style={{ background: color }}>
          <p className="text-xs font-bold tracking-wide uppercase opacity-90">{s.weekdayLabel}</p>
          <div className="flex items-baseline justify-between gap-3">
            <p className="text-3xl font-bold">{s.title}</p>
            <p className="text-right text-lg font-bold">{s.focus}</p>
          </div>
        </div>
        <p className="text-ink">
          <RichText text={s.intro} />
        </p>

        <Callout title="Warm-up" color={color}>
          <p>
            <RichText text={s.warmup.text} />
          </p>
          <p className="text-sm text-muted">Rope: {ropeText(s.warmup.rope, program.phases)}</p>
          {s.warmup.mobility.length > 0 && (
            <ul className="list-disc pl-5 text-sm">
              {s.warmup.mobility.map((m) => (
                <li key={m}>
                  <RichText text={m} />
                </li>
              ))}
            </ul>
          )}
          {s.warmup.warmupSets && (
            <p className="text-sm">
              <RichText text={s.warmup.warmupSets} />
            </p>
          )}
        </Callout>

        <div className="overflow-hidden rounded-2xl ring-1 ring-line">
          <div className="flex items-baseline justify-between gap-2 bg-primary px-3 py-2 text-xs font-bold text-white uppercase">
            <span>Pairs</span>
            <span className="font-semibold normal-case opacity-90">
              Sets for weeks {phaseWeeks(program)}
            </span>
          </div>
          {s.pairs.flatMap((pair, pi) =>
            pair.items.map((item) => {
              const ex = program.exercises[item.exerciseId];
              return (
                <div
                  key={item.code}
                  className={`grid grid-cols-[2.5rem_1fr] gap-x-2 px-3 py-2.5 ${
                    pi % 2 === 0 ? "bg-info-soft" : "bg-surface"
                  }`}
                >
                  <span className="text-lg font-bold" style={{ color }}>
                    {item.code}
                  </span>
                  <span>
                    {exLink(item.exerciseId)}
                    <span className="block text-xs text-muted">{ex?.muscles.join(" · ")}</span>
                    <span className="mt-1 flex flex-wrap gap-x-4 text-sm text-ink">
                      <span>
                        <span className="text-muted">Sets </span>
                        <span className="font-bold tabular-nums">
                          {item.setsByPhase.map((n, i) => (
                            <span
                              key={i}
                              className={
                                i === p && pos.status === "active"
                                  ? "text-accent-ink underline"
                                  : ""
                              }
                            >
                              {i > 0 ? " / " : ""}
                              {n}
                            </span>
                          ))}
                        </span>
                      </span>
                      <span>
                        <span className="text-muted">Reps </span>
                        <span className="font-semibold">{item.display}</span>
                      </span>
                    </span>
                  </span>
                </div>
              );
            }),
          )}
        </div>
        <p className="text-sm text-muted">
          <RichText text={s.restNote} />
        </p>

        {s.finisher && (
          <Callout tone="accent" title={`Finisher · ${s.finisher.name}`}>
            <p>
              <RichText text={s.finisher.description} />
            </p>
            {s.finisher.kind === "circuit" && (
              <ul className="text-sm">
                {s.finisher.circuit.steps.map((st) => (
                  <li key={st.exerciseId}>
                    {exLink(st.exerciseId)} · {st.display}
                  </li>
                ))}
              </ul>
            )}
          </Callout>
        )}

        {s.waistCircuit && (
          <Callout tone="accent" title={s.waistCircuit.title}>
            {s.waistCircuit.description && (
              <p>
                <RichText text={s.waistCircuit.description} />
              </p>
            )}
            <ol className="divide-y divide-line">
              {resolveCircuit(s.waistCircuit, p, 1).steps.map((st, i) => (
                <li key={i} className="flex justify-between gap-3 py-1.5">
                  <span>
                    <span className="mr-2 font-bold text-accent-ink">{i + 1}</span>
                    {exLink(st.exerciseId)}
                  </span>
                  <span className="text-ink">{s.waistCircuit!.steps[i]!.display}</span>
                </li>
              ))}
            </ol>
          </Callout>
        )}

        {s.walkAfter && (
          <Callout title="Walking pad" color={program.theme.dayColors.walk}>
            <p>
              <RichText text={s.walkAfter.text} />
            </p>
          </Callout>
        )}

        {s.notes.length > 0 && (
          <Card>
            <p className="mb-2 text-sm font-bold tracking-wide uppercase" style={{ color }}>
              Coach's notes
            </p>
            <ul className="list-disc space-y-1 pl-5 text-ink">
              {s.notes.map((n, i) => (
                <li key={i}>
                  <RichText text={n} />
                </li>
              ))}
            </ul>
          </Card>
        )}

        <Callout title="Cool-down">
          <p>
            <RichText text={s.cooldown.text} />
          </p>
          {s.cooldown.stretches.length > 0 && (
            <p className="text-sm text-muted">
              {s.cooldown.stretches.join(" · ")} · {s.cooldown.holdSec} s each
            </p>
          )}
        </Callout>

        <Button size="lg" className="w-full" onClick={() => navigate(`/workout/${s.id}`)}>
          Start {s.title}
        </Button>
      </div>
    </Screen>
  );
}

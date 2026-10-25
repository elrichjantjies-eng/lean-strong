import { Link, useParams } from "react-router";
import { useActive, useToday } from "@/app/active";
import { programPosition } from "@/domain/schedule";
import { placeholderValues } from "@/domain/weightPlan";
import { Screen } from "@/components/Screen";
import { SectionView } from "./Sections";

export function GuidePageScreen() {
  const { pageId } = useParams();
  const { profile, program } = useActive();
  const today = useToday();
  const index = program.pages.findIndex((p) => p.id === pageId);
  const page = program.pages[index];
  if (!page) {
    return (
      <Screen title="Guide">
        <p className="text-muted">That page isn't in your program.</p>
      </Screen>
    );
  }
  const pos = programPosition(program, profile.startDate, today);
  const values = placeholderValues(program, profile.startKg, profile.goalKg);
  const next = program.pages[index + 1];
  return (
    <Screen title={page.title} kicker={page.kicker} back="/more">
      <article className="space-y-6">
        {page.sections.map((section, i) => (
          <SectionView
            key={i}
            section={section}
            program={program}
            values={values}
            currentPhase={pos.status === "active" ? pos.phase.number : undefined}
          />
        ))}
      </article>
      {next && (
        <Link
          to={`/more/guide/${next.id}`}
          className="mt-8 flex min-h-14 items-center justify-between rounded-2xl bg-surface px-4 ring-1 ring-line"
        >
          <span>
            <span className="block text-xs font-bold tracking-wide text-muted uppercase">Next</span>
            <span className="font-semibold text-ink">{next.title}</span>
          </span>
          <span aria-hidden="true">→</span>
        </Link>
      )}
    </Screen>
  );
}

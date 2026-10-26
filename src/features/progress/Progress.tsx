import { useSearchParams } from "react-router";
import { useActive } from "@/app/active";
import { Screen } from "@/components/Screen";
import { CheckinTab } from "./CheckinTab";
import { MeasurementsTab } from "./MeasurementsTab";
import { FitnessTab, StepsTab, StrengthTab, WinsTab } from "./OtherTabs";
import { WeightTab } from "./WeightTab";

export function ProgressScreen() {
  const { program } = useActive();
  const [params, setParams] = useSearchParams();
  const tabs = [
    { id: "weight", label: "Weight" },
    { id: "measurements", label: program.setting === "home" ? "Waist & body" : "Measurements" },
    { id: "steps", label: "Steps" },
    ...(program.tracking.strengthCheckins ? [{ id: "strength", label: "Strength" }] : []),
    ...(program.tracking.fitnessTests ? [{ id: "tests", label: "Fitness tests" }] : []),
    { id: "wins", label: "Wins" },
    { id: "checkin", label: "Check-in" },
  ];
  const tab = tabs.find((t) => t.id === params.get("tab"))?.id ?? "weight";

  return (
    <Screen title="Progress" kicker={program.subtitle}>
      <div
        role="tablist"
        aria-label="Progress sections"
        className="-mx-4 mb-4 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none]"
      >
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            ref={
              tab === t.id
                ? (el) => el?.scrollIntoView({ inline: "center", block: "nearest" })
                : undefined
            }
            onClick={() => setParams({ tab: t.id }, { replace: true })}
            className={`min-h-11 shrink-0 rounded-full px-4 text-sm font-semibold whitespace-nowrap ${
              tab === t.id
                ? "bg-accent-strong text-on-accent"
                : "bg-surface text-ink ring-1 ring-line"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" aria-label={tabs.find((t) => t.id === tab)?.label}>
        {tab === "weight" && <WeightTab />}
        {tab === "measurements" && <MeasurementsTab />}
        {tab === "steps" && <StepsTab />}
        {tab === "strength" && <StrengthTab />}
        {tab === "tests" && <FitnessTab />}
        {tab === "wins" && <WinsTab />}
        {tab === "checkin" && <CheckinTab />}
      </div>
    </Screen>
  );
}

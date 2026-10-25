import { useMemo, useState } from "react";
import { Link } from "react-router";
import { useActive } from "@/app/active";
import { assetUrl } from "@/content/loader";
import { Screen } from "@/components/Screen";
import { inputClass } from "@/components/ui";

export function LibraryScreen() {
  const { program } = useActive();
  const [query, setQuery] = useState("");
  const [muscle, setMuscle] = useState("");
  const [equipment, setEquipment] = useState("");

  const all = Object.values(program.exercises);
  const muscles = useMemo(() => [...new Set(all.flatMap((e) => e.muscles))].sort(), [all]);
  const equipmentList = useMemo(() => [...new Set(all.flatMap((e) => e.equipment))].sort(), [all]);

  const q = query.trim().toLowerCase();
  const matches = (id: string) => {
    const e = program.exercises[id];
    if (!e) return false;
    if (muscle && !e.muscles.includes(muscle)) return false;
    if (equipment && !e.equipment.includes(equipment)) return false;
    return (
      !q || e.name.toLowerCase().includes(q) || e.muscles.some((m) => m.toLowerCase().includes(q))
    );
  };
  const sections = program.guideSections
    .map((s) => ({ ...s, ids: s.exerciseIds.filter(matches) }))
    .filter((s) => s.ids.length > 0);

  return (
    <Screen title="Exercises" kicker={program.subtitle}>
      <div className="mb-5 space-y-2">
        <input
          type="search"
          aria-label="Search exercises"
          placeholder="Search by name or muscle"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className={inputClass}
        />
        <div className="grid grid-cols-2 gap-2">
          <select
            aria-label="Filter by muscle"
            className={`${inputClass} text-base`}
            value={muscle}
            onChange={(e) => setMuscle(e.target.value)}
          >
            <option value="">All muscles</option>
            {muscles.map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
          <select
            aria-label="Filter by equipment"
            className={`${inputClass} text-base`}
            value={equipment}
            onChange={(e) => setEquipment(e.target.value)}
          >
            <option value="">All equipment</option>
            {equipmentList.map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
        </div>
      </div>

      {sections.length === 0 && (
        <p className="text-muted">No exercises match. Try a different search.</p>
      )}
      <div className="space-y-6">
        {sections.map((section) => (
          <section key={section.title} aria-label={section.title}>
            <h2 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">
              {section.title}
            </h2>
            <ul className="divide-y divide-line overflow-hidden rounded-2xl bg-surface ring-1 ring-line">
              {section.ids.map((id) => {
                const e = program.exercises[id]!;
                return (
                  <li key={id}>
                    <Link
                      to={`/exercises/${id}`}
                      className="flex min-h-18 items-center gap-3 px-3 py-2 active:bg-surface-2"
                    >
                      <span className="size-16 shrink-0 overflow-hidden rounded-xl bg-panel">
                        <img
                          src={assetUrl(e.images.start)}
                          alt=""
                          loading="lazy"
                          decoding="async"
                          width={64}
                          height={64}
                          className="size-full object-contain"
                        />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block font-semibold text-ink">{e.name}</span>
                        <span className="block truncate text-sm text-muted">
                          {e.muscles.join(" · ")}
                        </span>
                      </span>
                      <span aria-hidden="true" className="text-muted">
                        ›
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>
    </Screen>
  );
}

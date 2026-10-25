import { Link } from "react-router";
import { useLiveQuery } from "dexie-react-hooks";
import { useActive } from "@/app/active";
import { programs } from "@/content/loader";
import { db } from "@/db/db";
import { setSetting } from "@/db/settings";
import { Icon } from "@/components/Icon";
import { Screen } from "@/components/Screen";

function Row({ to, title, subtitle }: { to: string; title: string; subtitle?: string }) {
  return (
    <li>
      <Link
        to={to}
        className="flex min-h-14 items-center justify-between gap-3 px-4 py-2 active:bg-surface-2"
      >
        <span>
          <span className="block font-semibold text-ink">{title}</span>
          {subtitle && <span className="block text-sm text-muted">{subtitle}</span>}
        </span>
        <Icon name="chevron" className="size-5 shrink-0 text-muted" />
      </Link>
    </li>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="mb-6">
      <h2 className="mb-2 text-sm font-bold tracking-wide text-muted uppercase">{title}</h2>
      <ul className="divide-y divide-line overflow-hidden rounded-2xl bg-surface ring-1 ring-line">
        {children}
      </ul>
    </section>
  );
}

export function MoreScreen() {
  const { profile, program } = useActive();
  const profiles = useLiveQuery(() => db.profiles.orderBy("createdAt").toArray(), []);
  return (
    <Screen title="More">
      <Group title="Guide">
        {program.pages.map((p) => (
          <Row key={p.id} to={`/more/guide/${p.id}`} title={p.title} subtitle={p.kicker} />
        ))}
      </Group>

      <Group title="Profiles">
        {profiles?.map((p) => {
          const active = p.id === profile.id;
          return (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => void setSetting("activeProfileId", p.id)}
                aria-pressed={active}
                className="flex min-h-14 w-full items-center justify-between gap-3 px-4 py-2 text-left active:bg-surface-2"
              >
                <span>
                  <span className="block font-semibold text-ink">{p.name}</span>
                  <span className="block text-sm text-muted">
                    {programs[p.programId]?.subtitle}
                  </span>
                </span>
                {active && <Icon name="check" className="size-5 text-accent-ink" />}
              </button>
            </li>
          );
        })}
        <Row to="/new-profile" title="Add a profile" subtitle="For someone else on this phone" />
      </Group>

      <Group title="App">
        <Row to="/more/settings" title="Settings" subtitle="Profile, reminders, sound, theme" />
        <Row to="/more/backup" title="Backup & restore" subtitle="Keep your history safe" />
      </Group>
      <p className="text-center text-xs text-muted">
        Lean & Strong · your data stays on this device
      </p>
    </Screen>
  );
}

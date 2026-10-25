import { NavLink } from "react-router";
import { Icon, type IconName } from "./Icon";

const tabs: { to: string; label: string; icon: IconName }[] = [
  { to: "/", label: "Today", icon: "today" },
  { to: "/plan", label: "Plan", icon: "plan" },
  { to: "/exercises", label: "Exercises", icon: "exercises" },
  { to: "/progress", label: "Progress", icon: "progress" },
  { to: "/more", label: "More", icon: "more" },
];

export function TabBar() {
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface/95 backdrop-blur"
      style={{ paddingBottom: "var(--safe-bottom)" }}
    >
      <ul className="mx-auto flex max-w-xl">
        {tabs.map((tab) => (
          <li key={tab.to} className="flex-1">
            <NavLink
              to={tab.to}
              end={tab.to === "/"}
              className={({ isActive }) =>
                `flex min-h-14 flex-col items-center justify-center gap-0.5 pt-1.5 pb-1 text-[0.7rem] font-medium ${
                  isActive ? "text-accent-ink" : "text-muted"
                }`
              }
            >
              <Icon name={tab.icon} />
              {tab.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

import { HashRouter, Outlet, Route, Routes } from "react-router";
import { TabBar } from "./components/TabBar";
import { Screen } from "./components/Screen";
import { programList } from "./content/loader";

function TabsLayout() {
  return (
    <>
      <Outlet />
      <TabBar />
    </>
  );
}

function TodayScreen() {
  return (
    <Screen title="Today" kicker="Lean & Strong">
      <ul className="space-y-3">
        {programList.map((p) => (
          <li key={p.id} className="rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-line">
            <p
              className="text-xs font-semibold tracking-wide uppercase"
              style={{ color: p.theme.accent }}
            >
              {p.weeks} weeks · {p.setting}
            </p>
            <h2 className="text-lg font-bold">{p.title}</h2>
            <p className="text-sm text-muted">{p.subtitle}</p>
          </li>
        ))}
      </ul>
    </Screen>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <Screen title={title}>
      <p className="text-muted">Coming in a later milestone.</p>
    </Screen>
  );
}

export function App() {
  return (
    <HashRouter>
      <Routes>
        <Route element={<TabsLayout />}>
          <Route index element={<TodayScreen />} />
          <Route path="plan" element={<Placeholder title="Plan" />} />
          <Route path="exercises" element={<Placeholder title="Exercises" />} />
          <Route path="progress" element={<Placeholder title="Progress" />} />
          <Route path="more" element={<Placeholder title="More" />} />
        </Route>
      </Routes>
    </HashRouter>
  );
}

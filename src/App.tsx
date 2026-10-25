import { lazy, Suspense } from "react";
import { HashRouter, Navigate, Outlet, Route, Routes, useNavigate } from "react-router";
import { useActiveState, useAppTheme } from "./app/active";
import { ActiveProvider } from "./app/ActiveProvider";
import { Screen } from "./components/Screen";
import { TabBar } from "./components/TabBar";
import { Onboarding } from "./features/onboarding/Onboarding";
import { TodayScreen } from "./features/today/Today";
import { MoreScreen } from "./features/more/More";
import { useStartupTasks } from "./app/startup";

// Bigger screens load on first use (they're all precached, so this works offline too).
const named = <K extends string>(load: () => Promise<Record<K, React.ComponentType>>, name: K) =>
  lazy(() => load().then((m) => ({ default: m[name] })));
const WorkoutScreen = named(() => import("./features/player/Player"), "WorkoutScreen");
const WalkScreen = named(() => import("./features/player/WalkScreen"), "WalkScreen");
const LogPastScreen = named(() => import("./features/player/LogPast"), "LogPastScreen");
const PlanScreen = named(() => import("./features/plan/Plan"), "PlanScreen");
const SessionPageScreen = named(() => import("./features/plan/SessionPage"), "SessionPageScreen");
const LibraryScreen = named(() => import("./features/library/Library"), "LibraryScreen");
const ExerciseDetailScreen = named(
  () => import("./features/library/ExerciseDetail"),
  "ExerciseDetailScreen",
);
const ProgressScreen = named(() => import("./features/progress/Progress"), "ProgressScreen");
const GuidePageScreen = named(() => import("./features/guide/GuidePage"), "GuidePageScreen");
const SettingsScreen = named(() => import("./features/settings/Settings"), "SettingsScreen");
const BackupScreen = named(() => import("./features/settings/Backup"), "BackupScreen");
import { InstallBanner, UpdatePrompt } from "./features/pwa/WebApp";
import { isNative } from "./platform";
import { NativeBridge } from "./platform/NativeBridge";

function TabsLayout() {
  return (
    <>
      <InstallBanner />
      <Suspense fallback={<div className="min-h-dvh" />}>
        <Outlet />
      </Suspense>
      <TabBar />
    </>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <Screen title={title}>
      <p className="text-muted">Coming in a later milestone.</p>
    </Screen>
  );
}

function AddProfile() {
  const navigate = useNavigate();
  return <Onboarding onCancel={() => navigate(-1)} />;
}

function Gate() {
  const state = useActiveState();
  useAppTheme(state.status === "ready" ? state.program : undefined);
  useStartupTasks(
    state.status === "ready" ? state.profile : undefined,
    state.status === "ready" ? state.program : undefined,
  );

  if (state.status === "loading") {
    return <div className="min-h-dvh bg-bg" aria-busy="true" />;
  }
  if (state.status === "none") return <Onboarding />;

  return (
    <ActiveProvider value={{ profile: state.profile, program: state.program }}>
      <Suspense fallback={<div className="min-h-dvh bg-bg" />}>
        <Routes>
          <Route element={<TabsLayout />}>
            <Route index element={<TodayScreen />} />
            <Route path="plan" element={<PlanScreen />} />
            <Route path="plan/session/:sessionId" element={<SessionPageScreen />} />
            <Route path="exercises" element={<LibraryScreen />} />
            <Route path="exercises/:exerciseId" element={<ExerciseDetailScreen />} />
            <Route path="progress" element={<ProgressScreen />} />
            <Route path="more" element={<MoreScreen />} />
            <Route path="more/guide/:pageId" element={<GuidePageScreen />} />
            <Route path="more/settings" element={<SettingsScreen />} />
            <Route path="more/backup" element={<BackupScreen />} />
            <Route path="log-past" element={<LogPastScreen />} />
            <Route path="complete" element={<Placeholder title="Program complete" />} />
          </Route>
          <Route path="workout/:sessionId" element={<WorkoutScreen />} />
          <Route path="walk" element={<WalkScreen />} />
          <Route path="new-profile" element={<AddProfile />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </ActiveProvider>
  );
}

export function App() {
  return (
    <HashRouter>
      {!isNative && <UpdatePrompt />}
      {isNative && <NativeBridge />}
      <Gate />
    </HashRouter>
  );
}

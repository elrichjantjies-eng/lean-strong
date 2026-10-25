import { HashRouter, Navigate, Outlet, Route, Routes, useNavigate } from "react-router";
import { useActiveState, useAppTheme } from "./app/active";
import { ActiveProvider } from "./app/ActiveProvider";
import { Screen } from "./components/Screen";
import { TabBar } from "./components/TabBar";
import { Onboarding } from "./features/onboarding/Onboarding";
import { TodayScreen } from "./features/today/Today";
import { LogPastScreen } from "./features/player/LogPast";
import { WorkoutScreen } from "./features/player/Player";
import { WalkScreen } from "./features/player/WalkScreen";
import { GuidePageScreen } from "./features/guide/GuidePage";
import { ExerciseDetailScreen } from "./features/library/ExerciseDetail";
import { LibraryScreen } from "./features/library/Library";
import { MoreScreen } from "./features/more/More";
import { PlanScreen } from "./features/plan/Plan";
import { SessionPageScreen } from "./features/plan/SessionPage";

function TabsLayout() {
  return (
    <>
      <Outlet />
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

  if (state.status === "loading") {
    return <div className="min-h-dvh bg-bg" aria-busy="true" />;
  }
  if (state.status === "none") return <Onboarding />;

  return (
    <ActiveProvider value={{ profile: state.profile, program: state.program }}>
      <Routes>
        <Route element={<TabsLayout />}>
          <Route index element={<TodayScreen />} />
          <Route path="plan" element={<PlanScreen />} />
          <Route path="plan/session/:sessionId" element={<SessionPageScreen />} />
          <Route path="exercises" element={<LibraryScreen />} />
          <Route path="exercises/:exerciseId" element={<ExerciseDetailScreen />} />
          <Route path="progress/*" element={<Placeholder title="Progress" />} />
          <Route path="more" element={<MoreScreen />} />
          <Route path="more/guide/:pageId" element={<GuidePageScreen />} />
          <Route path="more/*" element={<Placeholder title="Settings" />} />
          <Route path="log-past" element={<LogPastScreen />} />
          <Route path="complete" element={<Placeholder title="Program complete" />} />
        </Route>
        <Route path="workout/:sessionId" element={<WorkoutScreen />} />
        <Route path="walk" element={<WalkScreen />} />
        <Route path="new-profile" element={<AddProfile />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ActiveProvider>
  );
}

export function App() {
  return (
    <HashRouter>
      <Gate />
    </HashRouter>
  );
}

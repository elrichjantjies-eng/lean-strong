import type { ReactNode } from "react";
import type { Program } from "@/content/types";
import type { Profile } from "@/domain/model";
import { ActiveContext } from "./active";

export function ActiveProvider({
  value,
  children,
}: {
  value: { profile: Profile; program: Program };
  children: ReactNode;
}) {
  return <ActiveContext.Provider value={value}>{children}</ActiveContext.Provider>;
}

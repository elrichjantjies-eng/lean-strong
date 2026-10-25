import { programSchema } from "./schema";
import type { Program } from "./types";

// Every JSON file in ./programs is a program, so a new one can be added by dropping in a file.
const files = import.meta.glob<{ default: unknown }>("./programs/*.json", { eager: true });

export const programs: Record<string, Program> = Object.fromEntries(
  Object.entries(files).map(([path, mod]) => {
    const result = programSchema.safeParse(mod.default);
    if (!result.success) {
      throw new Error(`Invalid program file ${path}: ${result.error.message}`);
    }
    return [result.data.id, result.data];
  }),
);

export const programList: Program[] = Object.values(programs);

export function getProgram(id: string): Program {
  const program = programs[id];
  if (!program) throw new Error(`Unknown program "${id}"`);
  return program;
}

/** Resolve a relative content path ("assets/exercises/…") against the app's base URL. */
export function assetUrl(path: string): string {
  return `${import.meta.env.BASE_URL}${path}`;
}

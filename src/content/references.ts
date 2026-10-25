import type { Program } from "./types";

/** Every place in a program that points at an exercise, with a human-readable location. */
export function exerciseReferences(program: Program): { exerciseId: string; where: string }[] {
  const refs: { exerciseId: string; where: string }[] = [];
  for (const session of Object.values(program.sessions)) {
    for (const pair of session.pairs) {
      for (const item of pair.items) {
        refs.push({ exerciseId: item.exerciseId, where: `session ${session.id} ${item.code}` });
      }
    }
    if (session.finisher?.kind === "circuit") {
      for (const step of session.finisher.circuit.steps) {
        refs.push({ exerciseId: step.exerciseId, where: `session ${session.id} finisher` });
      }
    }
    for (const step of session.waistCircuit?.steps ?? []) {
      refs.push({ exerciseId: step.exerciseId, where: `session ${session.id} waist circuit` });
    }
  }
  for (const section of program.guideSections) {
    for (const exerciseId of section.exerciseIds) {
      refs.push({ exerciseId, where: `guide section "${section.title}"` });
    }
  }
  for (const exerciseId of program.tracking.strengthCheckins?.exerciseIds ?? []) {
    refs.push({ exerciseId, where: "strength check-ins" });
  }
  for (const test of program.tracking.fitnessTests?.tests ?? []) {
    if (test.exerciseId)
      refs.push({ exerciseId: test.exerciseId, where: `fitness test ${test.id}` });
  }
  return refs;
}

/** Problems with internal references (missing exercises, sessions, mismatched ids). */
export function referenceProblems(program: Program): string[] {
  const problems: string[] = [];
  for (const ref of exerciseReferences(program)) {
    if (!program.exercises[ref.exerciseId]) {
      problems.push(`${program.id}: unknown exercise "${ref.exerciseId}" in ${ref.where}`);
    }
  }
  for (const [key, exercise] of Object.entries(program.exercises)) {
    if (key !== exercise.id)
      problems.push(`${program.id}: exercise key "${key}" ≠ id "${exercise.id}"`);
  }
  for (const [key, session] of Object.entries(program.sessions)) {
    if (key !== session.id)
      problems.push(`${program.id}: session key "${key}" ≠ id "${session.id}"`);
  }
  for (const day of program.weekTemplate) {
    if (day.kind === "workout" && (!day.sessionId || !program.sessions[day.sessionId])) {
      problems.push(`${program.id}: ${day.weekday} has unknown session "${day.sessionId}"`);
    }
  }
  return problems;
}

/** All image paths a program uses (relative, e.g. "assets/exercises/…"). */
export function imagePaths(program: Program): string[] {
  return Object.values(program.exercises).flatMap((e) =>
    [e.images.start, e.images.end, e.images.loop].filter((p): p is string => p !== null),
  );
}

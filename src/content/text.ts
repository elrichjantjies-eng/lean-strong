/** Values available to `{placeholder}` tokens in program text. */
export interface PlaceholderValues {
  startKg?: number | string;
  goalKg?: number | string;
  weeks?: number | string;
  ratePerWeek?: number | string;
  weeksToGoal?: number | string;
  checkpointWeek?: number | string;
  checkpointKg?: number | string;
}

/** Replace known `{placeholders}`; unknown or missing ones are left as written. */
export function fillPlaceholders(text: string, values: PlaceholderValues): string {
  return text.replace(/\{(\w+)\}/g, (match, key: string) => {
    const value = values[key as keyof PlaceholderValues];
    return value === undefined ? match : String(value);
  });
}

export type TextRun = { text: string; bold: boolean };

/** Split text on `**bold**` markers (the only inline markup in the content). */
export function parseBold(text: string): TextRun[] {
  const runs: TextRun[] = [];
  const parts = text.split("**");
  parts.forEach((part, i) => {
    if (part) runs.push({ text: part, bold: i % 2 === 1 });
  });
  return runs;
}

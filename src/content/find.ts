import type { Page, Program, Section } from "./types";

type Callout = Extract<Section, { type: "callout" }>;
type CardItem = Extract<Section, { type: "cards" }>["cards"][number];

function sections(program: Program): { page: Page; section: Section }[] {
  return program.pages.flatMap((page) => page.sections.map((section) => ({ page, section })));
}

/** First callout whose title starts with `prefix` (case-insensitive). */
export function findCallout(program: Program, prefix: string): Callout | null {
  const p = prefix.toLowerCase();
  for (const { section } of sections(program)) {
    if (section.type === "callout" && section.title?.toLowerCase().startsWith(p)) return section;
  }
  return null;
}

/** First card (inside a `cards` section) with this title. */
export function findCard(program: Program, title: string): CardItem | null {
  for (const { section } of sections(program)) {
    if (section.type !== "cards") continue;
    const card = section.cards.find((c) => c.title.toLowerCase() === title.toLowerCase());
    if (card) return card;
  }
  return null;
}

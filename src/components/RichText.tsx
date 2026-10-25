import { fillPlaceholders, parseBold, type PlaceholderValues } from "@/content/text";

/** Render content text: fills `{placeholders}` and turns `**bold**` into <strong>. */
export function RichText({ text, values = {} }: { text: string; values?: PlaceholderValues }) {
  return (
    <>
      {parseBold(fillPlaceholders(text, values)).map((run, i) =>
        run.bold ? (
          <strong key={i} className="font-semibold">
            {run.text}
          </strong>
        ) : (
          <span key={i}>{run.text}</span>
        ),
      )}
    </>
  );
}

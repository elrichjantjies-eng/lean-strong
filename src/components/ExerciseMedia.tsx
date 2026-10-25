import { useState } from "react";
import { assetUrl } from "@/content/loader";
import type { Exercise } from "@/content/types";
import { frameLabels, usePrefersReducedMotion } from "./media";
import { RichText } from "./RichText";
import { Sheet } from "./Sheet";
import { Callout } from "./ui";

/**
 * The looping demo on the light illustration panel (figures are navy line art, so the panel
 * stays light in dark mode). Falls back to the still start picture for reduced motion or when
 * there's no loop.
 */
export function ExerciseDemo({
  exercise,
  className = "",
  lazy = false,
}: {
  exercise: Exercise;
  className?: string;
  lazy?: boolean;
}) {
  const reduced = usePrefersReducedMotion();
  const src = !reduced && exercise.images.loop ? exercise.images.loop : exercise.images.start;
  return (
    <div className={`flex items-center justify-center rounded-2xl bg-panel ${className}`}>
      <img
        src={assetUrl(src)}
        alt={`${exercise.name} demonstration`}
        loading={lazy ? "lazy" : "eager"}
        decoding="async"
        className="h-full max-h-full w-full object-contain"
      />
    </div>
  );
}

export function ExerciseFrames({ exercise, lazy = false }: { exercise: Exercise; lazy?: boolean }) {
  const labels = frameLabels(exercise);
  const frames = [exercise.images.start, exercise.images.end];
  return (
    <div className="grid grid-cols-2 gap-2">
      {frames.map((src, i) => (
        <figure key={src} className="rounded-2xl bg-panel p-2">
          <img
            src={assetUrl(src)}
            alt={`${exercise.name}: ${labels[i]}`}
            loading={lazy ? "lazy" : "eager"}
            decoding="async"
            className="aspect-square w-full object-contain"
          />
          <figcaption className="mt-1 text-center text-sm font-semibold text-[#2a3349]">
            {i + 1}. {labels[i]}
          </figcaption>
        </figure>
      ))}
    </div>
  );
}

export function ExerciseCues({ exercise }: { exercise: Exercise }) {
  return (
    <div className="space-y-3">
      <ol className="space-y-2">
        {exercise.cues.map((cue, i) => (
          <li key={i} className="flex gap-3 text-ink">
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-accent-soft text-sm font-bold text-accent-ink">
              {i + 1}
            </span>
            <span className="pt-0.5">
              <RichText text={cue} />
            </span>
          </li>
        ))}
      </ol>
      <Callout tone="accent" title="Watch out">
        <p>
          <RichText text={exercise.watchOut} />
        </p>
      </Callout>
    </div>
  );
}

/** Tap the demo to see pictures 1 and 2, the cues and the watch-out. */
export function ExerciseMedia({
  exercise,
  className = "h-56",
}: {
  exercise: Exercise;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={`Show how to do ${exercise.name}`}
        className="block w-full"
      >
        <ExerciseDemo exercise={exercise} className={className} />
      </button>
      <Sheet open={open} title={exercise.name} onClose={() => setOpen(false)}>
        <div className="space-y-4">
          <ExerciseFrames exercise={exercise} />
          <p className="text-sm text-muted">{exercise.muscles.join(" · ")}</p>
          <ExerciseCues exercise={exercise} />
        </div>
      </Sheet>
    </>
  );
}

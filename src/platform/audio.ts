/**
 * Beeps with the Web Audio API. iOS only allows audio after a user gesture, so call
 * `unlockAudio()` from the first tap of a session (e.g. the Start button).
 */
let ctx: AudioContext | null = null;

function context(): AudioContext | null {
  if (ctx) return ctx;
  const Ctor =
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  ctx = new Ctor();
  return ctx;
}

export function unlockAudio(): void {
  const c = context();
  if (!c) return;
  if (c.state === "suspended") void c.resume();
  // Play one silent sample: this is what actually unlocks output on iOS.
  const buffer = c.createBuffer(1, 1, 22_050);
  const src = c.createBufferSource();
  src.buffer = buffer;
  src.connect(c.destination);
  src.start(0);
}

export function beep(kind: "tick" | "go" = "tick"): void {
  const c = context();
  if (!c || c.state !== "running") return;
  const osc = c.createOscillator();
  const gain = c.createGain();
  const duration = kind === "go" ? 0.45 : 0.12;
  osc.type = "sine";
  osc.frequency.value = kind === "go" ? 1320 : 880;
  gain.gain.setValueAtTime(0.0001, c.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.6, c.currentTime + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + duration);
  osc.connect(gain).connect(c.destination);
  osc.start();
  osc.stop(c.currentTime + duration + 0.02);
}

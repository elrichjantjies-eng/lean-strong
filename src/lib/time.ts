/** Current epoch ms. Only call from event handlers and effects, never while rendering. */
export const nowMs = (): number => Date.now();

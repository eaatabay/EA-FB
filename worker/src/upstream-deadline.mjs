/**
 * One deadline for upstream headers AND body. An upstream that sends headers
 * then stalls must not keep the public catalog request open indefinitely.
 * The caller owns response validation; no secret, URL or error body is logged.
 */
export async function withUpstreamDeadline(task, timeoutMs = 12000) {
  if (typeof task !== "function" || !Number.isSafeInteger(timeoutMs) ||
      timeoutMs < 100 || timeoutMs > 60000) {
    throw new Error("invalid_upstream_deadline");
  }
  const controller = new AbortController();
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(() => task(controller.signal)),
      new Promise((_, reject) => {
        timer = setTimeout(() => {
          reject(Object.assign(new Error("upstream_deadline_exceeded"),
            {name:"TimeoutError"}));
          // Reject before abort: a synchronous abort handler cannot turn a
          // timed-out upstream into a late successful response.
          try { controller.abort(); } catch { /* timeout already rejected */ }
        }, timeoutMs);
      }),
    ]);
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}

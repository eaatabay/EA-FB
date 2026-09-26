/** Byte-limited JSON text reader. Reject before buffering even with lying headers. */
export async function readBoundedText(response, maxBytes) {
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1) {
    throw new Error("invalid_upstream_budget");
  }
  const advertised = response.headers.get("content-length");
  if (advertised !== null && /^\d+$/.test(advertised) &&
      Number(advertised) > maxBytes) throw new Error("upstream_too_large");
  if (!response.body) throw new Error("upstream_empty");
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", {fatal:true});
  let bytes = 0, text = "";
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      if (!(value instanceof Uint8Array)) throw new Error("upstream_invalid_chunk");
      bytes += value.byteLength;
      if (bytes > maxBytes) {
        // Cancellation may itself hang on a hostile stream. Reject the byte
        // budget immediately and observe cancellation errors asynchronously.
        try { void Promise.resolve(reader.cancel()).catch(() => {}); }
        catch { /* Still reject oversized data. */ }
        throw new Error("upstream_too_large");
      }
      text += decoder.decode(value, {stream:true});
    }
    return text + decoder.decode();
  } finally {
    reader.releaseLock();
  }
}

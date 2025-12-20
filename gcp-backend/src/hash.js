import crypto from "crypto";
import fetch from "node-fetch";

export async function sha256OfUrl(url) {
  const r = await fetch(url, { redirect: "follow" });
  if (!r.ok || !r.body) throw new Error(`Hash fetch failed: ${r.status}`);

  const hash = crypto.createHash("sha256");
  for await (const chunk of r.body) hash.update(chunk);
  return hash.digest("hex");
}

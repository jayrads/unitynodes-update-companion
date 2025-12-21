import express from "express";
import { initFirebaseAdmin, upsertDeviceToken, getLatest, setLatest } from "./firestore.js";
import { sendPushToTokens } from "./fcm.js";
import { scrapeUnityNodesIndexForLatest } from "./unitynodesScraper.js";
import { sha256OfUrl } from "./hash.js";

const app = express();
app.use(express.json({ limit: "1mb" }));

const API_KEY = process.env.API_KEY || "";
const UNITYNODES_INDEX_URL = process.env.UNITYNODES_INDEX_URL || "";
const PORT = process.env.PORT || 8080;

function requireApiKey(req, res, next) {
  const auth = req.headers.authorization || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!API_KEY || token !== API_KEY) return res.status(401).json({ error: "Unauthorized" });
  next();
}

initFirebaseAdmin();

app.get("/health", (_req, res) => res.json({ ok: true }));

app.get("/latest.json", async (_req, res) => {
  const latest = await getLatest();
  if (!latest) return res.status(404).json({ error: "latest not set" });
  res.set("Cache-Control", "no-store");
  res.json(latest);
});

app.post("/registerToken", requireApiKey, async (req, res) => {
  const { token, appVersion, deviceId } = req.body || {};
  if (!token || typeof token !== "string") return res.status(400).json({ error: "token required" });

  await upsertDeviceToken({
    token,
    platform: "android",
    appVersion: appVersion || null,
    deviceId: deviceId || null
  });

  res.json({ ok: true });
});

app.post("/scrape", requireApiKey, async (_req, res) => {
  try {
    if (!UNITYNODES_INDEX_URL) {
      return res.status(500).json({ error: "UNITYNODES_INDEX_URL not configured" });
    }

    const current = await getLatest();
    const found = await scrapeUnityNodesIndexForLatest(UNITYNODES_INDEX_URL);
    if (!found) return res.status(500).json({ error: "scrape failed" });

    // "New" means the APK URL changed (a new artifact is available).
    const isNew = !current || current.apkUrl !== found.apkUrl;

    // Refresh Firestore even when the APK URL is the same, if any metadata changed.
    // This prevents stale fields from lingering when we tweak parsing/formatting logic.
    const needsRefresh =
      !current ||
      current.apkUrl !== found.apkUrl ||
      current.fileName !== found.fileName ||
      current.versionName !== found.versionName ||
      current.publishedAt !== found.publishedAt ||
      Number(current.sizeBytes) !== Number(found.sizeBytes);

    if (!needsRefresh) {
        const latest = {
            ...current,
            checkedAt: new Date().toISOString()
        };

        // Optional: persist checkedAt so /latest.json reflects liveness
        await setLatest(latest);

        return res.json({ ok: true, isNew: false, latest });
    }

    // Only compute SHA when the underlying APK changes or we don't have it yet.
    // (If we don't have current, or if current.sha256 is missing, compute it.)
    const sha256 =
      !current || isNew || !current.sha256 ? await sha256OfUrl(found.apkUrl) : current.sha256;

    const latest = {
        versionName: found.versionName,
        apkUrl: found.apkUrl,
        fileName: found.fileName,
        publishedAt: found.publishedAt,
        sizeBytes: found.sizeBytes,
        sha256,
        updatedAt: new Date().toISOString(),
        checkedAt: new Date().toISOString()
    };

    await setLatest(latest);

    // Push is best-effort; it should not prevent updating Firestore.
    let pushed = null;
    try {
      pushed = await sendPushToTokens({
        title: "UnityNodes update available",
        body: `New APK: ${latest.versionName}`,
        data: { versionName: latest.versionName, apkUrl: latest.apkUrl }
      });
    } catch (err) {
      console.error("FCM push failed (continuing):", err);
      pushed = { ok: false, error: String(err?.message || err) };
    }

    res.json({ ok: true, isNew, latest, pushed });
  } catch (err) {
    console.error("SCRAPE ERROR:", err);
    res.status(500).json({ error: "scrape crashed", details: String(err?.message || err) });
  }
});

app.listen(PORT, () => console.log(`Backend listening on :${PORT}`));

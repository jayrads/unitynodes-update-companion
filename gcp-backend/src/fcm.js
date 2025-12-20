import admin from "firebase-admin";
import { getAllTokens, deleteDeviceToken } from "./firestore.js";

export async function sendPushToTokens({ title, body, data }) {
  const tokens = await getAllTokens();
  if (tokens.length === 0) return { tokens: 0, successCount: 0, failureCount: 0, pruned: 0 };

  const resp = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: Object.fromEntries(Object.entries(data || {}).map(([k, v]) => [k, String(v)]))
  });

  let pruned = 0;

  await Promise.all(
    resp.responses.map(async (r, i) => {
      if (r.success) return;
      const code = r.error?.code || "";
      const token = tokens[i];

      const shouldDelete =
        code.includes("registration-token-not-registered") ||
        code.includes("invalid-registration-token") ||
        code.includes("invalid-argument");

      if (shouldDelete) {
        await deleteDeviceToken(token);
        pruned += 1;
      }
    })
  );

  return { tokens: tokens.length, successCount: resp.successCount, failureCount: resp.failureCount, pruned };
}

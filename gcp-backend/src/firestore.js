import admin from "firebase-admin";

let initialized = false;

export function initFirebaseAdmin() {
  if (initialized) return;
  admin.initializeApp();
  initialized = true;
}

function db() {
  return admin.firestore();
}

export async function upsertDeviceToken({ token, platform, appVersion, deviceId }) {
  const ref = db().collection("devices").doc(token);
  await ref.set(
    {
      token,
      platform,
      appVersion,
      deviceId,
      lastSeenAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );
}

export async function deleteDeviceToken(token) {
  await db().collection("devices").doc(token).delete();
}

export async function getAllTokens(limit = 5000) {
  const snap = await db().collection("devices").limit(limit).get();
  return snap.docs.map(d => d.get("token")).filter(Boolean);
}

export async function getLatest() {
  const doc = await db().collection("meta").doc("latest").get();
  return doc.exists ? doc.data() : null;
}

export async function setLatest(latest) {
  await db().collection("meta").doc("latest").set(latest, { merge: true });
}

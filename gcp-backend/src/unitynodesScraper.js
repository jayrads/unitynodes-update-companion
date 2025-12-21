import fetch from "node-fetch";

function parseApacheIndexRows(html) {
  const re =
    /href\s*=\s*["']([^"']+\.apk)["'][^>]*>.*?<\/a>\s+(\d{2}-[A-Za-z]{3}-\d{4})\s+(\d{2}:\d{2})\s+(\d+)/g;

  const out = [];
  let m;
  while ((m = re.exec(html)) !== null) {
    out.push({ href: m[1], dateStr: m[2], timeStr: m[3], sizeBytes: Number(m[4]) });
  }
  return out;
}

function parseApacheDateTimeAsIso(dateStr, timeStr) {
  const months = { Jan:"01",Feb:"02",Mar:"03",Apr:"04",May:"05",Jun:"06",Jul:"07",Aug:"08",Sep:"09",Oct:"10",Nov:"11",Dec:"12" };
  const [dd, mon, yyyy] = dateStr.split("-");
  const mm = months[mon] || "01";
  return new Date(`${yyyy}-${mm}-${dd}T${timeStr}:00Z`).toISOString();
}

function extractVersionName(fileName) {
  const base = fileName.replace(/\.apk$/i, "");
  return base.replace(/^Unity-/i, "");
}


export async function scrapeUnityNodesIndexForLatest(indexUrl) {
  const r = await fetch(indexUrl, { redirect: "follow" });
  if (!r.ok) return null;

  const html = await r.text();
  const rows = parseApacheIndexRows(html);
  if (rows.length === 0) return null;

  const enriched = rows.map(row => {
     const encodedFileName = row.href.split("/").pop() || row.href;

     // decode for display fields
     const fileName = decodeURIComponent(encodedFileName);

     // keep URL encoded for fetching
     const apkUrl = new URL(row.href, indexUrl).toString();

     const publishedAt = parseApacheDateTimeAsIso(row.dateStr, row.timeStr);

     return {
       apkUrl,
       fileName,
       versionName: extractVersionName(fileName),
       publishedAt,
       sizeBytes: row.sizeBytes
     };
   });

  enriched.sort((a, b) => a.publishedAt.localeCompare(b.publishedAt));
  return enriched[enriched.length - 1];
}

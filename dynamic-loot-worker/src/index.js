const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const RARITIES = new Set(["Thường", "Hiếm", "Sử Thi", "Huyền Thoại", "Thần Thoại"]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cors = corsHeaders(env, request);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    try {
      if (url.pathname === "/" || url.pathname === "/api/health") {
        return json({
          ok: true,
          service: "LQLQ Dynamic Loot Engine",
          version: "0.32.0",
          aiEnabled: String(env.AI_ENABLED || "false") === "true"
        }, 200, cors);
      }

      if (url.pathname !== "/api/random-loot") {
        return json({ ok: false, error: "Not found" }, 404, cors);
      }

      const rarity = normalizeRarity(url.searchParams.get("rarity"));
      const locale = normalizeLocale(url.searchParams.get("locale"));
      const mode = (url.searchParams.get("mode") || "auto").toLowerCase();
      const seed = url.searchParams.get("seed") || crypto.randomUUID();

      let item;
      if (shouldUseAi(mode, env, seed)) {
        try {
          item = await generateAiLoot(env, { rarity, locale, seed });
        } catch (error) {
          item = await fetchKnowledgeLoot({ rarity, locale, seed });
          item.fallbackReason = String(error?.message || error).slice(0, 160);
        }
      } else {
        item = await fetchKnowledgeLoot({ rarity, locale, seed });
      }

      return json({ ok: true, item }, 200, cors);
    } catch (error) {
      return json({
        ok: false,
        error: String(error?.message || error || "Unknown error").slice(0, 300)
      }, 500, cors);
    }
  }
};

function shouldUseAi(mode, env, seed) {
  if (mode === "knowledge") return false;
  if (mode === "ai") return String(env.AI_ENABLED || "false") === "true";
  if (String(env.AI_ENABLED || "false") !== "true") return false;
  const rate = clamp(Number(env.AI_RATE || 0.12), 0, 1);
  return seededFraction(`${seed}|ai`) < rate;
}

async function generateAiLoot(env, { rarity, locale, seed }) {
  if (!env.AI) throw new Error("Workers AI binding chưa được cấu hình.");

  const language = locale === "vi" ? "Vietnamese" : "English";
  const schema = {
    type: "object",
    properties: {
      name: { type: "string" },
      category: {
        type: "string",
        enum: [
          "Nhân vật nguyên bản", "Động vật huyền ảo", "Linh Thú", "Hồn Thú",
          "Ma Thú", "Yêu Thú", "Thần Thú", "Đạo cụ", "Vũ khí", "Cổ vật",
          "Phương tiện", "Công trình", "Địa Danh / Bản Đồ", "Thực vật", "Thiên thể", "Kỳ Vật"
        ]
      },
      description: { type: "string" },
      imagePrompt: { type: "string" }
    },
    required: ["name", "category", "description", "imagePrompt"]
  };

  const prompt = `Create one completely random, original collectible for an adventure browser, inspired by a FAMOUS, WIDELY-RECOGNIZABLE real-world domain (legendary athletes, iconic historical figures, world billionaires, famous landmarks/maps, beloved anime archetypes, well-known animals) so it feels familiar rather than obscure.\n`
    + `You decide what it is: a creature, spirit beast, person-like original character, artifact, tool, vehicle, plant, building, landmark, celestial object, or something stranger — but keep the inspiration recognizable and popular, not niche.\n`
    + `Do not use copyrighted characters, brand names, logos, real living people, political propaganda, sexual content, gore, or hateful content.\n`
    + `Write name and description in ${language}. Keep the description under 45 words.\n`
    + `The image prompt must request a single centered collectible subject, original design, no text, no logo, square composition, richly detailed trading-card art.\n`
    + `The app has already assigned rarity: ${rarity}. Seed concept: ${seed.slice(0, 80)}.`;

  const textResponse = await env.AI.run(
    env.TEXT_MODEL || "@cf/meta/llama-3.1-8b-instruct-fast",
    {
      messages: [
        { role: "system", content: "Return only schema-valid JSON for a safe original collectible." },
        { role: "user", content: prompt }
      ],
      response_format: {
        type: "json_schema",
        json_schema: schema
      },
      temperature: 1.15,
      max_tokens: 450
    }
  );

  const generated = textResponse?.response || textResponse;
  if (!generated || typeof generated !== "object") {
    throw new Error("Text model did not return structured data.");
  }

  const imagePrompt = `${sanitize(generated.imagePrompt, 1000)}, no words, no letters, no watermark, no logo`;
  const imageResponse = await env.AI.run(
    env.IMAGE_MODEL || "@cf/black-forest-labs/flux-1-schnell",
    {
      prompt: imagePrompt,
      seed: hashSeed(seed),
      steps: 4
    }
  );
  if (!imageResponse?.image) throw new Error("Image model did not return an image.");

  return {
    id: `ai-${crypto.randomUUID()}`,
    name: sanitize(generated.name, 100) || "Kỳ Vật Vô Danh",
    category: sanitize(generated.category, 60) || "Kỳ Vật",
    description: sanitize(generated.description, 420) || "Một phát hiện nguyên bản từ Vạn Giới.",
    rarity,
    stars: starsForRarity(rarity),
    imageDataUri: `data:image/jpeg;base64,${imageResponse.image}`,
    imageUrl: `data:image/jpeg;base64,${imageResponse.image}`,
    sourceType: "ai",
    sourceUrl: "",
    attribution: "Generated with Cloudflare Workers AI",
    license: "Review model terms before commercial distribution",
    generated: true
  };
}

// Dải thứ hạng lượt xem (theo ngày, Wikimedia Pageviews API) ứng với mỗi độ
// hiếm — thẻ hiếm hơn buộc phải lấy trong nhóm chủ đề CÀNG nổi tiếng, để nội
// dung luôn gần gũi/quen thuộc (tỷ phú, nhân vật lịch sử, vận động viên, địa
// danh, nhân vật anime... đang thật sự được nhiều người quan tâm) thay vì một
// bài Wikipedia ngẫu nhiên bất kỳ có thể rất xa lạ.
const FAME_RANK_BANDS = {
  "Thần Thoại": [1, 40],
  "Huyền Thoại": [1, 100],
  "Sử Thi": [40, 250],
  "Hiếm": [100, 500],
  "Thường": [200, 1000]
};

const IGNORED_TITLE_PATTERN = /^(Main_Page|Trang_Chính|Special:|Đặc_biệt:|Wikipedia:|Bản_mẫu:|Category:|Thể_loại:|Portal:|Cổng_thông_tin:|File:|Tập_tin:|Help:|Trợ_giúp:)/i;

async function fetchTopViewedTitles(language, seed) {
  // Chọn 1 ngày quá khứ ổn định (2-400 ngày trước) theo seed để dữ liệu
  // pageviews chắc chắn đã được Wikimedia tổng hợp xong (tránh ngày quá gần).
  const daysAgo = 2 + Math.floor(seededFraction(`${seed}|day`) * 398);
  const date = new Date(Date.now() - daysAgo * 86400000);
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, "0");
  const d = String(date.getUTCDate()).padStart(2, "0");
  const api = `https://wikimedia.org/api/rest_v1/metrics/pageviews/top/${language}.wikipedia/all-access/${y}/${m}/${d}`;
  const response = await fetch(api, { headers: { "user-agent": "lqlq-dynamic-loot/0.32" } });
  if (!response.ok) throw new Error(`Pageviews HTTP ${response.status}`);
  const root = await response.json();
  const articles = root?.items?.[0]?.articles || [];
  return articles.filter((entry) => entry?.article && !IGNORED_TITLE_PATTERN.test(entry.article));
}

async function fetchPageByTitle(language, title) {
  const api = new URL(`https://${language}.wikipedia.org/w/api.php`);
  api.search = new URLSearchParams({
    action: "query",
    titles: title,
    prop: "pageimages|extracts|pageprops",
    piprop: "thumbnail",
    pithumbsize: "640",
    exintro: "1",
    explaintext: "1",
    exsentences: "2",
    redirects: "1",
    format: "json",
    origin: "*"
  }).toString();
  const response = await fetch(api, { headers: { "user-agent": "lqlq-dynamic-loot/0.32" } });
  if (!response.ok) throw new Error(`Wikipedia HTTP ${response.status}`);
  const root = await response.json();
  const page = Object.values(root?.query?.pages || {})[0];
  if (!page || page.missing !== undefined) return null;
  if (!page?.thumbnail?.source || String(page.extract || "").trim().length < 24) return null;
  return page;
}

async function fetchKnowledgeLoot({ rarity, locale, seed }) {
  const languages = locale === "vi" ? ["vi", "en"] : ["en", "vi"];
  const [bandStart, bandEnd] = FAME_RANK_BANDS[rarity] || FAME_RANK_BANDS["Thường"];
  let lastError;

  for (const language of languages) {
    try {
      const candidates = await fetchTopViewedTitles(language, `${seed}|${language}`);
      if (!candidates.length) throw new Error("Empty pageviews list.");

      const bandCandidates = candidates.filter((entry) => entry.rank >= bandStart && entry.rank <= bandEnd);
      const pool = bandCandidates.length ? bandCandidates : candidates;

      for (let attempt = 0; attempt < 5; attempt += 1) {
        const index = Math.floor(seededFraction(`${seed}|${language}|pick|${attempt}`) * pool.length);
        const candidate = pool[Math.min(index, pool.length - 1)];
        const title = decodeURIComponent(candidate.article.replace(/_/g, " "));
        const page = await fetchPageByTitle(language, title);
        if (!page) continue;

        const cleanTitle = sanitize(page.title, 100) || "Thẻ Kỳ Vật Vô Danh";
        const description = sanitize(page.extract, 420) || "Một chủ đề nổi tiếng từ kho tri thức mở.";
        const qid = page?.pageprops?.wikibase_item || "";
        const pageId = Number(page.pageid || 0);

        return {
          id: qid ? `wikidata-${qid}` : `wikipedia-${language}-${pageId}`,
          name: cleanTitle,
          category: classify(cleanTitle, description),
          description,
          rarity,
          stars: starsForRarity(rarity),
          imageUrl: page.thumbnail.source,
          sourceType: "wikimedia",
          sourceUrl: `https://${language}.wikipedia.org/?curid=${pageId}`,
          attribution: "Wikipedia / Wikimedia Commons",
          license: "See the source page for image-specific license and attribution",
          generated: false
        };
      }
      throw new Error("No usable page with image found in fame band.");
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error("Knowledge source unavailable.");
}

function classify(title, description) {
  const text = `${title} ${description}`.toLowerCase();
  const has = (...terms) => terms.some((term) => text.includes(term));
  if (has("sinh năm", "mất năm", "nhà văn", "họa sĩ", "scientist", "born", "actor", "politician")) return "Nhân vật";
  if (has("loài", "động vật", "chim", "cá", "species", "animal", "bird", "mammal", "fish")) return "Động vật";
  if (has("thực vật", "cây", "hoa", "plant", "tree", "flower")) return "Thực vật";
  if (has("tỷ phú", "giàu nhất", "billionaire", "net worth", "forbes")) return "Nhân vật";
  if (has("cầu thủ", "vận động viên", "bóng đá", "footballer", "athlete", "olympic")) return "Nhân vật";
  if (has("anime", "manga", "nhân vật hoạt hình", "fictional character")) return "Nhân vật hư cấu";
  if (has("bản đồ", "thành phố", "quốc gia", "núi", "sông", "đảo", "thủ đô", "map", "city", "country", "capital", "mountain", "river", "island")) return "Địa Danh / Bản Đồ";
  if (has("di sản thế giới", "world heritage", "kỳ quan", "landmark")) return "Địa Danh / Bản Đồ";
  if (has("tòa nhà", "công trình", "nhà thờ", "building", "bridge", "temple")) return "Công trình";
  if (has("tranh", "tượng", "tác phẩm", "painting", "sculpture", "film", "novel")) return "Tác phẩm";
  if (has("hành tinh", "ngôi sao", "thiên hà", "planet", "star", "galaxy", "asteroid")) return "Thiên thể";
  if (has("thiết bị", "máy", "vũ khí", "tàu", "xe", "device", "machine", "weapon", "ship", "vehicle")) return "Đồ vật";
  return "Kỳ Vật";
}

function normalizeRarity(raw) {
  const value = String(raw || "Thường").trim();
  return RARITIES.has(value) ? value : "Thường";
}

function normalizeLocale(raw) {
  return String(raw || "vi").toLowerCase().startsWith("en") ? "en" : "vi";
}

function starsForRarity(rarity) {
  if (rarity === "Thần Thoại" || rarity === "Huyền Thoại") return 5;
  if (rarity === "Sử Thi") return 4;
  if (rarity === "Hiếm") return 3;
  return 1;
}

function sanitize(value, maxLength) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, maxLength);
}

function seededFraction(seed) {
  let hash = 2166136261;
  for (let i = 0; i < seed.length; i += 1) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0) / 4294967296;
}

function hashSeed(seed) {
  return Math.floor(seededFraction(seed) * 2_147_483_647);
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, Number.isFinite(value) ? value : min));
}

function corsHeaders(env, request) {
  const allowed = String(env.ALLOW_ORIGIN || "*");
  const origin = request.headers.get("origin") || "*";
  const selected = allowed === "*" ? "*" : (allowed.split(",").map((v) => v.trim()).includes(origin) ? origin : allowed.split(",")[0]);
  return {
    "access-control-allow-origin": selected,
    "access-control-allow-methods": "GET,OPTIONS",
    "access-control-allow-headers": "content-type",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  };
}

function json(body, status, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...JSON_HEADERS, ...extraHeaders }
  });
}

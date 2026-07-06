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
          "Phương tiện", "Công trình", "Thực vật", "Thiên thể", "Kỳ Vật"
        ]
      },
      description: { type: "string" },
      imagePrompt: { type: "string" }
    },
    required: ["name", "category", "description", "imagePrompt"]
  };

  const prompt = `Create one completely random, original collectible for an adventure browser.\n`
    + `You decide what it is: a creature, spirit beast, person-like original character, artifact, tool, vehicle, plant, building, celestial object, or something stranger.\n`
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

async function fetchKnowledgeLoot({ rarity, locale, seed }) {
  const languages = locale === "vi" ? ["vi", "en"] : ["en", "vi"];
  let lastError;

  for (const language of languages) {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        const api = new URL(`https://${language}.wikipedia.org/w/api.php`);
        api.search = new URLSearchParams({
          action: "query",
          generator: "random",
          grnnamespace: "0",
          grnlimit: "12",
          prop: "pageimages|extracts|pageprops",
          piprop: "thumbnail",
          pithumbsize: "640",
          exintro: "1",
          explaintext: "1",
          exsentences: "2",
          format: "json",
          origin: "*"
        }).toString();

        const response = await fetch(api, {
          headers: { "user-agent": "lqlq-dynamic-loot/0.32" }
        });
        if (!response.ok) throw new Error(`Wikipedia HTTP ${response.status}`);
        const root = await response.json();
        const pages = Object.values(root?.query?.pages || {}).filter((page) =>
          page?.thumbnail?.source && String(page.extract || "").trim().length >= 24
        );
        if (!pages.length) throw new Error("No random page with image.");

        const index = Math.floor(seededFraction(`${seed}|${language}|${attempt}`) * pages.length);
        const page = pages[Math.min(index, pages.length - 1)];
        const title = sanitize(page.title, 100) || "Kỳ Vật Vô Danh";
        const description = sanitize(page.extract, 420) || "Một phát hiện ngẫu nhiên từ kho tri thức mở.";
        const qid = page?.pageprops?.wikibase_item || "";
        const pageId = Number(page.pageid || 0);

        return {
          id: qid ? `wikidata-${qid}` : `wikipedia-${language}-${pageId}`,
          name: title,
          category: classify(title, description),
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
      } catch (error) {
        lastError = error;
      }
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
  if (has("thành phố", "quốc gia", "núi", "sông", "đảo", "city", "country", "mountain", "river", "island")) return "Địa danh";
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

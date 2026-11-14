// Aerolite island cells with the power rule. Each cell of row 0 holds one island: its cell scaled about its site by its fill.
//
//   q = (x, y * K, z) / S                      the scaled position in cell units, K = CELL_SIZE / CELL_HEIGHT
//   p_i(q) = |q - s_i|^2 - w_i                  the power rule value of cell i; a block takes the least value
//   wall of i toward j: (q - s_i) . V = h       V = s_j - s_i, h = |V|^2 / 2 + (w_i - w_j) / 2
//   gauge g = max over walls of ((q - s_i) . V) / h
//   cell radius R = min over walls of S * h / |N|,  N = (V.x, V.y * K, V.z)
//   island body B = (fill - g) * R              B > 0 inside the island
//
// The island is the convex polyhedron (q - s_i) . V <= fill * h. A lost cell has h <= 0 toward one wall. It draws nothing.
// prototypes/cells/README.md states the geometry, the reach rule of the mod and the limits of this view.
//
// The lattice holds three rows. Row 0 rolls its cell weight and its fill. Rows -1 and 1 have the cell weight 0.5 and hold nothing.
// The walls of a cell come from the boxes within 2 in X and Z, in the three rows.
//
// March. MARCH 0 steps from cell to cell. In each cell it clips the ray against the island polyhedron, so it needs no small steps.
// MARCH 1 steps by -B inside a cell of row 0, capped at the cell exit. -B is at most the distance to the island.
//
// Camera. iMouse.xy turns the camera, so a drag on shadertoy.com turns it. iMouse.xy = (0, 0) gives the start view.

// ---- lattice settings and rolls -----------------------------------------------------------------------
#define CELL_SIZE 256.0      // [64, 1024, 32] The cell size S in blocks.
#define CELL_HEIGHT 128.0    // [32, 512, 16] The cell height in blocks.
#define LAYER_MIN_Y 0.0      // The island bottom: the bottom of row 0, in blocks.
#define JITTER 0.8           // [0, 1, 0.05] The site jitter of the lattice.
#define WEIGHT_MIN 0.0       // [0, 1, 0.05] Cell Weight Min of the island variation.
#define WEIGHT_MAX 1.0       // [0, 1, 0.05] Cell Weight Max of the island variation.
#define FILL_MIN 0.3         // [0, 1, 0.05] Fill Min of the island variation.
#define FILL_MAX 0.9         // [0, 1, 0.05] Fill Max of the island variation.

// ---- view ---------------------------------------------------------------------------------------------
#define COLOR_BY_WEIGHT 1    // 0: one tint per cell. 1: blue at weight 0, gray at 0.5, orange at weight 1.
#define MARCH 0              // 0: cell to cell with an exact clip. 1: field steps capped at the cell exit.
#define MAX_CELLS 48         // The cell count of a MARCH 0 ray.
#define MAX_STEPS 200        // The step count of a MARCH 1 ray.
#define STEP_EPS 0.25        // The overshoot of a MARCH 1 step in blocks.
#define FAR 4000.0           // The far end of the ray in blocks at the cell size 256.
#define CAM_DIST 4.0         // The start distance of the camera from the target, in cells.
#define CAM_YAW 0.6          // The start yaw in radians.
#define CAM_PITCH 0.5        // The start pitch in radians. Positive looks down.
#define SPIN 0.0             // The turn of the camera per second of iTime, in radians.

const float K = CELL_SIZE / CELL_HEIGHT;
const float ROW0 = LAYER_MIN_Y * K / CELL_SIZE;   // the scaled Y of the bottom of row 0, in cell units
const uint SALT_SITE = 0x51u;
const uint SALT_ROLL = 0xC3u;
const uint SALT_FILL = 0xF1u;
const uint SALT_TINT = 0x7Du;

// ---- hashing ------------------------------------------------------------------------------------------
uvec3 mix3(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v;
}

vec3 rand3(ivec3 c, uint salt) {
    return vec3(mix3(uvec3(c) ^ uvec3(salt, salt * 7919u, salt * 104729u))) * (1.0 / 4294967296.0);
}

// ---- lattice ------------------------------------------------------------------------------------------
// The site in cell units. It does not depend on a cell weight.
vec3 siteOf(ivec3 c) {
    return vec3(c) + 0.5 + JITTER * (rand3(c, SALT_SITE) - 0.5) + vec3(0.0, ROW0, 0.0);
}

// Row 0 rolls its cell weight. Every other row has 0.5.
float weightOf(ivec3 c) {
    return c.y == 0 ? mix(WEIGHT_MIN, WEIGHT_MAX, rand3(c, SALT_ROLL).x) : 0.5;
}

float fillOf(ivec3 c) {
    return mix(FILL_MIN, FILL_MAX, rand3(c, SALT_FILL).x);
}

// The cell with the least power rule value among the 3x3x3 boxes, in the three rows.
ivec3 ownerOf(vec3 q) {
    ivec3 b = ivec3(floor(q - vec3(0.0, ROW0, 0.0)));
    float best = 1e30;
    ivec3 own = ivec3(b.x, clamp(b.y, -1, 1), b.z);
    for (int dz = -1; dz <= 1; dz++)
    for (int dy = -1; dy <= 1; dy++)
    for (int dx = -1; dx <= 1; dx++) {
        ivec3 c = b + ivec3(dx, dy, dz);
        if (c.y < -1 || c.y > 1) continue;
        vec3 d = q - siteOf(c);
        float v = dot(d, d) - weightOf(c);
        if (v < best) { best = v; own = c; }
    }
    return own;
}

// One cell against the ray q(t) = ro + t * rd, with t in blocks.
struct Clip {
    bool lost;     // h <= 0 toward one wall
    float tIn;     // the ray enters the island here
    float tOut;    // the ray leaves the island here
    float tExit;   // the ray leaves the cell here
    ivec3 next;    // the cell behind the exit wall
    vec3 normal;   // the block space normal of the island entry plane
    float body;    // the island body at q(t), in blocks
};

Clip clipCell(ivec3 c, vec3 ro, vec3 rd, float t) {
    Clip r;
    r.lost = false;
    r.tIn = -1e30;
    r.tOut = 1e30;
    r.tExit = 1e30;
    r.next = c;
    vec3 entry = vec3(0.0, 1.0, 0.0);
    vec3 si = siteOf(c);
    float wi = weightOf(c);
    float f = fillOf(c);
    vec3 d0 = ro - si;
    vec3 dp = d0 + t * rd;
    float g = -1e30;
    float rad = 1e30;
    for (int dz = -2; dz <= 2; dz++)
    for (int dy = -2; dy <= 2; dy++)
    for (int dx = -2; dx <= 2; dx++) {
        ivec3 o = c + ivec3(dx, dy, dz);
        if ((dx == 0 && dy == 0 && dz == 0) || o.y < -1 || o.y > 1) continue;
        vec3 V = siteOf(o) - si;
        float h = 0.5 * dot(V, V) + 0.5 * (wi - weightOf(o));
        if (h <= 0.0) r.lost = true;
        float a = dot(d0, V);
        float b = dot(rd, V);
        if (b > 0.0) {
            float te = (h - a) / b;
            if (te < r.tExit) { r.tExit = te; r.next = o; }
            r.tOut = min(r.tOut, (f * h - a) / b);
        } else if (b < 0.0) {
            float ti = (f * h - a) / b;
            if (ti > r.tIn) { r.tIn = ti; entry = V; }
        } else if (a > f * h) {
            r.tOut = -1e30;
        }
#if MARCH == 1
        g = max(g, dot(dp, V) / h);
        rad = min(rad, h / length(vec3(V.x, V.y * K, V.z)));
#endif
    }
    r.normal = normalize(vec3(entry.x, entry.y * K, entry.z));
    r.body = 0.0;
#if MARCH == 1
    r.body = (f - g) * rad * CELL_SIZE;
#endif
    return r;
}

// ---- view ---------------------------------------------------------------------------------------------
vec3 sky(vec3 rd) {
    return mix(vec3(0.80, 0.86, 0.95), vec3(0.35, 0.55, 0.85), clamp(rd.y * 1.5 + 0.3, 0.0, 1.0));
}

vec3 cellColor(ivec3 c) {
#if COLOR_BY_WEIGHT == 1
    float w = weightOf(c);
    vec3 lo = vec3(0.20, 0.40, 0.85);
    vec3 mid = vec3(0.78, 0.78, 0.74);
    vec3 hi = vec3(0.92, 0.45, 0.15);
    return w < 0.5 ? mix(lo, mid, 2.0 * w) : mix(mid, hi, 2.0 * w - 1.0);
#else
    vec3 h = rand3(c, SALT_TINT);
    return mix(vec3(0.52, 0.47, 0.42), vec3(0.72, 0.66, 0.56), h.x) * (0.85 + 0.25 * h.y);
#endif
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = (2.0 * fragCoord - iResolution.xy) / iResolution.y;
    // A full width of mouse travel is one turn. A full height is half a turn of pitch.
    vec2 m = (iMouse.x == 0.0 && iMouse.y == 0.0) ? 0.5 * iResolution.xy : iMouse.xy;
    float yaw = CAM_YAW + SPIN * iTime + 6.2831853 * (m.x / iResolution.x - 0.5);
    float pitch = clamp(CAM_PITCH + 3.1415927 * (m.y / iResolution.y - 0.5), -1.55, 1.55);
    vec3 target = vec3(0.5 * CELL_SIZE, LAYER_MIN_Y + 0.5 * CELL_HEIGHT, 0.5 * CELL_SIZE);
    vec3 ro = target + CAM_DIST * CELL_SIZE * vec3(cos(pitch) * cos(yaw), sin(pitch), cos(pitch) * sin(yaw));
    vec3 fw = normalize(target - ro);
    vec3 rt = normalize(cross(fw, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rt, fw);
    vec3 rd = normalize(fw * 1.6 + uv.x * rt + uv.y * up);
    float far = FAR * CELL_SIZE / 256.0;

    vec3 col = sky(rd);
    // The cells of row 0 lie inside this slab. Outside it, only rows -1 and 1 reach.
    float yLo = LAYER_MIN_Y - 1.5 * CELL_HEIGHT;
    float yHi = LAYER_MIN_Y + 2.5 * CELL_HEIGHT;
    float tA = (yHi - ro.y) / rd.y;
    float tB = (yLo - ro.y) / rd.y;
    bool inside = ro.y < yHi && ro.y > yLo;
    float t0 = rd.y == 0.0 ? (inside ? 0.0 : 1e9) : max(min(tA, tB), 0.0);
    float t1 = rd.y == 0.0 ? 1e9 : min(max(tA, tB), far);

    // The scaled ray in cell units. The parameter t stays in blocks.
    vec3 sro = vec3(ro.x, ro.y * K, ro.z) / CELL_SIZE;
    vec3 srd = vec3(rd.x, rd.y * K, rd.z) / CELL_SIZE;

    bool hit = false;
    float tHit = 0.0;
    ivec3 hitCell = ivec3(0);
    vec3 n = vec3(0.0, 1.0, 0.0);
    if (t1 > t0) {
        float t = t0;
#if MARCH == 0
        ivec3 c = ownerOf(sro + t * srd);
        for (int i = 0; i < MAX_CELLS; i++) {
            if (t > t1) break;
            Clip k = clipCell(c, sro, srd, t);
            if (c.y == 0 && !k.lost && k.tIn <= k.tOut && k.tOut > t) {
                hit = true;
                tHit = max(k.tIn, t);
                hitCell = c;
                n = k.normal;
                break;
            }
            // The exit wall names the next cell, so the march needs no new lookup.
            t = max(k.tExit, t + 1e-3);
            c = k.next;
        }
#else
        for (int i = 0; i < MAX_STEPS; i++) {
            if (t > t1) break;
            ivec3 c = ownerOf(sro + t * srd);
            Clip k = clipCell(c, sro, srd, t);
            bool island = c.y == 0 && !k.lost;
            if (island && k.body > 0.0) {
                hit = true;
                tHit = t;
                hitCell = c;
                n = k.normal;
                break;
            }
            float step = k.tExit - t;
            if (island) step = min(step, -k.body);
            t += max(step, 0.0) + STEP_EPS;
        }
#endif
    }
    if (hit) {
        vec3 sun = normalize(vec3(0.5, 0.8, 0.3));
        float dif = clamp(dot(n, sun), 0.0, 1.0);
        float bounce = clamp(-n.y, 0.0, 1.0) * 0.25 + clamp(dot(n, normalize(vec3(-0.6, 0.0, -0.4))), 0.0, 1.0) * 0.15;
        col = cellColor(hitCell) * (0.2 + 0.75 * dif + bounce) + 0.08 * sky(n);
        col = mix(col, sky(rd), 0.85 * clamp((tHit - 0.45 * far) / (0.55 * far), 0.0, 1.0));
    }
    fragColor = vec4(pow(col, vec3(0.4545)), 1.0);
}

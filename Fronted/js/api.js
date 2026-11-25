/* =====================================================================
   Conexión del frontend con el backend (Spring Boot, puerto 8080).
   - Si la página se abre desde el backend (http://localhost:8080) usa rutas relativas.
   - Si se abre con Live Server u otro puerto / como archivo, llama a http://localhost:8080.
   ===================================================================== */
const API_BASE = (location.port === "8080" ? "" : "http://localhost:8080") + "/api";

const Sesion = {
  guardar(datos, recordar) {
    const destino = recordar ? localStorage : sessionStorage;
    (recordar ? sessionStorage : localStorage).removeItem("milan_sesion");
    destino.setItem("milan_sesion", JSON.stringify(datos));
  },
  leer() {
    try {
      return JSON.parse(sessionStorage.getItem("milan_sesion") || localStorage.getItem("milan_sesion") || "null");
    } catch {
      return null;
    }
  },
  token() {
    const s = Sesion.leer();
    return s ? s.token : null;
  },
  borrar() {
    sessionStorage.removeItem("milan_sesion");
    localStorage.removeItem("milan_sesion");
  },
};

class ApiError extends Error {
  constructor(mensaje, estado, datos) {
    super(mensaje);
    this.estado = estado;
    this.datos = datos;
  }
}

/** Llama a la API. Lanza ApiError con el mensaje {"error": "..."} que devuelve el backend. */
async function api(ruta, { method = "GET", body, query } = {}) {
  let url = API_BASE + ruta;
  if (query) {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") params.append(k, v);
    });
    const qs = params.toString();
    if (qs) url += "?" + qs;
  }
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = Sesion.token();
  if (token) headers.Authorization = "Bearer " + token;

  let res;
  try {
    res = await fetch(url, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  } catch {
    throw new ApiError("No hay conexión con el servidor. ¿Está corriendo el backend en el puerto 8080?", 0);
  }

  if (res.status === 204) return null;
  const texto = await res.text();
  let datos = null;
  try {
    datos = texto ? JSON.parse(texto) : null;
  } catch {
    datos = texto;
  }

  if (!res.ok) {
    if (res.status === 401 && !ruta.startsWith("/auth/login") && !location.pathname.endsWith("index.html")) {
      Sesion.borrar();
      if (!/\/(index\.html)?$/.test(location.pathname)) location.href = "index.html";
    }
    let mensaje = (datos && datos.error) || `Error ${res.status}`;
    if (datos && datos.campos) {
      mensaje = Object.values(datos.campos).join(". ");
    }
    throw new ApiError(mensaje, res.status, datos);
  }
  return datos;
}

/* ---------- Utilidades de formato compartidas ---------- */
const fmt = {
  dinero(v) {
    const n = Number(v || 0);
    return "$ " + n.toLocaleString("es-CO", { maximumFractionDigits: 0 });
  },
  iniciales(texto) {
    return (texto || "?").split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join("") || "?";
  },
  hora(fechaHora) {
    return fechaHora ? String(fechaHora).substring(11, 16) : "";
  },
  /** Fecha local en formato aaaa-mm-dd (sin desfases de zona horaria). */
  iso(fecha) {
    const d = fecha instanceof Date ? fecha : new Date(fecha);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  },
  aFecha(iso) {
    const [a, m, d] = String(iso).substring(0, 10).split("-").map(Number);
    return new Date(a, m - 1, d);
  },
  fechaLarga(fecha) {
    const d = fecha instanceof Date ? fecha : fmt.aFecha(fecha);
    const t = d.toLocaleDateString("es-CO", { weekday: "long", day: "2-digit", month: "long" });
    return t.charAt(0).toUpperCase() + t.slice(1);
  },
  fechaCorta(fecha) {
    const d = fecha instanceof Date ? fecha : fmt.aFecha(fecha);
    const t = d.toLocaleDateString("es-CO", { weekday: "short", day: "2-digit", month: "long" });
    return t.charAt(0).toUpperCase() + t.slice(1);
  },
  fechaHoraRelativa(fechaHora) {
    if (!fechaHora) return "—";
    const dia = String(fechaHora).substring(0, 10);
    const hoy = fmt.iso(new Date());
    if (dia === hoy) return "Hoy, " + fmt.hora(fechaHora);
    const d = fmt.aFecha(dia);
    return d.toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" });
  },
  telefono(t) {
    const s = String(t || "").replace(/\D/g, "");
    return s.length === 10 ? `${s.slice(0, 3)} ${s.slice(3, 6)} ${s.slice(6)}` : t || "";
  },
  html(texto) {
    return String(texto ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  },
};

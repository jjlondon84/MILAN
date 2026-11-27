/* =====================================================================
   MILAN Barbería — panel conectado a la API REST (ver js/api.js)
   ===================================================================== */
const $ = (id) => document.getElementById(id);
const h = fmt.html;
const icon = (n, s = 20) => `<svg class="icon" width="${s}" height="${s}" aria-hidden="true"><use href="#i-${n}"/></svg>`;

if (!Sesion.token()) window.location.href = "index.html";

const screenMeta = {
  dashboard:  { eyebrow: "RESUMEN GENERAL",  title: "Buenos días",          subtitle: "Todo lo que necesitas para gestionar tu barbería hoy." },
  agenda:     { eyebrow: "GESTIÓN DE CITAS", title: "Agenda",               subtitle: "Organiza los turnos del equipo y evita cruces de horario." },
  personal:   { eyebrow: "EQUIPO",           title: "Gestión de personal",  subtitle: "Administra turnos, descansos, licencias y días libres." },
  servicios:  { eyebrow: "CATÁLOGO",         title: "Servicios",            subtitle: "Configura precios, duración y comisiones por servicio." },
  comisiones: { eyebrow: "LIQUIDACIÓN",      title: "Comisiones",           subtitle: "Consulta lo generado por cada barbero en el periodo." },
  clientes:   { eyebrow: "RELACIONES",       title: "Clientes",             subtitle: "Historial, preferencias y datos de contacto." },
  inventario: { eyebrow: "EXISTENCIAS",      title: "Inventario",           subtitle: "Controla productos e insumos antes de que se agoten." },
  caja:       { eyebrow: "CAJA Y VENTAS",    title: "Caja",                 subtitle: "Abre la jornada, cobra servicios y productos y haz el arqueo." },
  midia:      { eyebrow: "MI DÍA",           title: "Buenos días",          subtitle: "Tus citas de hoy, lo que llevas generado y tus propinas." },
  mihorario:  { eyebrow: "MI TURNO",         title: "Mi horario",           subtitle: "Tu horario semanal, descansos y días libres." },
};

/* Lo que ve cada rol. El backend también lo controla (403 si un barbero entra a una sección del administrador). */
const MENU_POR_ROL = {
  administrador: ["dashboard", "agenda", "personal", "servicios", "comisiones", "clientes", "inventario", "caja"],
  barbero: ["midia", "agenda", "mihorario", "comisiones", "clientes"],
};
const TEXTOS_BARBERO = {
  agenda: { title: "Mi agenda", subtitle: "Solo tus citas. Agenda, inicia y termina tus servicios." },
  comisiones: { eyebrow: "MI LIQUIDACIÓN", title: "Mis comisiones", subtitle: "Lo que generaste en el periodo: comisiones y propinas." },
  clientes: { subtitle: "Consulta preferencias e historial, y registra clientes nuevos." },
};
const esAdmin = () => !estado.usuario || estado.usuario.rol === "administrador";

const ESTADOS_CITA = {
  programada:  { label: "Pendiente",   cls: "status--warning", color: "gray" },
  confirmada:  { label: "Confirmada",  cls: "status--success", color: "blue" },
  en_atencion: { label: "En atención", cls: "status--active",  color: "purple" },
  terminada:   { label: "Atendida · por cobrar", cls: "status--warning", color: "green" },
  completada:  { label: "Completada",  cls: "status--neutral", color: "green" },
  cancelada:   { label: "Cancelada",   cls: "status--error",   color: "red" },
  no_asistio:  { label: "No asistió",  cls: "status--error",   color: "red" },
};
const DISPONIBILIDAD = {
  en_turno:       { label: "En turno",      cls: "status--success" },
  descanso:       { label: "En descanso",   cls: "status--warning" },
  fuera_de_turno: { label: "Fuera de turno", cls: "status--neutral" },
  ausente:        { label: "Ausente",       cls: "status--neutral" },
  sin_turno:      { label: "Sin turno",     cls: "status--neutral" },
  inactivo:       { label: "Inactivo",      cls: "status--error" },
};
const CATEGORIAS = { corte: "Corte", barba: "Barba", combo: "Combo", estetico: "Estética" };
const TIPOS_AUSENCIA = { licencia: "Licencia", dia_libre: "Día libre", incapacidad: "Incapacidad", vacaciones: "Vacaciones" };
const DIAS = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];

const estado = {
  usuario: null,
  pantalla: "dashboard",
  agendaFecha: new Date(),
  agendaVista: "dia",
  servicios: [],
  barberos: [],
  clientes: [],
  productos: [],
  caja: null,
};

const statusBadge = (cls, label) => `<span class="status ${cls}"><i></i>${h(label)}</span>`;
const estadoCita = (e) => ESTADOS_CITA[e] || { label: e, cls: "status--neutral", color: "gray" };
const nombre = (x) => (x ? `${x.nombres} ${x.apellidos}` : "");
const hhmm = (t) => (t ? String(t).substring(0, 5) : "");

/* ===================================================================== utilidades UI */
let toastTimer;
function toast(mensaje, tipo = "ok") {
  const t = $("toast");
  t.textContent = mensaje;
  t.className = "toast" + (tipo === "error" ? " error" : "");
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), 4200);
}

async function intentar(fn, mensajeOk) {
  try {
    const r = await fn();
    if (mensajeOk) toast(mensajeOk);
    return r;
  } catch (err) {
    toast(err.message, "error");
    throw err;
  }
}

function filaVacia(tbody, columnas, texto) {
  tbody.innerHTML = `<tr class="empty-row"><td colspan="${columnas}">${h(texto)}</td></tr>`;
}
function filaCargando(tbody, columnas) {
  tbody.innerHTML = `<tr class="loading-row"><td colspan="${columnas}"><span class="spinner"></span></td></tr>`;
}

/** Modal genérico de formulario. actions: [{label, cls, onClick(valores) -> Promise}] */
const modal = {
  abrir({ eyebrow = "", title = "", subtitle = "", html = "", actions = [], wide = false }) {
    document.querySelector("#form-modal .modal").classList.toggle("modal--wide", wide);
    $("form-eyebrow").textContent = eyebrow;
    $("form-title").textContent = title;
    $("form-subtitle").textContent = subtitle;
    $("form-subtitle").hidden = !subtitle;
    $("form-body").innerHTML = html;
    this.error("");
    const cont = $("form-actions");
    cont.innerHTML = "";
    actions.forEach((a) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn " + (a.cls || "btn--primary");
      b.textContent = a.label;
      b.addEventListener("click", async () => {
        if (!a.onClick) return modal.cerrar();
        this.error("");
        b.disabled = true;
        try {
          await a.onClick(modal.valores());
        } catch (err) {
          this.error(err.message);
        } finally {
          b.disabled = false;
        }
      });
      cont.appendChild(b);
    });
    $("form-modal").hidden = false;
    const primero = $("form-body").querySelector("input:not([type=hidden]):not([disabled]), select, textarea");
    if (primero) primero.focus();
  },
  cerrar() {
    $("form-modal").hidden = true;
  },
  error(texto) {
    $("form-error-text").textContent = texto || "";
    $("form-error").hidden = !texto;
  },
  valores() {
    const v = {};
    $("form-body").querySelectorAll("[name]").forEach((el) => {
      if (el.type === "checkbox") v[el.name] = el.checked;
      else if (el.type === "number") v[el.name] = el.value === "" ? null : Number(el.value);
      else v[el.name] = el.value.trim() === "" ? null : el.value.trim();
    });
    return v;
  },
};
$("form-close").addEventListener("click", () => modal.cerrar());
$("form-modal").addEventListener("click", (e) => {
  if (e.target.id === "form-modal") modal.cerrar();
});

function campo(label, name, { type = "text", value = "", full = false, required = false, attrs = "", options = null, placeholder = "" } = {}) {
  let control;
  if (options) {
    control = `<select name="${name}" ${attrs}>${options
      .map(([v, t]) => `<option value="${h(v)}" ${String(v) === String(value ?? "") ? "selected" : ""}>${h(t)}</option>`)
      .join("")}</select>`;
  } else if (type === "textarea") {
    control = `<textarea name="${name}" placeholder="${h(placeholder)}" ${attrs}>${h(value ?? "")}</textarea>`;
  } else if (type === "checkbox") {
    return `<label class="check-field ${full ? "full" : ""}"><input type="checkbox" name="${name}" ${value ? "checked" : ""} ${attrs}> ${h(label)}</label>`;
  } else {
    control = `<input type="${type}" name="${name}" value="${h(value ?? "")}" placeholder="${h(placeholder)}" ${attrs}>`;
  }
  return `<label class="simple-field ${full ? "full" : ""}"><span>${h(label)}${required ? " *" : ""}</span>${control}</label>`;
}

/** Filtro de texto de las barras "Buscar..." de cada tabla. */
function conectarBuscadorTabla(seccion) {
  const input = document.querySelector(`[data-content="${seccion}"] .search-field input`);
  if (!input) return;
  input.placeholder = `Buscar en ${screenMeta[seccion].title.toLowerCase()}...`;
  input.addEventListener("input", () => aplicarBusquedaTabla(seccion));
}
function aplicarBusquedaTabla(seccion) {
  const input = document.querySelector(`[data-content="${seccion}"] .search-field input`);
  if (!input) return;
  const q = input.value.trim().toLowerCase();
  document.querySelectorAll(`[data-content="${seccion}"] tbody tr`).forEach((tr) => {
    if (tr.classList.contains("empty-row") || tr.classList.contains("loading-row")) return;
    tr.hidden = q !== "" && !tr.textContent.toLowerCase().includes(q);
  });
}

/* ===================================================================== catálogos */
async function cargarCatalogos() {
  const admin = esAdmin();
  const [servicios, barberos, clientes, productos] = await Promise.all([
    api("/servicios"),
    api(admin ? "/barberos" : "/barberos/activos"),
    api("/clientes"),
    admin ? api("/productos") : Promise.resolve([]),
  ]);
  estado.servicios = servicios;
  estado.barberos = barberos;
  estado.clientes = clientes;
  estado.productos = productos;
}
const barberoPorId = (id) => estado.barberos.find((b) => b.idBarbero === id);
const clientePorId = (id) => estado.clientes.find((c) => c.idCliente === id);

/* ===================================================================== navegación */
const sidebar = $("sidebar");
const backdrop = $("nav-backdrop");

function toggleMobileNav(open) {
  sidebar.classList.toggle("open", open);
  backdrop.classList.toggle("show", open);
}

const cargadores = {
  dashboard: cargarDashboard,
  agenda: cargarAgenda,
  personal: cargarPersonal,
  servicios: cargarServicios,
  comisiones: cargarComisiones,
  clientes: cargarClientes,
  inventario: cargarInventario,
  caja: cargarCaja,
  midia: cargarMiDia,
  mihorario: cargarMiHorario,
};

function navigate(id) {
  if (estado.usuario && !MENU_POR_ROL[esAdmin() ? "administrador" : "barbero"].includes(id)) {
    id = esAdmin() ? "dashboard" : "midia";
  }
  const meta = { ...screenMeta[id], ...(!esAdmin() && TEXTOS_BARBERO[id] ? TEXTOS_BARBERO[id] : {}) };
  estado.pantalla = id;
  document.querySelectorAll("[data-content]").forEach((s) => (s.hidden = s.dataset.content !== id));
  document.querySelectorAll(".sidebar nav button").forEach((b) => b.classList.toggle("active", b.dataset.screen === id));
  $("page-eyebrow").textContent = meta.eyebrow;
  $("page-title").textContent = id === "dashboard" || id === "midia" ? saludo() : meta.title;
  $("page-subtitle").textContent = meta.subtitle;
  $("today").hidden = id !== "dashboard";
  toggleMobileNav(false);
  cargadores[id]().catch((err) => toast(err.message, "error"));
}

function saludo() {
  const hora = new Date().getHours();
  const base = hora < 12 ? "Buenos días" : hora < 19 ? "Buenas tardes" : "Buenas noches";
  const nombreCorto = estado.usuario ? (estado.usuario.nombreCompleto || estado.usuario.nombreUsuario).split(" ")[0] : "";
  return nombreCorto ? `${base}, ${nombreCorto}` : base;
}

document.querySelectorAll("[data-screen]").forEach((b) => b.addEventListener("click", () => navigate(b.dataset.screen)));
document.querySelectorAll("[data-go]").forEach((b) => b.addEventListener("click", () => navigate(b.dataset.go)));
document.querySelectorAll("[data-quick]").forEach((b) =>
  b.addEventListener("click", () => (b.dataset.quick === "cita" ? abrirDrawerCita() : formCliente())),
);
$("open-nav").addEventListener("click", () => toggleMobileNav(true));
backdrop.addEventListener("click", () => toggleMobileNav(false));

$("logout").addEventListener("click", async () => {
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {
    /* la sesión pudo haber vencido */
  }
  Sesion.borrar();
  window.location.href = "index.html";
});

/* ===================================================================== perfil y usuarios */
async function cargarPerfil() {
  const u = await api("/auth/me");
  estado.usuario = u;
  const nombreVisible = u.nombreCompleto || u.nombreUsuario;
  $("profile-name").textContent = nombreVisible;
  $("profile-role").textContent = u.rol ? u.rol.charAt(0).toUpperCase() + u.rol.slice(1) : "";
  $("profile-avatar").textContent = fmt.iniciales(nombreVisible);
  aplicarMenuPorRol();
  if (estado.pantalla === "dashboard") $("page-title").textContent = saludo();
}

function aplicarMenuPorRol() {
  const visibles = MENU_POR_ROL[esAdmin() ? "administrador" : "barbero"];
  document.querySelectorAll(".sidebar nav button").forEach((b) => (b.hidden = !visibles.includes(b.dataset.screen)));
  document.querySelectorAll(".sidebar nav button").forEach((b) => {
    if (!esAdmin() && TEXTOS_BARBERO[b.dataset.screen]?.title) b.querySelector("span").textContent = TEXTOS_BARBERO[b.dataset.screen].title;
  });
  $("global-search-input").placeholder = esAdmin() ? "Buscar cliente, barbero o producto..." : "Buscar cliente...";
}

$("profile").addEventListener("click", () => {
  const u = estado.usuario || {};
  const esAdmin = u.rol === "administrador";
  modal.abrir({
    eyebrow: "MI CUENTA",
    title: u.nombreCompleto || u.nombreUsuario || "Mi cuenta",
    subtitle: `Usuario: ${u.nombreUsuario || ""} · ${u.rol || ""}${u.email ? " · " + u.email : ""}`,
    html: `<p class="form-section-title">Mi nombre</p>
      <div class="form-grid">
        ${campo("Nombre que se muestra", "nombreCompleto", { value: u.nombreCompleto || "", full: true, placeholder: "Ej: Juan Londoño" })}
      </div>
      <p class="form-section-title">Cambiar contraseña</p>
      <div class="form-grid">
        ${campo("Contraseña actual", "contrasenaActual", { type: "password", full: true })}
        ${campo("Nueva contraseña", "nuevaContrasena", { type: "password", placeholder: "Mínimo 6 caracteres" })}
        ${campo("Repite la nueva", "repetir", { type: "password" })}
      </div>`,
    actions: [
      ...(esAdmin ? [{ label: "Gestionar usuarios", cls: "btn--secondary", onClick: () => gestionarUsuarios() }] : []),
      {
        label: "Guardar nombre",
        cls: "btn--secondary",
        onClick: async (v) => {
          if (!v.nombreCompleto || v.nombreCompleto.length < 3) throw new Error("Escribe tu nombre (mínimo 3 letras)");
          await api("/auth/perfil", { method: "PUT", body: { nombreCompleto: v.nombreCompleto } });
          await cargarPerfil();
          if (["dashboard", "midia"].includes(estado.pantalla)) $("page-title").textContent = saludo();
          modal.cerrar();
          toast("Nombre actualizado");
        },
      },
      {
        label: "Guardar contraseña",
        onClick: async (v) => {
          if (!v.contrasenaActual || !v.nuevaContrasena) throw new Error("Completa los campos de contraseña");
          if (v.nuevaContrasena !== v.repetir) throw new Error("Las contraseñas nuevas no coinciden");
          await api("/auth/contrasena", { method: "PUT", body: { contrasenaActual: v.contrasenaActual, nuevaContrasena: v.nuevaContrasena } });
          modal.cerrar();
          toast("Contraseña actualizada");
        },
      },
    ],
  });
});

async function gestionarUsuarios() {
  const [usuarios, roles, fichas] = await Promise.all([api("/usuarios"), api("/roles"), api("/barberos")]);
  const selectFicha = (u) => {
    if (u.rol !== "barbero") return '<span class="cell-sub">—</span>';
    const ops = fichas
      .filter((b) => !b.idUsuario || b.idUsuario === u.idUsuario)
      .map((b) => `<option value="${b.idBarbero}" ${b.idBarbero === u.idBarbero ? "selected" : ""}>${h(nombre(b))}</option>`)
      .join("");
    return `<select class="filter-select filter-select--sm" data-ficha-user="${u.idUsuario}"><option value="0">Sin ficha</option>${ops}</select>`;
  };
  const filas = usuarios
    .map(
      (u) => `<tr><td>${h(u.nombreUsuario)}<span class="cell-sub">${h(u.nombreCompleto || "")}</span></td><td>${h(u.email || "—")}</td><td>${h(u.rol)}</td><td>${selectFicha(u)}</td>
      <td>${u.activo ? statusBadge("status--success", "Activo") : statusBadge("status--neutral", "Inactivo")}</td>
      <td><button class="text-link" data-toggle-user="${u.idUsuario}" data-activo="${u.activo}">${u.activo ? "Desactivar" : "Activar"}</button></td></tr>`,
    )
    .join("");
  modal.abrir({
    eyebrow: "ADMINISTRACIÓN",
    title: "Usuarios del sistema",
    wide: true,
    subtitle: "Las contraseñas se guardan con bcrypt + SHA-256 y los correos cifrados con AES.",
    html: `<div class="table-wrap"><table class="mini-table"><thead><tr><th>Usuario</th><th>Correo</th><th>Rol</th><th>Ficha de barbero</th><th>Estado</th><th></th></tr></thead><tbody>${filas}</tbody></table></div>
      <p class="form-section-title">Nuevo usuario</p>
      <div class="form-grid">
        ${campo("Usuario", "nombreUsuario", { required: true })}
        ${campo("Nombre completo", "nombreCompleto")}
        ${campo("Correo", "email", { type: "email" })}
        ${campo("Rol", "idRol", { options: roles.map((r) => [r.idRol, r.nombre]) })}
        ${campo("Contraseña", "contrasena", { type: "password", full: true, required: true, placeholder: "Mínimo 6 caracteres" })}
      </div>`,
    actions: [
      { label: "Cerrar", cls: "btn--secondary" },
      {
        label: "Crear usuario",
        onClick: async (v) => {
          await api("/usuarios", { method: "POST", body: { ...v, idRol: Number(v.idRol) } });
          toast("Usuario creado");
          gestionarUsuarios();
        },
      },
    ],
  });
  $("form-body").querySelectorAll("[data-ficha-user]").forEach((sel) =>
    sel.addEventListener("change", async () => {
      try {
        await api(`/usuarios/${sel.dataset.fichaUser}`, { method: "PUT", body: { idBarbero: Number(sel.value) } });
        toast(Number(sel.value) ? "Cuenta vinculada a la ficha" : "Cuenta desvinculada");
        gestionarUsuarios();
      } catch (err) {
        modal.error(err.message);
      }
    }),
  );
  $("form-body").querySelectorAll("[data-toggle-user]").forEach((b) =>
    b.addEventListener("click", async () => {
      try {
        await api(`/usuarios/${b.dataset.toggleUser}`, { method: "PUT", body: { activo: b.dataset.activo !== "true" } });
        gestionarUsuarios();
      } catch (err) {
        modal.error(err.message);
      }
    }),
  );
}

/* ===================================================================== búsqueda global */
let busquedaTimer;
const searchInput = $("global-search-input");
const searchResults = $("search-results");
[0, 300, 1200].forEach((ms) => setTimeout(() => {
  if (document.activeElement !== searchInput) searchInput.value = "";
}, ms));
searchInput.addEventListener("input", () => {
  clearTimeout(busquedaTimer);
  busquedaTimer = setTimeout(buscarGlobal, 250);
});
document.addEventListener("keydown", (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    searchInput.focus();
  }
  if (e.key === "Escape") {
    searchResults.hidden = true;
    $("notification-panel").hidden = true;
  }
});
document.addEventListener("click", (e) => {
  if (!e.target.closest("#global-search")) searchResults.hidden = true;
  if (!e.target.closest("#notification-panel") && !e.target.closest("#notification-btn")) $("notification-panel").hidden = true;
});

async function buscarGlobal() {
  const q = searchInput.value.trim();
  if (q.length < 2) {
    searchResults.hidden = true;
    return;
  }
  try {
    const r = await api("/buscar", { query: { q } });
    const grupo = (titulo, items, render) =>
      items.length ? `<h4>${titulo}</h4>` + items.map(render).join("") : "";
    const html =
      grupo("CLIENTES", r.clientes, (c) => `<button data-res="cliente" data-id="${c.idCliente}"><span>${h(nombre(c))}</span><small>${h(fmt.telefono(c.telefono))}</small></button>`) +
      grupo("BARBEROS", r.barberos, (b) => `<button data-res="barbero" data-id="${b.idBarbero}"><span>${h(nombre(b))}</span><small>${h(b.estado)}</small></button>`) +
      grupo("PRODUCTOS", r.productos, (p) => `<button data-res="producto" data-id="${p.idProducto}"><span>${h(p.nombre)}</span><small>${p.stockActual} und.</small></button>`);
    searchResults.innerHTML = html || `<div class="empty-state">Sin resultados para “${h(q)}”</div>`;
    searchResults.hidden = false;
    searchResults.querySelectorAll("[data-res]").forEach((b) =>
      b.addEventListener("click", async () => {
        searchResults.hidden = true;
        searchInput.value = "";
        const id = Number(b.dataset.id);
        if (b.dataset.res === "cliente") {
          navigate("clientes");
          detalleCliente(id);
        } else if (b.dataset.res === "barbero") {
          navigate("personal");
          formBarbero(await api(`/barberos/${id}`));
        } else {
          navigate("inventario");
          formProducto(await api(`/productos/${id}`));
        }
      }),
    );
  } catch (err) {
    toast(err.message, "error");
  }
}

/* ===================================================================== notificaciones */
let notificaciones = [];
$("notification-btn").addEventListener("click", () => {
  const p = $("notification-panel");
  p.innerHTML =
    "<h4>Notificaciones</h4>" +
    (notificaciones.length
      ? notificaciones.map((n) => `<button data-go-n="${n.ir}"><span>${h(n.texto)}</span><small>${h(n.detalle || "")}</small></button>`).join("")
      : '<div class="empty-state">No hay alertas pendientes</div>');
  p.hidden = !p.hidden;
  p.querySelectorAll("[data-go-n]").forEach((b) =>
    b.addEventListener("click", () => {
      p.hidden = true;
      navigate(b.dataset.goN);
    }),
  );
});

function actualizarNotificaciones(d) {
  notificaciones = [];
  (d.productosStockBajo || []).forEach((p) =>
    notificaciones.push({ texto: `Stock bajo: ${p.nombre}`, detalle: `${p.stock_actual} / mín. ${p.stock_minimo}`, ir: "inventario" }),
  );
  if (d.citasProximas > 0) notificaciones.push({ texto: `${d.citasProximas} cita(s) próximas hoy`, ir: "agenda" });
  if (!d.cajaAbierta) notificaciones.push({ texto: "La caja está cerrada", detalle: "Ábrela para poder cobrar", ir: "caja" });
  $("notification-dot").hidden = notificaciones.length === 0;
}

/* ===================================================================== DASHBOARD */
async function cargarDashboard() {
  $("today-label").textContent = fmt.fechaCorta(new Date());
  const d = await api("/dashboard");
  actualizarNotificaciones(d);

  const variacion = d.variacionIngresosPorcentaje;
  const textoVariacion =
    variacion === null || variacion === undefined
      ? `Ayer: ${fmt.dinero(d.ingresosDiaAnterior)}`
      : `${variacion >= 0 ? "+" : ""}${Number(variacion).toLocaleString("es-CO")}% vs. ayer`;
  const tarjeta = (iconId, cls, titulo, valor, pie) =>
    `<article class="stat-card"><div class="stat-icon ${cls}">${icon(iconId)}</div><div><span>${titulo}</span><strong>${valor}</strong><small>${h(pie)}</small></div></article>`;
  $("dash-stats").innerHTML =
    tarjeta("calendar", "", "CITAS DE HOY", d.citasDelDia, `${d.citasProximas} próximas`) +
    tarjeta("money", "success", "INGRESOS DEL DÍA", fmt.dinero(d.ingresosDelDia), textoVariacion) +
    tarjeta("warning", "warning", "STOCK BAJO", d.stockBajo, d.stockBajo ? "Requieren atención" : "Todo en orden") +
    tarjeta("personal", "", "BARBEROS EN TURNO", `${d.barberosEnTurno} / ${d.barberosActivos}`, d.barberosEnTurno ? "Capacidad disponible" : "Nadie en turno hoy");

  const lista = $("dash-appointments");
  const citas = d.citas || [];
  if (!citas.length) {
    lista.innerHTML = '<div class="empty-state">No hay citas para hoy. Agenda una desde “Nueva cita”.</div>';
  } else {
    lista.innerHTML = citas
      .map((c) => {
        const e = estadoCita(c.estado);
        return `<div class="appointment-row"><div class="time"><strong>${fmt.hora(c.fechaHoraInicio)}</strong><span>HOY</span></div><div class="avatar">${h(fmt.iniciales(c.cliente))}</div><div class="appointment-main"><strong>${h(c.cliente)}</strong><span>${h(c.servicios.map((s) => s.nombre).join(" + "))} · ${h(c.barbero.split(" ")[0])}</span></div>${statusBadge(e.cls, e.label)}<button class="row-action" aria-label="Ver detalle" data-cita="${c.idCita}">${icon("chevron", 18)}</button></div>`;
      })
      .join("");
    lista.querySelectorAll("[data-cita]").forEach((b) => b.addEventListener("click", () => detalleCita(Number(b.dataset.cita))));
  }

  const stock = d.productosStockBajo || [];
  $("dash-stock-badge").textContent = `${stock.length} ${stock.length === 1 ? "alerta" : "alertas"}`;
  $("dash-stock").innerHTML = stock.length
    ? stock
        .slice(0, 4)
        .map((p, i) => {
          const pct = p.stock_minimo ? Math.round((p.stock_actual / p.stock_minimo) * 100) : 0;
          return `<div class="stock-item"><span class="product-dot ${i % 2 ? "gray" : "blue"}"></span><div><strong>${h(p.nombre)}</strong><small>${p.stock_actual} unidades · mínimo ${p.stock_minimo}</small></div><span>${pct}%</span></div>`;
        })
        .join("")
    : '<div class="empty-state">Sin alertas de inventario</div>';
}

/* ===================================================================== CITAS (detalle y acciones) */
async function detalleCita(idCita) {
  const c = await intentar(() => api(`/citas/${idCita}`));
  const e = estadoCita(c.estado);
  const pendiente = ["programada", "confirmada"].includes(c.estado);
  const acciones = [{ label: "Cerrar", cls: "btn--secondary" }];
  const accion = (label, cls, ruta, ok) => ({
    label,
    cls,
    onClick: async () => {
      await api(`/citas/${idCita}/${ruta}`, { method: "PATCH" });
      modal.cerrar();
      toast(ok);
      refrescarPantalla();
    },
  });
  if (pendiente) {
    acciones.push(accion("Cancelar cita", "btn--danger", "cancelar", "Cita cancelada"));
    acciones.push(accion("No asistió", "btn--secondary", "no-asistio", "Marcada como no asistió"));
    acciones.push({ label: "Reprogramar", cls: "btn--secondary", onClick: async () => (modal.cerrar(), abrirDrawerCita(c)) });
  }
  if (c.estado === "programada") acciones.push(accion("Confirmar", "btn--secondary", "confirmar", "Cita confirmada"));
  if (pendiente) acciones.push(accion("Iniciar atención", "btn--secondary", "iniciar", "Atención iniciada"));
  if (["programada", "confirmada", "en_atencion"].includes(c.estado)) {
    acciones.push({ label: "Terminé", cls: "btn--primary", onClick: async () => formTerminar(c) });
  }
  if (esAdmin() && ["programada", "confirmada", "en_atencion", "terminada"].includes(c.estado)) {
    acciones.push({ label: "Cobrar", cls: "btn--success", onClick: async () => (modal.cerrar(), formCobro(c)) });
  }
  modal.abrir({
    eyebrow: "DETALLE DE LA CITA",
    title: c.cliente,
    subtitle: `${fmt.fechaLarga(c.fechaHoraInicio.substring(0, 10))} · ${fmt.hora(c.fechaHoraInicio)} – ${fmt.hora(c.fechaHoraFin)}`,
    html: `<div class="detail-list">
      <div><span>Estado</span><strong>${statusBadge(e.cls, e.label)}</strong></div>
      <div><span>Barbero</span><strong>${h(c.barbero)}</strong></div>
      <div><span>Servicios</span><strong>${c.servicios.map((s) => `${h(s.nombre)} (${fmt.dinero(s.precio)})`).join("<br>")}</strong></div>
      <div><span>Total</span><strong>${fmt.dinero(c.total)}</strong></div>
      ${c.fechaHoraFinReal ? `<div><span>Terminó</span><strong>${fmt.hora(c.fechaHoraFinReal)}${c.minutosRetraso ? ` (+${c.minutosRetraso} min)` : ""}</strong></div>` : ""}
      ${Number(c.propina) > 0 ? `<div><span>Propina al barbero</span><strong>${fmt.dinero(c.propina)}</strong></div>` : ""}
      ${c.observaciones ? `<div><span>Observaciones</span><strong>${h(c.observaciones)}</strong></div>` : ""}
    </div>`,
    actions: acciones,
  });
}

/* ===================================================================== MI DÍA (barbero) */
$("midia-agendar").addEventListener("click", () => abrirDrawerCita());

async function cargarMiDia() {
  $("today-label").textContent = fmt.fechaCorta(new Date());
  let d;
  try {
    d = await api("/mi-panel");
  } catch (err) {
    $("midia-stats").innerHTML = "";
    $("midia-actual").innerHTML = `<div class="empty-state">${h(err.message)}<br>Pídele al administrador que vincule tu cuenta con tu ficha de barbero.</div>`;
    $("midia-citas").innerHTML = "";
    return;
  }
  const tarjeta = (iconId, cls, titulo, valor, pie) =>
    `<article class="stat-card"><div class="stat-icon ${cls}">${icon(iconId)}</div><div><span>${titulo}</span><strong>${valor}</strong><small>${h(pie)}</small></div></article>`;
  const t = d.barbero && d.barbero.turnoDelDia;
  $("midia-stats").innerHTML =
    tarjeta("calendar", "", "MIS CITAS DE HOY", d.citasDelDia, `${d.atendidas} atendidas`) +
    tarjeta("trend", "success", "GENERADO HOY", fmt.dinero(d.generadoHoy), "Servicios que atendiste") +
    tarjeta("money", "success", "MIS PROPINAS HOY", fmt.dinero(d.propinasHoy), "100% para ti") +
    tarjeta("clock", d.minutosRetrasoHoy > 0 ? "warning" : "", "MI TURNO", t ? `${hhmm(t.horaInicio)}–${hhmm(t.horaFin)}` : "Libre",
      d.minutosRetrasoHoy > 0 ? `Retraso acumulado: ${d.minutosRetrasoHoy} min` : t && t.descansoInicio ? `Descanso ${hhmm(t.descansoInicio)} – ${hhmm(t.descansoFin)}` : "Sin retrasos");

  const pendientes = d.citas.filter((c) => ["programada", "confirmada", "en_atencion"].includes(c.estado));
  const actual = d.enAtencion || pendientes[0];
  const panel = $("midia-actual");
  if (!actual) {
    panel.innerHTML = `<div class="panel-heading"><div><span class="eyebrow">AHORA</span><h3>Sin citas pendientes</h3></div></div><div class="empty-state">No tienes más citas hoy. Puedes agendar una con “+ Agendar”.</div>`;
  } else {
    const atendiendo = actual.estado === "en_atencion";
    const e = estadoCita(actual.estado);
    panel.innerHTML = `<div class="panel-heading"><div><span class="eyebrow">${atendiendo ? "ATENDIENDO AHORA" : "SIGUIENTE CITA"}</span><h3>${h(actual.cliente)}</h3></div>${statusBadge(e.cls, e.label)}</div>
      <div class="atendiendo-body">
        <div class="atendiendo-hora"><strong>${fmt.hora(actual.fechaHoraInicio)}</strong><span>hasta ${fmt.hora(actual.fechaHoraFin)}</span></div>
        <div><strong>${h(actual.servicios.map((s) => s.nombre).join(" + "))}</strong><span>${fmt.dinero(actual.total)}${actual.observaciones ? " · " + h(actual.observaciones) : ""}</span></div>
      </div>
      <div class="atendiendo-acciones">
        ${atendiendo ? "" : '<button class="btn btn--secondary" id="midia-iniciar">Iniciar atención</button>'}
        <button class="btn btn--primary" id="midia-termine">Terminé ${icon("check", 18)}</button>
      </div>`;
    if (!atendiendo)
      $("midia-iniciar").addEventListener("click", () =>
        intentar(() => api(`/citas/${actual.idCita}/iniciar`, { method: "PATCH" }), "Atención iniciada").then(refrescarPantalla),
      );
    $("midia-termine").addEventListener("click", () => formTerminar(actual));
  }

  const lista = $("midia-citas");
  lista.innerHTML = d.citas.length
    ? d.citas
        .map((c) => {
          const e = estadoCita(c.estado);
          const extra = Number(c.propina) > 0 ? ` · propina ${fmt.dinero(c.propina)}` : "";
          return `<div class="appointment-row"><div class="time"><strong>${fmt.hora(c.fechaHoraInicio)}</strong><span>${fmt.hora(c.fechaHoraFin)}</span></div><div class="avatar">${h(fmt.iniciales(c.cliente))}</div><div class="appointment-main"><strong>${h(c.cliente)}</strong><span>${h(c.servicios.map((s) => s.nombre).join(" + "))}${h(extra)}</span></div>${statusBadge(e.cls, e.label)}<button class="row-action" aria-label="Ver detalle" data-cita="${c.idCita}">${icon("chevron", 18)}</button></div>`;
        })
        .join("")
    : '<div class="empty-state">No tienes citas hoy.</div>';
  lista.querySelectorAll("[data-cita]").forEach((b) => b.addEventListener("click", () => detalleCita(Number(b.dataset.cita))));

  notificaciones = pendientes.slice(0, 3).map((c) => ({ texto: `${fmt.hora(c.fechaHoraInicio)} · ${c.cliente}`, detalle: c.servicios.map((s) => s.nombre).join(" + "), ir: "midia" }));
  $("notification-dot").hidden = notificaciones.length === 0;
}

/* ===================================================================== MI HORARIO (solo lectura) */
async function cargarMiHorario() {
  const turnosBody = $("mihorario-turnos");
  const ausBody = $("mihorario-ausencias");
  filaCargando(turnosBody, 4);
  filaCargando(ausBody, 4);
  const yo = (await api("/barberos/resumen"))[0];
  if (!yo) {
    filaVacia(turnosBody, 4, "Tu cuenta aún no está vinculada a una ficha de barbero. Habla con el administrador.");
    return filaVacia(ausBody, 4, "—");
  }
  const [turnos, ausencias] = await Promise.all([api(`/turnos/barbero/${yo.idBarbero}`), api(`/ausencias/barbero/${yo.idBarbero}`)]);
  const hoyDia = ((new Date().getDay() + 6) % 7) + 1;
  turnosBody.innerHTML = DIAS.slice(1)
    .map((dia, i) => {
      const t = turnos.find((x) => x.diaSemana === i + 1);
      const marca = i + 1 === hoyDia ? ' class="row-today"' : "";
      return t
        ? `<tr${marca}><td><strong>${dia}</strong>${i + 1 === hoyDia ? '<span class="cell-sub">Hoy</span>' : ""}</td><td>${hhmm(t.horaInicio)}</td><td>${hhmm(t.horaFin)}</td><td>${t.descansoInicio ? `${hhmm(t.descansoInicio)} – ${hhmm(t.descansoFin)}` : "—"}</td></tr>`
        : `<tr${marca}><td><strong>${dia}</strong></td><td colspan="3"><span class="cell-sub">Libre</span></td></tr>`;
    })
    .join("");
  const hoy = fmt.iso(new Date());
  const vigentes = ausencias.filter((a) => a.fechaFin >= hoy).sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
  if (!vigentes.length) filaVacia(ausBody, 4, "No tienes ausencias próximas");
  else
    ausBody.innerHTML = vigentes
      .map((a) => `<tr><td>${h(TIPOS_AUSENCIA[a.tipo] || a.tipo)}</td><td>${h(fmt.fechaCorta(a.fechaInicio))}</td><td>${h(fmt.fechaCorta(a.fechaFin))}</td><td>${h(a.motivo || "—")}</td></tr>`)
      .join("");
}

/* ===================================================================== TERMINÉ: cierre de la cita + propina */
const horaLocal = (d) => `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;

async function formTerminar(c) {
  const dia = c.fechaHoraInicio.substring(0, 10);
  const ahora = new Date();
  const horaFin = fmt.iso(ahora) === dia ? horaLocal(ahora) : fmt.hora(c.fechaHoraFin);
  modal.abrir({
    eyebrow: "TERMINÉ",
    title: `Cerrar la cita de ${c.cliente}`,
    subtitle: `${c.servicios.map((s) => s.nombre).join(" + ")} · programada ${fmt.hora(c.fechaHoraInicio)} – ${fmt.hora(c.fechaHoraFin)}`,
    html: `<div class="form-grid">
        ${campo("¿A qué hora terminaste?", "horaFin", { type: "time", value: horaFin, required: true })}
        ${campo("Propina que te dieron", "propina", { type: "number", value: 0, attrs: 'min="0" step="500"', placeholder: "0" })}
      </div>
      <div class="propina-rapida" id="propina-rapida">
        <button type="button" class="chip selected" data-p="0">Sin propina</button>
        <button type="button" class="chip" data-p="2000">$2.000</button>
        <button type="button" class="chip" data-p="5000">$5.000</button>
        <button type="button" class="chip" data-p="10000">$10.000</button>
      </div>
      <p class="form-hint">La propina es 100% tuya y sale aparte en tu liquidación. Si te dieron la propina en caja, la registra quien cobra.</p>
      <div id="terminar-preview" class="terminar-preview"></div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: "Terminé",
        cls: "btn--primary",
        onClick: async (v) => {
          if (!v.horaFin) throw new Error("Indica la hora en que terminaste");
          if (v.propina !== null && v.propina < 0) throw new Error("La propina no puede ser negativa");
          const r = await api(`/citas/${c.idCita}/terminar`, {
            method: "POST",
            body: { propina: v.propina || 0, fechaHoraFinReal: `${dia}T${v.horaFin}:00` },
          });
          modal.cerrar();
          const corridas = r.citasCorridas.length;
          toast(
            `Cita terminada${Number(v.propina) > 0 ? ` · propina ${fmt.dinero(v.propina)}` : ""}` +
              (corridas ? ` · se corrieron ${corridas} cita(s) siguiente(s)` : ""),
          );
          refrescarPantalla();
        },
      },
    ],
  });
  const body = $("form-body");
  const inpPropina = body.querySelector('[name="propina"]');
  const inpHora = body.querySelector('[name="horaFin"]');
  const chips = body.querySelectorAll("#propina-rapida [data-p]");
  const marcarChip = () => chips.forEach((b) => b.classList.toggle("selected", Number(b.dataset.p) === Number(inpPropina.value || 0)));
  chips.forEach((b) =>
    b.addEventListener("click", () => {
      inpPropina.value = b.dataset.p;
      marcarChip();
    }),
  );
  inpPropina.addEventListener("input", marcarChip);

  const preview = async () => {
    const cont = $("terminar-preview");
    if (!cont || !inpHora.value) return;
    try {
      const r = await api(`/citas/${c.idCita}/terminar`, { query: { fechaHoraFinReal: `${dia}T${inpHora.value}:00` } });
      const retraso = r.minutosRetraso > 0
        ? `<div class="preview-line warning"><span>Vas <strong>${r.minutosRetraso} min</strong> tarde respecto a lo programado.</span></div>`
        : `<div class="preview-line ok"><span>Terminaste a tiempo.</span></div>`;
      const corridas = r.citasCorridas.length
        ? `<p class="form-section-title">Se correrán estas citas</p>` +
          r.citasCorridas
            .map((x) => `<div class="preview-line${x.fueraDeTurno ? " warning" : ""}"><span>${h(x.cliente)}</span><span>${fmt.hora(x.inicioAntes)} → <strong>${fmt.hora(x.inicioDespues)}</strong>${x.fueraDeTurno ? " · queda fuera de tu turno o en tu descanso" : ""}</span></div>`)
            .join("")
        : r.minutosRetraso > 0 ? '<div class="preview-line"><span>No se cruza con tu siguiente cita; nada se corre.</span></div>' : "";
      cont.innerHTML = retraso + corridas;
    } catch (err) {
      cont.innerHTML = `<div class="preview-line warning"><span>${h(err.message)}</span></div>`;
    }
  };
  inpHora.addEventListener("change", preview);
  preview();
}

function refrescarPantalla() {
  cargadores[estado.pantalla]().catch((err) => toast(err.message, "error"));
}

/* ---------- Drawer: nueva cita / reprogramar ---------- */
const drawer = $("drawer");
let citaEnEdicion = null;
let serviciosElegidos = new Set();

const closeDrawer = () => (drawer.hidden = true);
$("new-appointment").addEventListener("click", () => abrirDrawerCita());
$("close-drawer").addEventListener("click", closeDrawer);
$("cancel-drawer").addEventListener("click", closeDrawer);

async function abrirDrawerCita(cita = null) {
  await cargarCatalogos();
  citaEnEdicion = cita;
  $("drawer-title").textContent = cita ? "Reprogramar cita" : "Nueva cita";
  $("confirm-appointment").textContent = cita ? "Guardar cambios" : "Confirmar cita";

  const clientes = [...estado.clientes].sort((a, b) => nombre(a).localeCompare(nombre(b)));
  $("cita-cliente").innerHTML = clientes.length
    ? clientes.map((c) => `<option value="${c.idCliente}">${h(nombre(c))} · ${h(fmt.telefono(c.telefono))}</option>`).join("")
    : '<option value="">Primero registra un cliente</option>';
  $("cita-cliente").disabled = !!cita;

  const activos = estado.barberos.filter((b) => b.estado === "activo");
  $("cita-barbero").innerHTML = activos.length
    ? activos.map((b) => `<option value="${b.idBarbero}">${h(nombre(b))}</option>`).join("")
    : '<option value="">No hay barberos activos</option>';

  serviciosElegidos = new Set(cita ? cita.servicios.map((s) => s.idServicio) : []);
  const servicios = estado.servicios.filter((s) => s.activo);
  if (!cita && !serviciosElegidos.size && servicios.length) serviciosElegidos.add(servicios[0].idServicio);
  const chips = $("cita-servicios");
  chips.innerHTML = servicios
    .map((s) => `<button type="button" class="chip ${serviciosElegidos.has(s.idServicio) ? "selected" : ""}" data-servicio="${s.idServicio}" ${cita ? "disabled" : ""}>${h(s.nombre)} · ${s.duracionMin} min</button>`)
    .join("");
  chips.querySelectorAll("[data-servicio]").forEach((b) =>
    b.addEventListener("click", () => {
      const id = Number(b.dataset.servicio);
      serviciosElegidos.has(id) ? serviciosElegidos.delete(id) : serviciosElegidos.add(id);
      b.classList.toggle("selected");
      cargarHorarios();
    }),
  );

  if (cita) {
    $("cita-cliente").value = cita.idCliente;
    $("cita-barbero").value = cita.idBarbero;
    $("cita-fecha").value = cita.fechaHoraInicio.substring(0, 10);
    $("cita-obs").value = cita.observaciones || "";
  } else {
    const base = estado.pantalla === "agenda" ? estado.agendaFecha : new Date();
    $("cita-fecha").value = fmt.iso(base < new Date() ? new Date() : base);
    $("cita-obs").value = "";
  }
  $("cita-fecha").min = fmt.iso(new Date());
  drawer.hidden = false;
  await cargarHorarios();
}

["cita-barbero", "cita-fecha"].forEach((id) => $(id).addEventListener("change", cargarHorarios));
$("time-select").addEventListener("change", checkTime);

async function cargarHorarios() {
  const select = $("time-select");
  const idBarbero = $("cita-barbero").value;
  const fecha = $("cita-fecha").value;
  select.innerHTML = "";
  if (!idBarbero || !fecha || !serviciosElegidos.size) {
    mostrarDisponibilidad(false, !serviciosElegidos.size ? "Selecciona al menos un servicio." : "Selecciona barbero y fecha.");
    return;
  }
  try {
    const horas = await api("/citas/horarios-disponibles", {
      query: { idBarbero, fecha, idServicios: [...serviciosElegidos].join(",") },
    });
    const actual = citaEnEdicion && citaEnEdicion.fechaHoraInicio.substring(0, 10) === fecha && String(citaEnEdicion.idBarbero) === idBarbero
      ? fmt.hora(citaEnEdicion.fechaHoraInicio) : null;
    const opciones = [...new Set([...(actual ? [actual] : []), ...horas])].sort();
    if (!opciones.length) {
      mostrarDisponibilidad(false, "El barbero no tiene horarios libres ese día (sin turno, ausencia o agenda llena). Elige otra fecha o profesional.");
      return;
    }
    select.innerHTML = opciones.map((t) => `<option value="${t}">${t}</option>`).join("");
    await checkTime();
  } catch (err) {
    mostrarDisponibilidad(false, err.message);
  }
}

async function checkTime() {
  const hora = $("time-select").value;
  if (!hora) return;
  try {
    const r = await api("/citas/disponibilidad", {
      query: {
        idBarbero: $("cita-barbero").value,
        fechaHoraInicio: `${$("cita-fecha").value}T${hora}:00`,
        idServicios: [...serviciosElegidos].join(","),
        excluirCita: citaEnEdicion ? citaEnEdicion.idCita : undefined,
      },
    });
    mostrarDisponibilidad(r.disponible, r.disponible ? `Horario disponible (${hora} – ${fmt.hora(r.fechaHoraFin)}). No se detectaron cruces.` : r.mensaje);
  } catch (err) {
    mostrarDisponibilidad(false, err.message);
  }
}

function mostrarDisponibilidad(ok, texto) {
  $("time-field").classList.toggle("invalid", !ok);
  $("conflict").hidden = ok;
  $("available").hidden = !ok;
  $(ok ? "available-text" : "conflict-text").textContent = texto;
  $("confirm-appointment").disabled = !ok || !$("cita-cliente").value;
}

$("confirm-appointment").addEventListener("click", async () => {
  const boton = $("confirm-appointment");
  boton.disabled = true;
  const fechaHoraInicio = `${$("cita-fecha").value}T${$("time-select").value}:00`;
  const observaciones = $("cita-obs").value.trim() || null;
  try {
    if (citaEnEdicion) {
      await api(`/citas/${citaEnEdicion.idCita}/reprogramar`, {
        method: "PUT",
        body: { fechaHoraInicio, idBarbero: Number($("cita-barbero").value), observaciones },
      });
      toast("Cita reprogramada");
    } else {
      await api("/citas", {
        method: "POST",
        body: {
          idCliente: Number($("cita-cliente").value),
          idBarbero: Number($("cita-barbero").value),
          fechaHoraInicio,
          idServicios: [...serviciosElegidos],
          observaciones,
        },
      });
      toast("Cita agendada");
    }
    closeDrawer();
    refrescarPantalla();
  } catch (err) {
    mostrarDisponibilidad(false, err.message);
  } finally {
    boton.disabled = $("conflict").hidden === false;
  }
});

/* ===================================================================== AGENDA */
const PX_HORA = 66;

$("agenda-prev").addEventListener("click", () => moverAgenda(-1));
$("agenda-next").addEventListener("click", () => moverAgenda(1));
$("agenda-today").addEventListener("click", () => {
  estado.agendaFecha = new Date();
  cargarAgenda();
});
document.querySelectorAll("[data-view]").forEach((b) =>
  b.addEventListener("click", () => {
    estado.agendaVista = b.dataset.view;
    document.querySelectorAll("[data-view]").forEach((x) => x.classList.toggle("active", x === b));
    cargarAgenda();
  }),
);

function moverAgenda(signo) {
  const d = new Date(estado.agendaFecha);
  d.setDate(d.getDate() + signo * (estado.agendaVista === "semana" ? 7 : 1));
  estado.agendaFecha = d;
  cargarAgenda();
}

function lunesDe(fecha) {
  const d = new Date(fecha);
  const dia = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - dia);
  return d;
}

async function cargarAgenda() {
  const semana = estado.agendaVista === "semana";
  $("agenda-calendar").hidden = semana;
  $("agenda-week").hidden = !semana;
  if (semana) return cargarAgendaSemana();

  const fecha = fmt.iso(estado.agendaFecha);
  $("agenda-date").textContent = fmt.fechaLarga(estado.agendaFecha);
  const [equipo, citas] = await Promise.all([
    api("/barberos/resumen", { query: { fecha } }),
    api("/citas", { query: { desde: fecha, hasta: fecha } }),
  ]);
  const barberos = equipo.filter((b) => b.estado === "activo");
  const cal = $("agenda-calendar");
  if (!barberos.length) {
    cal.innerHTML = '<div class="empty-state">No hay barberos activos. Regístralos en “Personal”.</div>';
    return;
  }

  const turnos = barberos.map((b) => b.turnoDelDia).filter(Boolean);
  const aMin = (t) => {
    const [hh, mm] = String(t).split(":").map(Number);
    return hh * 60 + mm;
  };
  let inicio = turnos.length ? Math.floor(Math.min(...turnos.map((t) => aMin(t.horaInicio))) / 60) : 8;
  let fin = turnos.length ? Math.ceil(Math.max(...turnos.map((t) => aMin(t.horaFin))) / 60) : 18;
  citas.forEach((c) => {
    inicio = Math.min(inicio, Number(fmt.hora(c.fechaHoraInicio).substring(0, 2)));
    fin = Math.max(fin, Math.ceil(aMin(fmt.hora(c.fechaHoraFin)) / 60));
  });
  const horas = Math.max(fin - inicio, 1);
  const columnas = `80px repeat(${barberos.length}, minmax(160px, 1fr))`;
  const top = (minutos) => ((minutos - inicio * 60) / 60) * PX_HORA;

  const cabeceras = barberos
    .map((b, i) => {
      const d = DISPONIBILIDAD[b.disponibilidad] || { label: b.disponibilidad };
      const cls = ["ausente", "sin_turno", "descanso"].includes(b.disponibilidad) ? "away" : b.disponibilidad === "fuera_de_turno" ? "off" : "";
      return `<div class="barber-head"><div class="avatar avatar--${(i % 3) + 1}">${h(fmt.iniciales(nombre(b)))}</div><div><strong>${h(nombre(b))}</strong><span class="${cls}">${h(b.ausenciaDelDia ? TIPOS_AUSENCIA[b.ausenciaDelDia.tipo] : d.label)}</span></div></div>`;
    })
    .join("");

  const columnasHtml = barberos
    .map((b) => {
      let contenido = "";
      const t = b.turnoDelDia;
      if (b.ausenciaDelDia) contenido += `<div class="calendar-off">${h(TIPOS_AUSENCIA[b.ausenciaDelDia.tipo])}</div>`;
      else if (!t) contenido += '<div class="calendar-off">Sin turno este día</div>';
      if (t && t.descansoInicio && !b.ausenciaDelDia) {
        const y = top(aMin(t.descansoInicio));
        const alto = ((aMin(t.descansoFin) - aMin(t.descansoInicio)) / 60) * PX_HORA;
        contenido += `<div class="calendar-break" style="top:${y}px;height:${alto}px">DESCANSO</div>`;
      }
      citas
        .filter((c) => c.idBarbero === b.idBarbero && c.estado !== "cancelada")
        .forEach((c) => {
          const ini = aMin(fmt.hora(c.fechaHoraInicio));
          const dur = aMin(fmt.hora(c.fechaHoraFin)) - ini;
          const e = estadoCita(c.estado);
          contenido += `<div class="calendar-event ${e.color}" data-cita="${c.idCita}" style="top:${top(ini) + 2}px;height:${Math.max((dur / 60) * PX_HORA - 4, 30)}px" title="${h(e.label)}"><strong>${h(c.cliente)}</strong><span>${h(c.servicios.map((s) => s.nombre).join(" + "))}</span><small>${fmt.hora(c.fechaHoraInicio)} · ${h(e.label)}</small></div>`;
        });
      return `<div class="calendar-col" data-barbero="${b.idBarbero}">${contenido}</div>`;
    })
    .join("");

  const etiquetas = Array.from({ length: horas }, (_, i) => `<span>${String(inicio + i).padStart(2, "0")}:00</span>`).join("");
  cal.style.minWidth = `${80 + barberos.length * 160}px`;
  cal.innerHTML = `<div class="calendar-head" style="grid-template-columns:${columnas}"><div class="timezone">GMT-5</div>${cabeceras}</div>
    <div class="calendar-body" style="grid-template-columns:${columnas};height:${horas * PX_HORA}px"><div class="hour-col" style="grid-template-rows:repeat(${horas}, 1fr)">${etiquetas}</div>${columnasHtml}</div>`;
  cal.querySelectorAll("[data-cita]").forEach((el) => el.addEventListener("click", () => detalleCita(Number(el.dataset.cita))));
}

async function cargarAgendaSemana() {
  const lunes = lunesDe(estado.agendaFecha);
  const domingo = new Date(lunes);
  domingo.setDate(lunes.getDate() + 6);
  $("agenda-date").textContent = `${fmt.fechaCorta(lunes)} – ${fmt.fechaCorta(domingo)}`;
  const tbody = $("agenda-week-body");
  filaCargando(tbody, 7);
  const citas = await api("/citas", { query: { desde: fmt.iso(lunes), hasta: fmt.iso(domingo) } });
  if (!citas.length) return filaVacia(tbody, 7, "No hay citas en esta semana");
  tbody.innerHTML = citas
    .map((c) => {
      const e = estadoCita(c.estado);
      return `<tr><td>${h(fmt.fechaCorta(c.fechaHoraInicio.substring(0, 10)))}</td><td>${fmt.hora(c.fechaHoraInicio)} – ${fmt.hora(c.fechaHoraFin)}</td><td><div class="name-cell"><div class="avatar">${h(fmt.iniciales(c.cliente))}</div><strong>${h(c.cliente)}</strong></div></td><td>${h(c.servicios.map((s) => s.nombre).join(" + "))}</td><td>${h(c.barbero)}</td><td>${statusBadge(e.cls, e.label)}</td><td><button class="row-action" data-cita="${c.idCita}" aria-label="Ver detalle">${icon("chevron")}</button></td></tr>`;
    })
    .join("");
  tbody.querySelectorAll("[data-cita]").forEach((b) => b.addEventListener("click", () => detalleCita(Number(b.dataset.cita))));
}

/* ===================================================================== PERSONAL */
$("new-barber").addEventListener("click", () => formBarbero());
$("filter-personal").addEventListener("change", () => cargarPersonal());

async function cargarPersonal() {
  const tbody = $("personal-body");
  filaCargando(tbody, 6);
  const [equipo] = await Promise.all([api("/barberos/resumen"), cargarCatalogos()]);
  const filtro = $("filter-personal").value;
  const lista = equipo.filter((b) => {
    if (!filtro) return true;
    if (filtro === "en_turno") return ["en_turno", "descanso"].includes(b.disponibilidad);
    return b.disponibilidad === filtro;
  });
  if (!lista.length) return filaVacia(tbody, 6, equipo.length ? "Ningún barbero coincide con el filtro" : "Aún no hay barberos. Crea el primero con “Nuevo barbero”.");
  tbody.innerHTML = lista
    .map((b) => {
      const t = b.turnoDelDia;
      const d = DISPONIBILIDAD[b.disponibilidad] || { label: b.disponibilidad, cls: "status--neutral" };
      const turno = b.ausenciaDelDia ? TIPOS_AUSENCIA[b.ausenciaDelDia.tipo] : t ? `${hhmm(t.horaInicio)} – ${hhmm(t.horaFin)}` : "Sin turno";
      const descanso = t && t.descansoInicio && !b.ausenciaDelDia ? `${hhmm(t.descansoInicio)} – ${hhmm(t.descansoFin)}` : "—";
      return `<tr><td><div class="name-cell"><div class="avatar">${h(fmt.iniciales(nombre(b)))}</div><div><strong>${h(nombre(b))}</strong><span class="cell-sub">${h(fmt.telefono(b.telefono))}</span></div></div></td><td>${h(turno)}</td><td>${h(descanso)}</td><td>${b.citasDelDia}</td><td>${statusBadge(d.cls, d.label)}</td>
        <td><div class="row-actions"><button class="text-link" data-horario="${b.idBarbero}">Horario</button>&nbsp;&nbsp;<button class="text-link" data-ausencias="${b.idBarbero}">Ausencias</button><button class="row-action" data-editar="${b.idBarbero}" aria-label="Editar">${icon("edit", 18)}</button><button class="row-action danger" data-borrar="${b.idBarbero}" aria-label="Eliminar">${icon("trash", 18)}</button></div></td></tr>`;
    })
    .join("");
  tbody.querySelectorAll("[data-editar]").forEach((b) => b.addEventListener("click", () => formBarbero(barberoPorId(Number(b.dataset.editar)))));
  tbody.querySelectorAll("[data-horario]").forEach((b) => b.addEventListener("click", () => formHorario(Number(b.dataset.horario))));
  tbody.querySelectorAll("[data-ausencias]").forEach((b) => b.addEventListener("click", () => formAusencias(Number(b.dataset.ausencias))));
  tbody.querySelectorAll("[data-borrar]").forEach((b) =>
    b.addEventListener("click", () => confirmarBorrado(`/barberos/${b.dataset.borrar}`, "el barbero", cargarPersonal)),
  );
  aplicarBusquedaTabla("personal");
}

function formBarbero(b = null) {
  modal.abrir({
    eyebrow: "EQUIPO",
    title: b ? "Editar barbero" : "Nuevo barbero",
    subtitle: b ? "" : "Después de crearlo, define su horario para poder agendarle citas.",
    html: `<div class="form-grid">
      ${campo("Nombres", "nombres", { value: b?.nombres, required: true })}
      ${campo("Apellidos", "apellidos", { value: b?.apellidos, required: true })}
      ${campo("Documento", "documento", { value: b?.documento, required: true })}
      ${campo("Teléfono", "telefono", { value: b?.telefono, required: true, type: "tel" })}
      ${campo("Fecha de ingreso", "fechaIngreso", { type: "date", value: b?.fechaIngreso || fmt.iso(new Date()), required: true })}
      ${campo("Estado", "estado", { value: b?.estado || "activo", options: [["activo", "Activo"], ["inactivo", "Inactivo"]] })}
    </div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: b ? "Guardar cambios" : "Crear barbero",
        onClick: async (v) => {
          const cuerpo = { ...v, idUsuario: b?.idUsuario ?? null };
          const r = await api(b ? `/barberos/${b.idBarbero}` : "/barberos", { method: b ? "PUT" : "POST", body: cuerpo });
          toast(b ? "Barbero actualizado" : "Barbero creado");
          if (b) modal.cerrar();
          else formHorario(r.idBarbero);
          cargarPersonal();
        },
      },
    ],
  });
}

async function formHorario(idBarbero) {
  const b = barberoPorId(idBarbero) || (await api(`/barberos/${idBarbero}`));
  const turnos = await api(`/turnos/barbero/${idBarbero}`);
  const porDia = Object.fromEntries(turnos.map((t) => [t.diaSemana, t]));
  const filas = [1, 2, 3, 4, 5, 6, 7]
    .map((d) => {
      const t = porDia[d];
      return `<tr data-dia="${d}"><td><label class="check-field"><input type="checkbox" class="h-on" ${t ? "checked" : ""}> ${DIAS[d]}</label></td>
        <td><input type="time" class="h-ini" value="${hhmm(t?.horaInicio) || "09:00"}"></td><td><input type="time" class="h-fin" value="${hhmm(t?.horaFin) || "18:00"}"></td>
        <td><input type="time" class="h-dini" value="${hhmm(t?.descansoInicio) || ""}"></td><td><input type="time" class="h-dfin" value="${hhmm(t?.descansoFin) || ""}"></td></tr>`;
    })
    .join("");
  modal.abrir({
    eyebrow: "HORARIO SEMANAL",
    title: nombre(b),
    subtitle: "Marca los días que trabaja. El descanso es opcional y debe quedar dentro del turno.",
    wide: true,
    html: `<div class="table-wrap"><table class="mini-table"><thead><tr><th>Día</th><th>Entrada</th><th>Salida</th><th>Descanso desde</th><th>Descanso hasta</th></tr></thead><tbody>${filas}</tbody></table></div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: "Guardar horario",
        onClick: async () => {
          const errores = [];
          for (const tr of $("form-body").querySelectorAll("tr[data-dia]")) {
            const dia = Number(tr.dataset.dia);
            const activo = tr.querySelector(".h-on").checked;
            const existente = porDia[dia];
            try {
              if (!activo) {
                if (existente) await api(`/turnos/${existente.idTurno}`, { method: "DELETE" });
                continue;
              }
              const cuerpo = {
                idBarbero,
                diaSemana: dia,
                horaInicio: tr.querySelector(".h-ini").value,
                horaFin: tr.querySelector(".h-fin").value,
                descansoInicio: tr.querySelector(".h-dini").value || null,
                descansoFin: tr.querySelector(".h-dfin").value || null,
              };
              if (!cuerpo.horaInicio || !cuerpo.horaFin) throw new Error("falta la hora de entrada o salida");
              if ((cuerpo.descansoInicio === null) !== (cuerpo.descansoFin === null)) throw new Error("completa las dos horas del descanso o deja ambas vacías");
              const r = await api(existente ? `/turnos/${existente.idTurno}` : "/turnos", { method: existente ? "PUT" : "POST", body: cuerpo });
              porDia[dia] = r;
            } catch (err) {
              errores.push(`${DIAS[dia]}: ${err.message}`);
            }
          }
          if (errores.length) throw new Error(errores.join(" · "));
          modal.cerrar();
          toast("Horario guardado");
          refrescarPantalla();
        },
      },
    ],
  });
}

async function formAusencias(idBarbero) {
  const b = barberoPorId(idBarbero) || (await api(`/barberos/${idBarbero}`));
  const ausencias = (await api(`/ausencias/barbero/${idBarbero}`)).sort((x, y) => y.fechaInicio.localeCompare(x.fechaInicio));
  const filas = ausencias.length
    ? ausencias
        .map((a) => `<tr><td>${h(TIPOS_AUSENCIA[a.tipo] || a.tipo)}</td><td>${h(a.fechaInicio)}</td><td>${h(a.fechaFin)}</td><td>${h(a.motivo || "—")}</td><td><button class="row-action danger" data-borrar-aus="${a.idAusencia}" aria-label="Eliminar">${icon("trash", 16)}</button></td></tr>`)
        .join("")
    : '<tr class="empty-row"><td colspan="5">Sin ausencias registradas</td></tr>';
  const hoy = fmt.iso(new Date());
  modal.abrir({
    eyebrow: "LICENCIAS Y DÍAS LIBRES",
    wide: true,
    title: nombre(b),
    html: `<div class="table-wrap"><table class="mini-table"><thead><tr><th>Tipo</th><th>Desde</th><th>Hasta</th><th>Motivo</th><th></th></tr></thead><tbody>${filas}</tbody></table></div>
      <p class="form-section-title">Registrar ausencia</p>
      <div class="form-grid">
        ${campo("Tipo", "tipo", { options: Object.entries(TIPOS_AUSENCIA) })}
        ${campo("Motivo", "motivo")}
        ${campo("Desde", "fechaInicio", { type: "date", value: hoy })}
        ${campo("Hasta", "fechaFin", { type: "date", value: hoy })}
      </div>`,
    actions: [
      { label: "Cerrar", cls: "btn--secondary", onClick: async () => (modal.cerrar(), refrescarPantalla()) },
      {
        label: "Registrar",
        onClick: async (v) => {
          await api("/ausencias", { method: "POST", body: { ...v, idBarbero } });
          toast("Ausencia registrada");
          formAusencias(idBarbero);
        },
      },
    ],
  });
  $("form-body").querySelectorAll("[data-borrar-aus]").forEach((btn) =>
    btn.addEventListener("click", async () => {
      try {
        await api(`/ausencias/${btn.dataset.borrarAus}`, { method: "DELETE" });
        formAusencias(idBarbero);
      } catch (err) {
        modal.error(err.message);
      }
    }),
  );
}

function confirmarBorrado(ruta, que, alTerminar) {
  modal.abrir({
    eyebrow: "CONFIRMAR",
    title: `¿Eliminar ${que}?`,
    subtitle: "Esta acción no se puede deshacer. Si el registro tiene historial (citas, ventas...), mejor desactívalo.",
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: "Eliminar",
        cls: "btn--danger",
        onClick: async () => {
          await api(ruta, { method: "DELETE" });
          modal.cerrar();
          toast("Registro eliminado");
          alTerminar();
        },
      },
    ],
  });
}

/* ===================================================================== SERVICIOS */
$("new-service").addEventListener("click", () => formServicio());
$("filter-servicios").addEventListener("change", () => cargarServicios());

async function cargarServicios() {
  const tbody = $("servicios-body");
  filaCargando(tbody, 7);
  estado.servicios = await api("/servicios");
  const filtro = $("filter-servicios").value;
  const lista = estado.servicios.filter((s) => !filtro || s.categoria === filtro);
  if (!lista.length) return filaVacia(tbody, 7, "No hay servicios en esta categoría");
  tbody.innerHTML = lista
    .map(
      (s) => `<tr><td><div class="name-cell"><div class="avatar">${h(s.nombre.substring(0, 2))}</div><strong>${h(s.nombre)}</strong></div></td><td>${h(CATEGORIAS[s.categoria] || s.categoria)}</td><td>${s.duracionMin} min</td><td>${fmt.dinero(s.precio)}</td><td>${Number(s.porcentajeComision)}%</td><td>${s.activo ? statusBadge("status--success", "Activo") : statusBadge("status--neutral", "Inactivo")}</td>
      <td><div class="row-actions"><button class="row-action" data-editar="${s.idServicio}" aria-label="Editar">${icon("edit", 18)}</button><button class="row-action danger" data-borrar="${s.idServicio}" aria-label="Eliminar">${icon("trash", 18)}</button></div></td></tr>`,
    )
    .join("");
  tbody.querySelectorAll("[data-editar]").forEach((b) =>
    b.addEventListener("click", () => formServicio(estado.servicios.find((s) => s.idServicio === Number(b.dataset.editar)))),
  );
  tbody.querySelectorAll("[data-borrar]").forEach((b) =>
    b.addEventListener("click", () => confirmarBorrado(`/servicios/${b.dataset.borrar}`, "el servicio", cargarServicios)),
  );
  aplicarBusquedaTabla("servicios");
}

function formServicio(s = null) {
  modal.abrir({
    eyebrow: "CATÁLOGO",
    title: s ? "Editar servicio" : "Nuevo servicio",
    subtitle: "Los cambios de precio o comisión no afectan las citas ya agendadas.",
    html: `<div class="form-grid">
      ${campo("Nombre", "nombre", { value: s?.nombre, required: true, full: true })}
      ${campo("Categoría", "categoria", { value: s?.categoria || "corte", options: Object.entries(CATEGORIAS) })}
      ${campo("Duración (min)", "duracionMin", { type: "number", value: s?.duracionMin ?? 30, attrs: 'min="1" step="5"' })}
      ${campo("Precio", "precio", { type: "number", value: s?.precio ?? "", attrs: 'min="0" step="1000"', required: true })}
      ${campo("% Comisión", "porcentajeComision", { type: "number", value: s?.porcentajeComision ?? 40, attrs: 'min="0" max="100" step="0.5"' })}
      ${campo("Servicio activo", "activo", { type: "checkbox", value: s ? s.activo : true, full: true })}
    </div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: s ? "Guardar cambios" : "Crear servicio",
        onClick: async (v) => {
          await api(s ? `/servicios/${s.idServicio}` : "/servicios", { method: s ? "PUT" : "POST", body: v });
          modal.cerrar();
          toast(s ? "Servicio actualizado" : "Servicio creado");
          cargarServicios();
        },
      },
    ],
  });
}

/* ===================================================================== COMISIONES */
let ultimoReporte = null;
(function iniciarFechasComision() {
  const hoy = new Date();
  $("com-desde").value = fmt.iso(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
  $("com-hasta").value = fmt.iso(hoy);
})();
$("com-filtrar").addEventListener("click", () => cargarComisiones());
$("com-exportar").addEventListener("click", exportarComisiones);

async function cargarComisiones() {
  const tbody = $("comisiones-body");
  filaCargando(tbody, 8);
  const desde = $("com-desde").value;
  const hasta = $("com-hasta").value;
  const r = await api("/comisiones/resumen", { query: { desde, hasta } });
  ultimoReporte = r;
  const periodo = `${fmt.fechaCorta(desde)} – ${fmt.fechaCorta(hasta)}`;
  $("comisiones-stats").innerHTML = `
    <article class="stat-card"><div class="stat-icon">${icon("trend")}</div><div><span>TOTAL GENERADO</span><strong>${fmt.dinero(r.totalGenerado)}</strong><small>${h(periodo)}</small></div></article>
    <article class="stat-card"><div class="stat-icon success">${icon("money")}</div><div><span>PROPINAS</span><strong>${fmt.dinero(r.totalPropinas)}</strong><small>100% del barbero</small></div></article>
    <article class="stat-card"><div class="stat-icon">${icon("money")}</div><div><span>${esAdmin() ? "A LIQUIDAR" : "ME TOCA"}</span><strong>${fmt.dinero(r.totalALiquidar)}</strong><small>Comisiones + propinas</small></div></article>`;
  if (!r.barberos.length) return filaVacia(tbody, 8, esAdmin() ? "No hay barberos registrados" : "Tu cuenta aún no está vinculada a una ficha de barbero");
  tbody.innerHTML = r.barberos
    .map(
      (b) => `<tr><td><div class="name-cell"><div class="avatar">${h(fmt.iniciales(b.barbero))}</div><strong>${h(b.barbero)}</strong></div></td><td>${b.cantidad_servicios}</td><td>${b.cantidad_productos}</td><td>${fmt.dinero(b.ventas_generadas)}</td><td>${fmt.dinero(b.comision_servicios)}</td><td>${fmt.dinero(b.comision_productos)}</td><td>${fmt.dinero(b.propinas)}${b.cantidad_propinas ? `<span class="cell-sub">${b.cantidad_propinas} propina(s)</span>` : ""}</td><td><strong>${fmt.dinero(b.total_a_pagar)}</strong><span class="cell-sub">Comisión ${fmt.dinero(b.comision_total)}</span></td></tr>`,
    )
    .join("");
  aplicarBusquedaTabla("comisiones");
}

function exportarComisiones() {
  if (!ultimoReporte) return toast("Primero consulta un periodo", "error");
  const cab = ["Barbero", "Servicios", "Total servicios", "Comisión servicios", "Productos", "Total productos", "Comisión productos", "Ventas generadas", "Comisión total", "Propinas", "A liquidar"];
  const filas = ultimoReporte.barberos.map((b) => [b.barbero, b.cantidad_servicios, b.total_servicios, b.comision_servicios, b.cantidad_productos, b.total_productos, b.comision_productos, b.ventas_generadas, b.comision_total, b.propinas, b.total_a_pagar]);
  const csv = [cab, ...filas, [], ["Total generado", ultimoReporte.totalGenerado], ["Total propinas", ultimoReporte.totalPropinas], ["Total a liquidar", ultimoReporte.totalALiquidar]]
    .map((f) => f.map((x) => `"${String(x ?? "").replace(/"/g, '""')}"`).join(";"))
    .join("\r\n");
  const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `comisiones_${ultimoReporte.desde}_${ultimoReporte.hasta}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

/* ===================================================================== CLIENTES */
$("new-client").addEventListener("click", () => formCliente());
$("filter-clientes").addEventListener("change", () => cargarClientes());

async function cargarClientes() {
  const tbody = $("clientes-body");
  filaCargando(tbody, 6);
  const lista = await api("/clientes/resumen");
  estado.clientes = lista;
  const filtro = $("filter-clientes").value;
  const filtrada = lista.filter((c) => (filtro === "proxima" ? c.proximaCita : filtro === "nuevo" ? c.visitas === 0 : true));
  if (!filtrada.length) return filaVacia(tbody, 6, lista.length ? "Ningún cliente coincide con el filtro" : "Aún no hay clientes. Crea el primero con “Nuevo cliente”.");
  tbody.innerHTML = filtrada
    .map(
      (c) => `<tr><td><div class="name-cell"><div class="avatar">${h(fmt.iniciales(nombre(c)))}</div><strong>${h(nombre(c))}</strong></div></td><td>${h(fmt.telefono(c.telefono))}${c.email ? `<span class="cell-sub">${h(c.email)}</span>` : ""}</td><td>${h(fmt.fechaHoraRelativa(c.ultimaVisita))}${c.proximaCita ? `<span class="cell-sub">Próxima: ${h(fmt.fechaHoraRelativa(c.proximaCita))}</span>` : ""}</td><td>${h(c.preferenciasCorte || "—")}</td><td>${c.visitas}</td>
      <td><div class="row-actions"><button class="row-action" data-ver="${c.idCliente}" aria-label="Ver detalle">${icon("chevron", 18)}</button>${esAdmin() ? `<button class="row-action" data-editar="${c.idCliente}" aria-label="Editar">${icon("edit", 18)}</button><button class="row-action danger" data-borrar="${c.idCliente}" aria-label="Eliminar">${icon("trash", 18)}</button>` : ""}</div></td></tr>`,
    )
    .join("");
  tbody.querySelectorAll("[data-ver]").forEach((b) => b.addEventListener("click", () => detalleCliente(Number(b.dataset.ver))));
  tbody.querySelectorAll("[data-editar]").forEach((b) => b.addEventListener("click", async () => formCliente(await api(`/clientes/${b.dataset.editar}`))));
  tbody.querySelectorAll("[data-borrar]").forEach((b) =>
    b.addEventListener("click", () => confirmarBorrado(`/clientes/${b.dataset.borrar}`, "el cliente", cargarClientes)),
  );
  aplicarBusquedaTabla("clientes");
}

function formCliente(c = null) {
  modal.abrir({
    eyebrow: "CLIENTES",
    title: c ? "Editar cliente" : "Nuevo cliente",
    subtitle: "El correo se guarda cifrado en la base de datos.",
    html: `<div class="form-grid">
      ${campo("Nombres", "nombres", { value: c?.nombres, required: true })}
      ${campo("Apellidos", "apellidos", { value: c?.apellidos, required: true })}
      ${campo("Teléfono", "telefono", { value: c?.telefono, required: true, type: "tel" })}
      ${campo("Correo", "email", { value: c?.email, type: "email" })}
      ${campo("Preferencias de corte", "preferenciasCorte", { value: c?.preferenciasCorte, type: "textarea", full: true, placeholder: "Estilo, número de máquina, observaciones técnicas" })}
    </div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: c ? "Guardar cambios" : "Crear cliente",
        onClick: async (v) => {
          await api(c ? `/clientes/${c.idCliente}` : "/clientes", { method: c ? "PUT" : "POST", body: v });
          modal.cerrar();
          toast(c ? "Cliente actualizado" : "Cliente creado");
          if (estado.pantalla === "clientes") cargarClientes();
          else cargarCatalogos();
        },
      },
    ],
  });
}

async function detalleCliente(idCliente) {
  const [c, historial] = await Promise.all([api(`/clientes/${idCliente}`), api(`/citas/cliente/${idCliente}`)]);
  const filas = historial.length
    ? historial
        .map((x) => {
          const e = estadoCita(x.estado);
          return `<tr><td>${h(fmt.fechaHoraRelativa(x.fechaHoraInicio))}</td><td>${h(x.servicios.map((s) => s.nombre).join(" + "))}</td><td>${h(x.barbero)}</td><td>${statusBadge(e.cls, e.label)}</td></tr>`;
        })
        .join("")
    : '<tr class="empty-row"><td colspan="4">Sin visitas registradas</td></tr>';
  modal.abrir({
    eyebrow: "FICHA DEL CLIENTE",
    wide: true,
    title: nombre(c),
    subtitle: `${fmt.telefono(c.telefono)}${c.email ? " · " + c.email : ""} · Cliente desde ${c.fechaRegistro}`,
    html: `<div class="detail-list"><div><span>Preferencias</span><strong>${h(c.preferenciasCorte || "—")}</strong></div></div>
      <p class="form-section-title">Historial de visitas</p>
      <div class="table-wrap"><table class="mini-table"><thead><tr><th>Fecha</th><th>Servicios</th><th>Barbero</th><th>Estado</th></tr></thead><tbody>${filas}</tbody></table></div>`,
    actions: [
      { label: "Cerrar", cls: "btn--secondary" },
      ...(esAdmin() ? [{ label: "Editar", cls: "btn--secondary", onClick: async () => formCliente(c) }] : []),
      {
        label: "Agendar cita",
        onClick: async () => {
          modal.cerrar();
          await abrirDrawerCita();
          $("cita-cliente").value = c.idCliente;
          await checkTime();
        },
      },
    ],
  });
}

/* ===================================================================== INVENTARIO */
$("new-product").addEventListener("click", () => formProducto());
$("filter-inventario").addEventListener("change", () => cargarInventario());
$("inv-ver-alertas").addEventListener("click", () => {
  $("filter-inventario").value = "bajo";
  cargarInventario();
});
const stockBajo = (p) => p.activo && p.stockActual <= p.stockMinimo;

async function cargarInventario() {
  const tbody = $("inventario-body");
  filaCargando(tbody, 7);
  estado.productos = await api("/productos");
  const alertas = estado.productos.filter(stockBajo);
  $("inv-alert").hidden = alertas.length === 0;
  $("inv-alert-title").textContent = `${alertas.length} ${alertas.length === 1 ? "producto requiere" : "productos requieren"} atención`;
  const filtro = $("filter-inventario").value;
  const lista = estado.productos.filter((p) => (filtro === "bajo" ? stockBajo(p) : !filtro || p.tipo === filtro));
  if (!lista.length) return filaVacia(tbody, 7, estado.productos.length ? "Ningún producto coincide con el filtro" : "Aún no hay productos. Crea el primero con “Nuevo producto”.");
  tbody.innerHTML = lista
    .map((p) => {
      const est = !p.activo ? statusBadge("status--neutral", "Inactivo") : stockBajo(p) ? statusBadge("status--warning", "Bajo") : statusBadge("status--success", "Activo");
      return `<tr><td><div class="name-cell"><div class="avatar">${h(p.nombre.substring(0, 2))}</div><strong>${h(p.nombre)}</strong></div></td><td>${p.tipo === "venta" ? "Venta" : "Insumo"}</td><td>${p.stockActual} unidades</td><td>${p.stockMinimo}</td><td>${p.precioVenta != null ? fmt.dinero(p.precioVenta) : "—"}</td><td>${est}</td>
        <td><div class="row-actions"><button class="text-link" data-mov="${p.idProducto}">Movimientos</button><button class="row-action" data-editar="${p.idProducto}" aria-label="Editar">${icon("edit", 18)}</button><button class="row-action danger" data-borrar="${p.idProducto}" aria-label="Eliminar">${icon("trash", 18)}</button></div></td></tr>`;
    })
    .join("");
  tbody.querySelectorAll("[data-editar]").forEach((b) =>
    b.addEventListener("click", () => formProducto(estado.productos.find((p) => p.idProducto === Number(b.dataset.editar)))),
  );
  tbody.querySelectorAll("[data-mov]").forEach((b) => b.addEventListener("click", () => formMovimiento(Number(b.dataset.mov))));
  tbody.querySelectorAll("[data-borrar]").forEach((b) =>
    b.addEventListener("click", () => confirmarBorrado(`/productos/${b.dataset.borrar}`, "el producto", cargarInventario)),
  );
  aplicarBusquedaTabla("inventario");
}

function formProducto(p = null) {
  modal.abrir({
    eyebrow: "INVENTARIO",
    title: p ? "Editar producto" : "Nuevo producto",
    subtitle: p ? "Las existencias se ajustan desde “Movimientos” (entradas y salidas)." : "",
    html: `<div class="form-grid">
      ${campo("Nombre", "nombre", { value: p?.nombre, required: true, full: true })}
      ${campo("Tipo", "tipo", { value: p?.tipo || "venta", options: [["venta", "Venta al cliente"], ["insumo", "Insumo interno"]] })}
      ${campo("Precio de venta", "precioVenta", { type: "number", value: p?.precioVenta ?? "", attrs: 'min="0" step="500"', placeholder: "Solo productos de venta" })}
      ${p ? "" : campo("Existencias iniciales", "stockActual", { type: "number", value: 0, attrs: 'min="0"' })}
      ${campo("Stock mínimo", "stockMinimo", { type: "number", value: p?.stockMinimo ?? 0, attrs: 'min="0"' })}
      ${campo("% Comisión por venta", "porcentajeComision", { type: "number", value: p?.porcentajeComision ?? 0, attrs: 'min="0" max="100" step="0.5"' })}
      ${campo("Producto activo", "activo", { type: "checkbox", value: p ? p.activo : true, full: true })}
    </div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: p ? "Guardar cambios" : "Crear producto",
        onClick: async (v) => {
          if (v.tipo === "venta" && v.precioVenta == null) throw new Error("Los productos de venta necesitan precio");
          if (v.tipo === "insumo" && v.precioVenta == null) v.precioVenta = null;
          await api(p ? `/productos/${p.idProducto}` : "/productos", { method: p ? "PUT" : "POST", body: v });
          modal.cerrar();
          toast(p ? "Producto actualizado" : "Producto creado");
          cargarInventario();
        },
      },
    ],
  });
}

async function formMovimiento(idProducto) {
  const p = estado.productos.find((x) => x.idProducto === idProducto) || (await api(`/productos/${idProducto}`));
  const historial = await api(`/inventario/movimientos/${idProducto}`);
  const filas = historial.length
    ? historial
        .slice(0, 12)
        .map((m) => `<tr><td>${h(fmt.fechaHoraRelativa(m.fechaHora))}</td><td>${m.tipo === "entrada" ? statusBadge("status--success", "Entrada") : statusBadge("status--warning", "Salida")}</td><td>${m.cantidad}</td><td>${h(m.motivo || "—")}</td></tr>`)
        .join("")
    : '<tr class="empty-row"><td colspan="4">Sin movimientos manuales (las ventas descuentan solas)</td></tr>';
  modal.abrir({
    eyebrow: "MOVIMIENTOS DE INVENTARIO",
    wide: true,
    title: p.nombre,
    subtitle: `Existencias actuales: ${p.stockActual} · mínimo ${p.stockMinimo}`,
    html: `<div class="form-grid">
        ${campo("Tipo", "tipo", { options: [["entrada", "Entrada (compra)"], ["salida", "Salida (consumo, merma)"]] })}
        ${campo("Cantidad", "cantidad", { type: "number", value: 1, attrs: 'min="1"' })}
        ${campo("Motivo", "motivo", { full: true, placeholder: "Ej: compra a proveedor, uso en servicio..." })}
      </div>
      <p class="form-section-title">Últimos movimientos</p>
      <div class="table-wrap"><table class="mini-table"><thead><tr><th>Fecha</th><th>Tipo</th><th>Cantidad</th><th>Motivo</th></tr></thead><tbody>${filas}</tbody></table></div>`,
    actions: [
      { label: "Cerrar", cls: "btn--secondary" },
      {
        label: "Registrar movimiento",
        onClick: async (v) => {
          await api("/inventario/movimientos", { method: "POST", body: { ...v, idProducto } });
          toast("Movimiento registrado");
          await cargarInventario();
          formMovimiento(idProducto);
        },
      },
    ],
  });
}

/* ===================================================================== CAJA */
async function cargarCaja() {
  await cargarCatalogos();
  const r = await api("/caja/estado");
  estado.caja = r.caja;
  const a = r.arqueo || {};
  const tarjeta = (iconId, cls, titulo, valor, pie) =>
    `<article class="stat-card"><div class="stat-icon ${cls}">${icon(iconId)}</div><div><span>${titulo}</span><strong>${valor}</strong><small>${h(pie)}</small></div></article>`;
  $("caja-stats").innerHTML =
    tarjeta("money", "", "APERTURA", fmt.dinero(a.monto_apertura), r.abierta ? "Base en efectivo" : "Caja cerrada") +
    tarjeta("money", "success", "EFECTIVO", fmt.dinero(a.total_efectivo), `Esperado en caja: ${fmt.dinero(a.efectivo_esperado)}`) +
    tarjeta("trend", "", "TARJETA + TRANSFERENCIA", fmt.dinero(Number(a.total_tarjeta || 0) + Number(a.total_transferencia || 0)), `Tarjeta ${fmt.dinero(a.total_tarjeta)} · Transf. ${fmt.dinero(a.total_transferencia)}`) +
    tarjeta("trend", "success", "TOTAL VENDIDO", fmt.dinero(a.total_vendido), "Ventas pagadas (sin propinas)") +
    tarjeta("money", "", "PROPINAS", fmt.dinero(a.total_propinas), `Para los barberos · cobrado ${fmt.dinero(a.total_cobrado)}`);

  const acciones = $("caja-acciones");
  if (r.abierta) {
    $("caja-estado-eyebrow").textContent = `CAJA #${r.caja.idCaja} · ABIERTA`;
    $("caja-estado-titulo").textContent = `Abierta desde ${fmt.fechaHoraRelativa(r.caja.fechaHoraApertura)}`;
    acciones.innerHTML = `<button class="btn btn--secondary" id="caja-cerrar">Cerrar caja</button><button class="btn btn--secondary" id="caja-productos">Vender productos</button><button class="btn btn--primary" id="caja-cobrar">Cobrar cita ${icon("plus", 18)}</button>`;
    $("caja-cerrar").addEventListener("click", formCerrarCaja);
    $("caja-productos").addEventListener("click", () => formCobro(null));
    $("caja-cobrar").addEventListener("click", elegirCitaACobrar);
  } else {
    $("caja-estado-eyebrow").textContent = "CAJA CERRADA";
    $("caja-estado-titulo").textContent = "Abre la caja para empezar a cobrar";
    acciones.innerHTML = `<button class="btn btn--secondary" id="caja-historial">Cajas anteriores</button><button class="btn btn--primary" id="caja-abrir">Abrir caja ${icon("plus", 18)}</button>`;
    $("caja-abrir").addEventListener("click", formAbrirCaja);
    $("caja-historial").addEventListener("click", historialCajas);
  }

  const tbody = $("caja-body");
  if (!r.abierta) return filaVacia(tbody, 7, "No hay una caja abierta");
  const ventas = await api("/ventas", { query: { idCaja: r.caja.idCaja } });
  if (!ventas.length) return filaVacia(tbody, 7, "Aún no hay ventas en esta caja");
  tbody.innerHTML = ventas
    .map((v) => {
      const cli = clientePorId(v.idCliente);
      const bar = barberoPorId(v.idBarbero);
      return `<tr><td>#${v.idVenta}${v.idCita ? `<span class="cell-sub">Cita #${v.idCita}</span>` : '<span class="cell-sub">Productos</span>'}</td><td>${fmt.hora(v.fechaHora)}</td><td>${h(cli ? nombre(cli) : "Ocasional")}</td><td>${h(bar ? nombre(bar) : "—")}</td><td>${fmt.dinero(v.total)}${Number(v.propina) > 0 ? `<span class="cell-sub">+ propina ${fmt.dinero(v.propina)}</span>` : ""}</td><td>${v.estado === "pagada" ? statusBadge("status--success", "Pagada") : statusBadge("status--error", "Anulada")}</td><td><button class="row-action" data-venta="${v.idVenta}" aria-label="Ver detalle">${icon("chevron")}</button></td></tr>`;
    })
    .join("");
  tbody.querySelectorAll("[data-venta]").forEach((b) => b.addEventListener("click", () => detalleVenta(Number(b.dataset.venta))));
}

function formAbrirCaja() {
  modal.abrir({
    eyebrow: "CAJA",
    title: "Abrir caja",
    subtitle: "Registra la base en efectivo con la que inicia la jornada.",
    html: campo("Monto de apertura", "montoApertura", { type: "number", value: 0, attrs: 'min="0" step="1000"' }),
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: "Abrir caja",
        onClick: async (v) => {
          await api("/caja/abrir", { method: "POST", body: { montoApertura: v.montoApertura ?? 0 } });
          modal.cerrar();
          toast("Caja abierta");
          cargarCaja();
        },
      },
    ],
  });
}

function formCerrarCaja() {
  modal.abrir({
    eyebrow: "ARQUEO",
    title: "Cerrar caja",
    subtitle: "Cuenta el efectivo físico. El sistema calcula la diferencia contra lo esperado.",
    html: `<div class="form-grid">${campo("Efectivo contado", "efectivoContado", { type: "number", attrs: 'min="0" step="100"', required: true })}${campo("Observaciones", "observaciones")}</div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: "Cerrar y ver arqueo",
        onClick: async (v) => {
          if (v.efectivoContado == null) throw new Error("Escribe el efectivo contado");
          const a = await api(`/caja/${estado.caja.idCaja}/cerrar`, { method: "POST", body: v });
          const dif = Number(a.diferencia || 0);
          modal.abrir({
            eyebrow: "ARQUEO DE CAJA",
            title: `Caja #${a.id_caja} cerrada`,
            html: `<div class="detail-list">
              <div><span>Apertura</span><strong>${fmt.dinero(a.monto_apertura)}</strong></div>
              <div><span>Ventas en efectivo</span><strong>${fmt.dinero(a.total_efectivo)}</strong></div>
              <div><span>Ventas con tarjeta</span><strong>${fmt.dinero(a.total_tarjeta)}</strong></div>
              <div><span>Ventas por transferencia</span><strong>${fmt.dinero(a.total_transferencia)}</strong></div>
              <div><span>Total vendido</span><strong>${fmt.dinero(a.total_vendido)}</strong></div>
              <div><span>Efectivo esperado</span><strong>${fmt.dinero(a.efectivo_esperado)}</strong></div>
              <div><span>Efectivo contado</span><strong>${fmt.dinero(a.efectivo_contado)}</strong></div>
              <div><span>Diferencia</span><strong>${statusBadge(dif === 0 ? "status--success" : "status--warning", (dif > 0 ? "Sobrante " : dif < 0 ? "Faltante " : "Cuadrada ") + fmt.dinero(Math.abs(dif)))}</strong></div>
            </div>`,
            actions: [{ label: "Listo" }],
          });
          cargarCaja();
        },
      },
    ],
  });
}

async function historialCajas() {
  const cajas = await api("/caja");
  const filas = cajas.length
    ? cajas.slice(0, 15).map((c) => `<tr><td>#${c.idCaja}</td><td>${h(fmt.fechaHoraRelativa(c.fechaHoraApertura))}</td><td>${h(c.fechaHoraCierre ? fmt.fechaHoraRelativa(c.fechaHoraCierre) : "—")}</td><td>${fmt.dinero(c.montoApertura)}</td><td><button class="text-link" data-arqueo="${c.idCaja}">Arqueo</button></td></tr>`).join("")
    : '<tr class="empty-row"><td colspan="5">Aún no hay cajas</td></tr>';
  modal.abrir({
    eyebrow: "HISTORIAL",
    title: "Cajas anteriores",
    html: `<div class="table-wrap"><table class="mini-table"><thead><tr><th>Caja</th><th>Apertura</th><th>Cierre</th><th>Base</th><th></th></tr></thead><tbody>${filas}</tbody></table></div><div id="arqueo-detalle"></div>`,
    actions: [{ label: "Cerrar", cls: "btn--secondary" }],
  });
  $("form-body").querySelectorAll("[data-arqueo]").forEach((b) =>
    b.addEventListener("click", async () => {
      const a = await api(`/caja/${b.dataset.arqueo}/arqueo`);
      $("arqueo-detalle").innerHTML = `<p class="form-section-title">Arqueo caja #${a.id_caja}</p><div class="detail-list"><div><span>Total vendido</span><strong>${fmt.dinero(a.total_vendido)}</strong></div><div><span>Efectivo esperado</span><strong>${fmt.dinero(a.efectivo_esperado)}</strong></div><div><span>Efectivo contado</span><strong>${a.efectivo_contado != null ? fmt.dinero(a.efectivo_contado) : "—"}</strong></div><div><span>Diferencia</span><strong>${a.diferencia != null ? fmt.dinero(a.diferencia) : "—"}</strong></div></div>`;
    }),
  );
}

async function elegirCitaACobrar() {
  const hoy = fmt.iso(new Date());
  const citas = (await api("/citas", { query: { desde: hoy, hasta: hoy } })).filter((c) => ["terminada", "en_atencion", "programada", "confirmada"].includes(c.estado))
    .sort((a, b) => (a.estado === "terminada" ? 0 : 1) - (b.estado === "terminada" ? 0 : 1));
  if (!citas.length) return toast("No hay citas pendientes de cobro hoy", "error");
  modal.abrir({
    eyebrow: "COBRAR",
    title: "¿Qué cita vas a cobrar?",
    html: campo("Cita", "idCita", { options: citas.map((c) => [c.idCita, `${c.estado === "terminada" ? "✓ " : ""}${fmt.hora(c.fechaHoraInicio)} · ${c.cliente} · ${c.servicios.map((s) => s.nombre).join(" + ")} · ${fmt.dinero(c.total)}`]) }),
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      { label: "Continuar", onClick: async (v) => formCobro(citas.find((c) => c.idCita === Number(v.idCita))) },
    ],
  });
}

/** Cobro de una cita (opcional) + productos, con uno o varios medios de pago. */
async function formCobro(cita) {
  await cargarCatalogos();
  if (!estado.caja) {
    const r = await api("/caja/estado");
    estado.caja = r.caja;
  }
  if (!estado.caja) {
    toast("Primero abre la caja", "error");
    return navigate("caja");
  }
  const productosVenta = estado.productos.filter((p) => p.tipo === "venta" && p.activo);
  const opcionesProducto = [["", "Agregar producto..."], ...productosVenta.map((p) => [p.idProducto, `${p.nombre} · ${fmt.dinero(p.precioVenta)} (${p.stockActual} disp.)`])];
  const barberos = [["", "Sin barbero"], ...estado.barberos.filter((b) => b.estado === "activo").map((b) => [b.idBarbero, nombre(b)])];
  const clientes = [["", "Cliente ocasional"], ...estado.clientes.map((c) => [c.idCliente, nombre(c)])];
  const totalCita = cita ? Number(cita.total) : 0;

  modal.abrir({
    eyebrow: `CAJA #${estado.caja.idCaja}`,
    title: cita ? `Cobrar a ${cita.cliente}` : "Venta de productos",
    subtitle: cita ? `${cita.servicios.map((s) => s.nombre).join(" + ")} · ${fmt.dinero(totalCita)}` : "Selecciona los productos que lleva el cliente.",
    html: `<div class="form-grid">
        ${cita ? "" : campo("Cliente", "idCliente", { options: clientes })}
        ${cita ? "" : campo("Barbero que vende (comisión)", "idBarbero", { options: barberos })}
      </div>
      <p class="form-section-title">Productos</p>
      <div class="pay-row"><label class="simple-field"><span>Producto</span><select id="cobro-producto">${opcionesProducto.map(([v, t]) => `<option value="${v}">${h(t)}</option>`).join("")}</select></label>
        <label class="simple-field"><span>Cantidad</span><input type="number" id="cobro-cantidad" value="1" min="1"></label><span></span>
        <button type="button" class="btn btn--secondary btn--sm" id="cobro-agregar" aria-label="Agregar">${icon("plus", 16)}</button></div>
      <table class="mini-table"><tbody id="cobro-items"></tbody></table>
      <p class="form-section-title">Propina para el barbero</p>
      <div class="pay-row pay-row--propina"><label class="simple-field"><span>Propina</span><input type="number" id="cobro-propina" min="0" step="500" value="0"></label>
        <div class="propina-rapida" id="cobro-propina-rapida"><button type="button" class="chip" data-p="0">Sin propina</button><button type="button" class="chip" data-p="2000">$2.000</button><button type="button" class="chip" data-p="5000">$5.000</button><button type="button" class="chip" data-p="10000">$10.000</button></div></div>
      <p class="form-hint" id="cobro-propina-nota">${cita && Number(cita.propina) > 0 ? `El barbero ya registró ${fmt.dinero(cita.propina)} de propina al terminar (en mano). Aquí solo agrega la que el cliente pague en caja.` : "Va 100% al barbero y no cuenta como venta ni comisión."}</p>
      <p class="form-section-title">Pagos</p>
      <div id="cobro-pagos"></div>
      <button type="button" class="text-link" id="cobro-agregar-pago">+ Agregar otro medio de pago</button>
      <div class="totals"><span>Venta</span><span id="cobro-venta"></span></div>
      <div class="totals"><span>Total a cobrar</span><strong id="cobro-total"></strong></div>
      <div class="totals"><span>Pagado</span><span id="cobro-pagado"></span></div>`,
    actions: [
      { label: "Cancelar", cls: "btn--secondary" },
      {
        label: "Registrar pago",
        cls: "btn--success",
        onClick: async (v) => {
          const pagos = [...$("form-body").querySelectorAll(".pago")].map((row) => ({
            medioPago: row.querySelector(".p-medio").value,
            monto: Number(row.querySelector(".p-monto").value || 0),
            referencia: row.querySelector(".p-ref").value.trim() || null,
          })).filter((p) => p.monto > 0);
          if (!pagos.length) throw new Error("Agrega al menos un pago");
          const propina = propinaActual();
          if (propina < 0) throw new Error("La propina no puede ser negativa");
          const idBarberoVenta = cita ? cita.idBarbero : v.idBarbero ? Number(v.idBarbero) : null;
          if (propina > 0 && !idBarberoVenta) throw new Error("Elige el barbero que recibe la propina");
          const cuerpo = {
            idCaja: estado.caja.idCaja,
            idCita: cita ? cita.idCita : null,
            idCliente: cita ? null : v.idCliente ? Number(v.idCliente) : null,
            idBarbero: cita ? null : v.idBarbero ? Number(v.idBarbero) : null,
            productos: items.map((i) => ({ idProducto: i.idProducto, cantidad: i.cantidad })),
            propina,
            pagos,
          };
          await api("/ventas", { method: "POST", body: cuerpo });
          modal.cerrar();
          toast("Pago registrado");
          refrescarPantalla();
        },
      },
    ],
  });

  const items = [];
  const propinaActual = () => Math.max(0, Number($("cobro-propina").value || 0));
  const venta = () => totalCita + items.reduce((s, i) => s + i.precio * i.cantidad, 0);
  const total = () => venta() + propinaActual();
  const pintarItems = () => {
    $("cobro-items").innerHTML = items
      .map((i, n) => `<tr><td>${h(i.nombre)}</td><td>${i.cantidad} × ${fmt.dinero(i.precio)}</td><td>${fmt.dinero(i.precio * i.cantidad)}</td><td><button type="button" class="row-action danger" data-quitar="${n}" aria-label="Quitar">${icon("trash", 16)}</button></td></tr>`)
      .join("");
    $("cobro-items").querySelectorAll("[data-quitar]").forEach((b) =>
      b.addEventListener("click", () => {
        items.splice(Number(b.dataset.quitar), 1);
        pintarItems();
      }),
    );
    $("cobro-venta").textContent = fmt.dinero(venta()) + (propinaActual() ? ` + propina ${fmt.dinero(propinaActual())}` : "");
    $("cobro-total").textContent = fmt.dinero(total());
    const pagosEl = $("form-body").querySelectorAll(".p-monto");
    if (pagosEl.length === 1) pagosEl[0].value = total();
    actualizarPagado();
  };
  const actualizarPagado = () => {
    const pagado = [...$("form-body").querySelectorAll(".p-monto")].reduce((s, el) => s + Number(el.value || 0), 0);
    const dif = pagado - total();
    $("cobro-pagado").textContent = `${fmt.dinero(pagado)}${dif === 0 ? " ✓" : dif > 0 ? ` (sobran ${fmt.dinero(dif)})` : ` (faltan ${fmt.dinero(-dif)})`}`;
  };
  const agregarPago = (monto) => {
    const row = document.createElement("div");
    row.className = "pay-row pago";
    row.innerHTML = `<label class="simple-field"><span>Medio</span><select class="p-medio"><option value="efectivo">Efectivo</option><option value="tarjeta">Tarjeta</option><option value="transferencia">Transferencia</option></select></label>
      <label class="simple-field"><span>Monto</span><input type="number" class="p-monto" min="0" step="100" value="${monto}"></label>
      <label class="simple-field"><span>Referencia</span><input class="p-ref" placeholder="Voucher / N° transf."></label>
      <button type="button" class="row-action danger" aria-label="Quitar pago">${icon("trash", 16)}</button>`;
    row.querySelector(".p-monto").addEventListener("input", actualizarPagado);
    row.querySelector("button").addEventListener("click", () => {
      if ($("form-body").querySelectorAll(".pago").length > 1) {
        row.remove();
        actualizarPagado();
      }
    });
    $("cobro-pagos").appendChild(row);
    actualizarPagado();
  };

  $("cobro-agregar").addEventListener("click", () => {
    const id = Number($("cobro-producto").value);
    const cantidad = Math.max(1, Number($("cobro-cantidad").value || 1));
    const p = productosVenta.find((x) => x.idProducto === id);
    if (!p) return;
    const existente = items.find((i) => i.idProducto === id);
    if (existente) existente.cantidad += cantidad;
    else items.push({ idProducto: id, nombre: p.nombre, precio: Number(p.precioVenta), cantidad });
    $("cobro-producto").value = "";
    $("cobro-cantidad").value = 1;
    pintarItems();
  });
  $("cobro-agregar-pago").addEventListener("click", () => {
    const pagado = [...$("form-body").querySelectorAll(".p-monto")].reduce((s, el) => s + Number(el.value || 0), 0);
    agregarPago(Math.max(total() - pagado, 0));
  });
  const chipsPropina = $("form-body").querySelectorAll("#cobro-propina-rapida [data-p]");
  const marcarPropina = () => chipsPropina.forEach((b) => b.classList.toggle("selected", Number(b.dataset.p) === propinaActual()));
  chipsPropina.forEach((b) =>
    b.addEventListener("click", () => {
      $("cobro-propina").value = b.dataset.p;
      marcarPropina();
      pintarItems();
    }),
  );
  $("cobro-propina").addEventListener("input", () => (marcarPropina(), pintarItems()));
  marcarPropina();
  agregarPago(total());
  pintarItems();
}

async function detalleVenta(idVenta) {
  const r = await api(`/ventas/${idVenta}`);
  const v = r.venta;
  const productos = r.detalles.length
    ? r.detalles.map((d) => {
        const p = estado.productos.find((x) => x.idProducto === d.idProducto);
        return `<div><span>${h(p ? p.nombre : "Producto " + d.idProducto)} × ${d.cantidad}</span><strong>${fmt.dinero(d.subtotal ?? d.precioUnitario * d.cantidad)}</strong></div>`;
      }).join("")
    : "";
  const propinaVenta = Number(v.propina) > 0
    ? `<div><span>Propina para ${h(nombre(barberoPorId(v.idBarbero)) || "el barbero")}</span><strong>${fmt.dinero(v.propina)}</strong></div>` : "";
  const pagos = r.pagos.map((p) => `<div><span>${h(p.medioPago)}${p.referencia ? " · " + h(p.referencia) : ""}</span><strong>${fmt.dinero(p.monto)}</strong></div>`).join("");
  const acciones = [{ label: "Cerrar", cls: "btn--secondary" }];
  if (v.estado === "pagada") {
    acciones.push({
      label: "Anular venta",
      cls: "btn--danger",
      onClick: async () => {
        await api(`/ventas/${idVenta}/anular`, { method: "PATCH" });
        modal.cerrar();
        toast("Venta anulada; los productos volvieron al inventario");
        cargarCaja();
      },
    });
  }
  modal.abrir({
    eyebrow: `VENTA #${v.idVenta}`,
    title: fmt.dinero(Number(v.total) + Number(v.propina || 0)),
    subtitle: `${fmt.fechaHoraRelativa(v.fechaHora)} · ${v.estado === "pagada" ? "Pagada" : "Anulada"}${v.idCita ? " · Cita #" + v.idCita : ""}`,
    html: `<div class="detail-list">${productos}${propinaVenta}${pagos}</div>`,
    actions: acciones,
  });
}

/* ===================================================================== arranque */
["personal", "servicios", "comisiones", "clientes", "inventario"].forEach(conectarBuscadorTabla);

(async function iniciar() {
  try {
    await cargarPerfil();
  } catch (err) {
    toast(err.message, "error");
  }
  navigate(esAdmin() ? "dashboard" : "midia");
})();

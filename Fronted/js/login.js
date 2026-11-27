/* Inicio de sesión real contra POST /api/auth/login y recuperación de contraseña. */
const $ = (id) => document.getElementById(id);

const form = $("login-form");
const password = $("password");
const error = $("form-error");

/* Que el navegador no deje escrito el usuario ni la contraseña de quien entró antes.
   Los campos arrancan en solo lectura (el navegador no los autollena) y se habilitan al tocarlos. */
function limpiarLogin() {
  // Solo se limpian los campos que la persona aún no ha tocado (siguen en solo lectura):
  // así nunca se borra lo que alguien está escribiendo.
  form.querySelectorAll("[data-sin-autollenado][readonly]").forEach((el) => (el.value = ""));
}
form.querySelectorAll("[data-sin-autollenado]").forEach((el) => {
  const habilitar = () => el.removeAttribute("readonly");
  el.addEventListener("focus", habilitar);
  el.addEventListener("pointerdown", habilitar);
});
limpiarLogin();
[150, 600, 1500].forEach((ms) => setTimeout(limpiarLogin, ms));
window.addEventListener("pageshow", () => {
  form.querySelectorAll("[data-sin-autollenado]").forEach((el) => el.setAttribute("readonly", ""));
  limpiarLogin();
});
const fields = form.querySelectorAll(".field");

// Si ya hay una sesión válida, entra directo al panel
if (Sesion.token()) {
  api("/auth/me").then(() => (window.location.href = "app.html")).catch(() => Sesion.borrar());
}

$("toggle-password").addEventListener("click", () => {
  const visible = password.type === "text";
  password.type = visible ? "password" : "text";
  $("toggle-password").innerHTML = `<svg class="icon" width="20" height="20"><use href="#i-${visible ? "eye" : "eyeOff"}"/></svg>`;
});

function mostrarError(texto) {
  $("form-success").hidden = true;
  $("form-error-text").textContent = texto;
  error.hidden = false;
  fields.forEach((f) => f.classList.add("field--error"));
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  const data = new FormData(form);
  const usuario = (data.get("usuario") || "").trim();
  const clave = data.get("password") || "";
  if (!usuario || !clave) {
    mostrarError("Ingresa tu usuario y contraseña para continuar.");
    return;
  }
  error.hidden = true;
  $("form-success").hidden = true;
  fields.forEach((f) => f.classList.remove("field--error"));
  $("submit-btn").disabled = true;
  $("submit-text").innerHTML = '<span class="spinner"></span> Validando acceso...';
  try {
    const r = await api("/auth/login", { method: "POST", body: { nombreUsuario: usuario, contrasena: clave } });
    Sesion.guardar({ token: r.token, usuario: r.usuario, expira: r.expira }, $("remember").checked);
    form.querySelectorAll("[data-sin-autollenado]").forEach((el) => (el.value = ""));
    window.location.href = "app.html";
  } catch (err) {
    mostrarError(err.message);
    $("submit-btn").disabled = false;
    $("submit-text").textContent = "Iniciar sesión";
  }
});

/* ---------- ¿Olvidaste tu contraseña? ---------- */
const recovery = $("recovery");
let paso = "correo"; // correo -> restablecer -> listo

function errorRecuperacion(texto) {
  $("recovery-error-text").textContent = texto || "";
  $("recovery-error").hidden = !texto;
}

function reiniciarRecuperacion() {
  paso = "correo";
  $("recovery-title").textContent = "¿Olvidaste tu contraseña?";
  $("recovery-text").textContent = "Ingresa tu correo y te enviaremos un enlace para restablecerla.";
  $("recovery-field").hidden = false;
  $("reset-fields").hidden = true;
  $("recovery-icon").innerHTML = '<svg class="icon" width="20" height="20"><use href="#i-lock"/></svg>';
  $("recovery-btn").textContent = "Enviar enlace";
  $("recovery-btn").disabled = false;
  errorRecuperacion("");
}

$("open-recovery").addEventListener("click", () => {
  reiniciarRecuperacion();
  recovery.hidden = false;
});
$("close-recovery").addEventListener("click", () => (recovery.hidden = true));

$("recovery-btn").addEventListener("click", async () => {
  errorRecuperacion("");
  if (paso === "listo") {
    recovery.hidden = true;
    return;
  }
  $("recovery-btn").disabled = true;
  try {
    if (paso === "correo") {
      const email = $("recovery-email").value.trim();
      if (!email) throw new Error("Escribe tu correo electrónico.");
      const r = await api("/auth/recuperar", { method: "POST", body: { email } });
      paso = "restablecer";
      $("recovery-title").textContent = "Revisa tu correo";
      $("recovery-text").textContent = `Si el correo está registrado, enviamos un código seguro a ${r.correoEnmascarado || "tu correo"}. Pégalo aquí con tu nueva contraseña.`;
      $("recovery-field").hidden = true;
      $("reset-fields").hidden = false;
      if (r.tokenRecuperacion) {
        // Modo desarrollo (sin servidor de correo): el backend devuelve el código
        $("reset-token").value = r.tokenRecuperacion;
      }
      $("recovery-btn").textContent = "Cambiar contraseña";
    } else if (paso === "restablecer") {
      const token = $("reset-token").value.trim();
      const nueva = $("reset-password").value;
      if (!token || nueva.length < 6) throw new Error("Pega el código y escribe una contraseña de mínimo 6 caracteres.");
      await api("/auth/restablecer", { method: "POST", body: { token, nuevaContrasena: nueva } });
      paso = "listo";
      $("recovery-title").textContent = "Contraseña actualizada";
      $("recovery-text").textContent = "Ya puedes iniciar sesión con tu nueva contraseña.";
      $("reset-fields").hidden = true;
      $("recovery-icon").innerHTML = '<svg class="icon" width="20" height="20"><use href="#i-check"/></svg>';
      $("recovery-btn").textContent = "Volver al inicio";
    }
  } catch (err) {
    errorRecuperacion(err.message);
  } finally {
    $("recovery-btn").disabled = false;
  }
});

/* ---------- Crear cuenta (POST /api/auth/registro) ---------- */
const register = $("register");
const regCampos = ["reg-nombre", "reg-email", "reg-usuario", "reg-password", "reg-password2"];

function errorRegistro(texto) {
  $("register-error-text").textContent = texto || "";
  $("register-error").hidden = !texto;
}

function revisarCoincidencia() {
  const a = $("reg-password").value;
  const b = $("reg-password2").value;
  const hint = $("reg-match");
  if (!b) {
    hint.textContent = "";
    hint.className = "field-hint";
  } else if (a === b) {
    hint.textContent = "✓ Las contraseñas coinciden";
    hint.className = "field-hint ok";
  } else {
    hint.textContent = "Las contraseñas no coinciden";
    hint.className = "field-hint bad";
  }
}

$("open-register").addEventListener("click", () => {
  regCampos.forEach((id) => ($(id).value = ""));
  revisarCoincidencia();
  errorRegistro("");
  register.hidden = false;
  $("reg-nombre").focus();
});
const cerrarRegistro = () => (register.hidden = true);
$("close-register").addEventListener("click", cerrarRegistro);
$("register-back").addEventListener("click", cerrarRegistro);
register.addEventListener("click", (e) => {
  if (e.target === register) cerrarRegistro();
});
$("reg-password").addEventListener("input", revisarCoincidencia);
$("reg-password2").addEventListener("input", revisarCoincidencia);
$("reg-usuario").addEventListener("input", (e) => (e.target.value = e.target.value.replace(/\s/g, "")));

$("register-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  errorRegistro("");
  const datos = {
    nombreCompleto: $("reg-nombre").value.trim().replace(/\s+/g, " "),
    email: $("reg-email").value.trim(),
    nombreUsuario: $("reg-usuario").value.trim(),
    contrasena: $("reg-password").value,
    confirmarContrasena: $("reg-password2").value,
  };
  if (datos.nombreCompleto.length < 3) return errorRegistro("Escribe tu nombre completo.");
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(datos.email)) return errorRegistro("Escribe un correo válido.");
  if (!/^[A-Za-z0-9._-]{3,40}$/.test(datos.nombreUsuario)) {
    return errorRegistro("El usuario debe tener entre 3 y 40 caracteres, sin espacios (letras, números, punto, guion o guion bajo).");
  }
  if (datos.contrasena.length < 6) return errorRegistro("La contraseña debe tener mínimo 6 caracteres.");
  if (datos.contrasena !== datos.confirmarContrasena) return errorRegistro("Las contraseñas no coinciden.");

  const boton = $("register-btn");
  boton.disabled = true;
  boton.innerHTML = '<span class="spinner"></span> Creando cuenta...';
  try {
    await api("/auth/registro", { method: "POST", body: datos });
    cerrarRegistro();
    error.hidden = true;
    fields.forEach((f) => f.classList.remove("field--error"));
    form.querySelector('input[name="usuario"]').value = datos.nombreUsuario;
    password.value = "";
    $("form-success-text").textContent = `¡Cuenta creada, ${datos.nombreCompleto.split(" ")[0]}! Ahora inicia sesión con tu usuario y contraseña.`;
    $("form-success").hidden = false;
    password.focus();
  } catch (err) {
    errorRegistro(err.message);
  } finally {
    boton.disabled = false;
    boton.textContent = "Crear cuenta";
  }
});

// Screen-flow port of COSGN00C (sign-on) -> COBIL00C (bill payment). One form submit = one ENTER.
(function () {
  const $ = (id) => document.getElementById(id);
  const message = $("message");
  const screens = {
    signon: { el: $("signon-screen"), tran: "CC00", pgm: "COSGN00C" },
    billpay: { el: $("billpay-screen"), tran: "CB00", pgm: "COBIL00C" },
  };

  function show(name) {
    for (const s of Object.values(screens)) s.el.hidden = true;
    screens[name].el.hidden = false;
    $("tran-id").textContent = screens[name].tran;
    $("pgm-name").textContent = screens[name].pgm;
    setMessage("");
  }

  function setMessage(text, isError) {
    message.textContent = text || "";
    message.classList.toggle("error", !!isError);
  }

  function focusField(field) {
    const map = { USERID: "userId", PASSWD: "password", ACTIDIN: "accountId", CONFIRM: "confirm" };
    const id = map[field];
    if (id) $(id).focus();
  }

  async function api(method, path, body) {
    const res = await fetch(path, {
      method,
      headers: { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
      const err = new Error((data && data.message) || res.statusText);
      err.field = data && data.field;
      err.status = res.status;
      throw err;
    }
    return data;
  }

  function formatMoney(value) {
    if (value === null || value === undefined) return "";
    return Number(value).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function clearBillpay() {
    $("accountId").value = "";
    $("currentBalance").value = "";
    $("confirm").value = "";
    $("accountId").focus();
  }

  $("signon-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const user = await api("POST", "/api/session", {
        userId: $("userId").value,
        password: $("password").value,
      });
      $("session-user").textContent = user.userId;
      $("session-type").textContent = user.admin ? "Admin" : "User";
      show("billpay");
      clearBillpay();
    } catch (err) {
      setMessage(err.message, true);
      focusField(err.field);
    }
  });

  $("billpay-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const result = await api("POST", "/api/bill-payments", {
        accountId: $("accountId").value,
        confirm: $("confirm").value,
      });
      if (result.currentBalance === null && !result.paid) {
        // Confirm = N: CLEAR-CURRENT-SCREEN
        clearBillpay();
        setMessage("");
        return;
      }
      $("currentBalance").value = formatMoney(result.currentBalance);
      setMessage(result.message, false);
      if (result.paid) {
        $("confirm").value = "";
        $("accountId").focus();
      } else {
        $("confirm").focus();
      }
    } catch (err) {
      if (err.status === 401) {
        show("signon");
        setMessage(err.message, true);
        return;
      }
      setMessage(err.message, true);
      focusField(err.field);
    }
  });

  $("billpay-clear").addEventListener("click", () => {
    clearBillpay();
    setMessage("");
  });

  $("billpay-exit").addEventListener("click", async () => {
    try {
      await api("POST", "/api/session/signoff");
    } catch (err) {
      if (err.status !== 401) {
        setMessage(err.message, true);
        return;
      }
    }
    show("signon");
    $("userId").value = "";
    $("password").value = "";
    setMessage("Thank you for using CardDemo application...");
    $("userId").focus();
  });

  api("GET", "/api/session")
    .then((user) => {
      $("session-user").textContent = user.userId;
      $("session-type").textContent = user.admin ? "Admin" : "User";
      show("billpay");
    })
    .catch(() => show("signon"));
})();

const scanInput = document.querySelector("#smartScan");
const verifyBtn = document.querySelector("#verifyBtn");
const verifyRing = document.querySelector("#verifyRing");
const matchState = document.querySelector("#matchState");
const deniedAlert = document.querySelector("#deniedAlert");
const opsPhase = document.querySelector("#opsPhase");
const saveBtn = document.querySelector("#saveBtn");
const saveCountdown = document.querySelector("#saveCountdown");
const goodQty = document.querySelector("#goodQty");
const scrapQty = document.querySelector("#scrapQty");
const yieldPercent = document.querySelector("#yieldPercent");
const finishDate = document.querySelector("#finishDate");
const postingDate = document.querySelector("#postingDate");

const scans = {
  operator: "",
  machine: "",
  runcard: "",
};

const routes = {
  operator: /^USER[-_ ]?\w+/i,
  machine: /^(MC|MACHINE)[-_ ]?\w+/i,
  runcard: /^(RC|RUNCARD)[-_ ]?\w+/i,
};

const cards = {
  operator: {
    card: document.querySelector("#operatorCard"),
    value: document.querySelector("#operatorValue"),
  },
  machine: {
    card: document.querySelector("#machineCard"),
    value: document.querySelector("#machineValue"),
  },
  runcard: {
    card: document.querySelector("#runcardCard"),
    value: document.querySelector("#runcardValue"),
  },
};

function routeScan(value) {
  const cleaned = value.trim();
  if (!cleaned) return;

  const target = Object.entries(routes).find(([, pattern]) => pattern.test(cleaned));
  if (!target) {
    matchState.textContent = "Unknown scan type. Use USER, MC, or RC prefix.";
    matchState.classList.remove("ok");
    return;
  }

  const [type] = target;
  scans[type] = cleaned.toUpperCase();
  cards[type].card.classList.add("scanned");
  cards[type].value.textContent = scans[type];
  scanInput.value = "";
  updateVerifyState();
}

function updateVerifyState() {
  const ready = scans.operator && scans.machine && scans.runcard;
  verifyBtn.disabled = !ready;
  deniedAlert.hidden = true;
  matchState.classList.remove("ok");
  matchState.textContent = ready
    ? "Ready for 3-second safety verification"
    : "Waiting for all three scans";
}

function countdown(button, indicator, onDone) {
  let count = 3;
  button.disabled = true;
  indicator.textContent = count;
  indicator.classList.add("counting");
  button.classList.add("counting");

  const timer = setInterval(() => {
    count -= 1;
    indicator.textContent = count;
    if (count === 0) {
      clearInterval(timer);
      indicator.classList.remove("counting");
      button.classList.remove("counting");
      onDone();
    }
  }, 1000);
}

function verifyMatch() {
  matchState.textContent = "Matching credentials...";
  countdown(verifyBtn, verifyRing, () => {
    const isMatch = scans.machine.includes("ICS09107") || scans.machine.includes("MC-09107");
    if (!isMatch) {
      deniedAlert.hidden = false;
      matchState.textContent = "Mismatch found. Scan authorized workcenter.";
      verifyBtn.disabled = false;
      verifyRing.textContent = "3";
      return;
    }

    deniedAlert.hidden = true;
    opsPhase.classList.remove("is-locked");
    matchState.classList.add("ok");
    matchState.textContent = "Verified. Production input unlocked.";
    verifyRing.textContent = "OK";
  });
}

function updateSaveState() {
  const good = Number(goodQty.value);
  const scrap = Number(scrapQty.value);
  const valid = goodQty.value !== "" && scrapQty.value !== "" && good + scrap > 0;
  saveBtn.disabled = !valid || opsPhase.classList.contains("is-locked");
  yieldPercent.textContent = valid ? `${Math.round((good / (good + scrap)) * 100)}%` : "---";
}

function nowStamp() {
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date());
}

scanInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") routeScan(scanInput.value);
});

scanInput.addEventListener("change", () => routeScan(scanInput.value));
verifyBtn.addEventListener("click", verifyMatch);
goodQty.addEventListener("input", updateSaveState);
scrapQty.addEventListener("input", updateSaveState);

saveBtn.addEventListener("click", () => {
  countdown(saveBtn, saveCountdown, () => {
    const stamp = nowStamp();
    finishDate.textContent = stamp;
    postingDate.textContent = stamp;
    saveBtn.disabled = true;
    saveCountdown.textContent = "OK";
  });
});

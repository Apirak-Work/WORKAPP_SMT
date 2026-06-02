import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  Barcode,
  CheckCircle2,
  Clock3,
  Factory,
  Lock,
  Save,
  ScanLine,
  ShieldCheck,
  TimerReset,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const routingSteps = [
  { oper: "1010", wc: "SMT-L01", description: "Kit Pull / Material Issue", yield: "100%" },
  { oper: "1020", wc: "PRN-203", description: "Solder Paste Printing", yield: "99.8%" },
  { oper: "1030", wc: "SPI-107", description: "3D Solder Paste Inspection", yield: "99.4%" },
  { oper: "1040", wc: "PNP-411", description: "Chip Mounting", yield: "Pending" },
  { oper: "1050", wc: "REF-022", description: "Reflow Oven", yield: "Pending" },
];

const scanSlots = [
  { key: "operator", label: "Operator", prefix: "OP-" },
  { key: "machine", label: "Machine", prefix: "MC-" },
  { key: "runcard", label: "Runcard", prefix: "RC-" },
];

const emptyScanState = {
  operator: "",
  machine: "",
  runcard: "",
};

const emptyQuantities = {
  goodQty: "",
  scrapQty: "",
  downtime: "",
  setupTime: "",
};

function formatDateTime(date) {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

function StatusCard({ label, value }) {
  const filled = Boolean(value);

  return (
    <Card
      className={`border-2 shadow-sm transition-colors ${
        filled ? "border-green-500 bg-green-50" : "border-slate-200 bg-white"
      }`}
    >
      <CardContent className="flex items-center gap-4 p-4">
        <div
          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${
            filled ? "bg-green-600 text-white" : "bg-slate-100 text-slate-400"
          }`}
        >
          {filled ? <CheckCircle2 className="h-6 w-6" /> : <Lock className="h-5 w-5" />}
        </div>
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-normal text-slate-500">{label}</p>
          <p
            className={`truncate text-lg font-black ${
              filled ? "text-green-700" : "text-slate-400"
            }`}
          >
            {filled || "Pending"}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

function TimestampCard({ label, value, helper }) {
  return (
    <Card className="border-slate-200 bg-slate-50 shadow-sm">
      <CardContent className="p-4">
        <p className="text-xs font-black uppercase tracking-normal text-blue-700">{label}</p>
        <p className="mt-2 min-h-7 text-base font-black text-slate-900">{value || "--"}</p>
        <p className="mt-2 text-xs font-semibold text-slate-500">{helper}</p>
      </CardContent>
    </Card>
  );
}

export default function ProductionConfirmation() {
  const [scanValue, setScanValue] = useState("");
  const [scans, setScans] = useState(emptyScanState);
  const [verifyCountdown, setVerifyCountdown] = useState(0);
  const [isVerifying, setIsVerifying] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [activeTab, setActiveTab] = useState("check");
  const [quantities, setQuantities] = useState(emptyQuantities);
  const [timestamps, setTimestamps] = useState({
    start: "",
    finish: "",
    posting: "",
  });
  const [saveCountdown, setSaveCountdown] = useState(0);
  const [isSaving, setIsSaving] = useState(false);
  const [isSaved, setIsSaved] = useState(false);

  const verifyTimerRef = useRef(null);
  const saveTimerRef = useRef(null);

  const allScansComplete = useMemo(
    () => Boolean(scans.operator && scans.machine && scans.runcard),
    [scans],
  );

  const canSave = useMemo(() => {
    const goodQty = Number(quantities.goodQty);
    const scrapQty = Number(quantities.scrapQty);

    return (
      isVerified &&
      !isSaving &&
      !isSaved &&
      quantities.goodQty !== "" &&
      quantities.scrapQty !== "" &&
      Number.isFinite(goodQty) &&
      Number.isFinite(scrapQty) &&
      goodQty + scrapQty > 0
    );
  }, [isSaved, isSaving, isVerified, quantities.goodQty, quantities.scrapQty]);

  useEffect(() => {
    return () => {
      window.clearInterval(verifyTimerRef.current);
      window.clearInterval(saveTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (!isVerified) return;

    const unlockedAt = formatDateTime(new Date());
    setTimestamps({
      start: unlockedAt,
      finish: unlockedAt,
      posting: unlockedAt,
    });
  }, [isVerified]);

  function routeScan(rawValue) {
    const value = rawValue.trim().toUpperCase();
    if (!value) return;

    setScans((current) => {
      if (value.startsWith("OP-")) {
        return { ...current, operator: value };
      }

      if (value.startsWith("MC-")) {
        return { ...current, machine: value };
      }

      if (value.startsWith("RC-")) {
        return { ...current, runcard: value };
      }

      const nextEmptySlot = scanSlots.find((slot) => !current[slot.key]);
      if (!nextEmptySlot) return current;

      return {
        ...current,
        [nextEmptySlot.key]: value,
      };
    });

    setScanValue("");
    setIsVerified(false);
    setIsSaved(false);
  }

  function handleScanKeyDown(event) {
    if (event.key !== "Enter") return;
    event.preventDefault();
    routeScan(scanValue);
  }

  function handleVerify() {
    if (!allScansComplete || isVerifying || isVerified) return;

    setIsVerifying(true);
    setVerifyCountdown(3);

    verifyTimerRef.current = window.setInterval(() => {
      setVerifyCountdown((current) => {
        if (current <= 1) {
          window.clearInterval(verifyTimerRef.current);
          setIsVerifying(false);
          setIsVerified(true);
          return 0;
        }

        return current - 1;
      });
    }, 1000);
  }

  function handleQuantityChange(field, value) {
    setQuantities((current) => ({
      ...current,
      [field]: value,
    }));
    setIsSaved(false);
  }

  function handleSaveConfirm() {
    if (!canSave) return;

    setIsSaving(true);
    setSaveCountdown(3);

    saveTimerRef.current = window.setInterval(() => {
      setSaveCountdown((current) => {
        if (current <= 1) {
          window.clearInterval(saveTimerRef.current);
          const completedAt = formatDateTime(new Date());

          setTimestamps((existing) => ({
            ...existing,
            finish: completedAt,
            posting: completedAt,
          }));
          setIsSaving(false);
          setIsSaved(true);
          return 0;
        }

        return current - 1;
      });
    }, 1000);
  }

  return (
    <div className="min-h-screen bg-slate-100 p-4 text-slate-950 md:p-6">
      <div className="mx-auto flex max-w-7xl flex-col gap-4">
        <header className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-xs font-black uppercase tracking-normal text-blue-700">
              Smart Manufacturing Execution System
            </p>
            <h1 className="mt-1 text-2xl font-black text-slate-950 md:text-3xl">
              Production Confirmation
            </h1>
          </div>
          <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-black text-blue-900">
            <Factory className="h-5 w-5 text-blue-700" />
            GEN2 / ST1 / Mobile Ready
          </div>
        </header>

        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="pb-3">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-950 text-white">
                <ScanLine className="h-6 w-6" />
              </div>
              <div>
                <CardTitle className="text-lg font-black text-slate-950">
                  Phase 1 - Smart Scan
                </CardTitle>
                <p className="text-sm font-semibold text-slate-500">
                  Scan Operator, Machine, or Runcard into one intelligent input.
                </p>
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="relative">
              <Barcode className="pointer-events-none absolute left-4 top-1/2 h-7 w-7 -translate-y-1/2 text-blue-700" />
              <Input
                value={scanValue}
                onChange={(event) => setScanValue(event.target.value)}
                onKeyDown={handleScanKeyDown}
                placeholder="Scan OP-2048, MC-SMT01, RC-S5UBT200102, or scan sequentially"
                className="h-16 border-2 border-blue-900 bg-white pl-14 text-xl font-black shadow-sm placeholder:text-slate-400 focus-visible:ring-blue-700"
              />
            </div>

            <div className="grid gap-3 md:grid-cols-3">
              {scanSlots.map((slot) => (
                <StatusCard key={slot.key} label={slot.label} value={scans[slot.key]} />
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-sm">
          <CardHeader className="pb-3">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-950 text-white">
                <ShieldCheck className="h-6 w-6" />
              </div>
              <div>
                <CardTitle className="text-lg font-black text-slate-950">
                  Phase 2 - Data Verification
                </CardTitle>
                <p className="text-sm font-semibold text-slate-500">
                  Safety interlock delays confirmation before production entry unlocks.
                </p>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
              <Button
                type="button"
                size="lg"
                disabled={!allScansComplete || isVerifying || isVerified}
                onClick={handleVerify}
                className="h-16 min-w-full rounded-lg bg-blue-950 text-base font-black text-white hover:bg-blue-900 disabled:bg-slate-300 disabled:text-slate-600 lg:min-w-96"
              >
                {isVerifying ? (
                  <>
                    <TimerReset className="mr-2 h-5 w-5 animate-spin" />
                    Verifying... {verifyCountdown}s
                  </>
                ) : isVerified ? (
                  <>
                    <CheckCircle2 className="mr-2 h-5 w-5" />
                    DATA VERIFIED
                  </>
                ) : (
                  <>
                    <ShieldCheck className="mr-2 h-5 w-5" />
                    VERIFY & MATCH DATA
                  </>
                )}
              </Button>

              <div
                className={`flex min-h-16 flex-1 items-center gap-3 rounded-lg border px-4 py-3 font-bold ${
                  isVerified
                    ? "border-green-500 bg-green-50 text-green-700"
                    : allScansComplete
                      ? "border-amber-400 bg-amber-50 text-amber-800"
                      : "border-slate-200 bg-slate-50 text-slate-500"
                }`}
              >
                {isVerified ? (
                  <CheckCircle2 className="h-6 w-6 shrink-0" />
                ) : allScansComplete ? (
                  <AlertTriangle className="h-6 w-6 shrink-0" />
                ) : (
                  <Lock className="h-6 w-6 shrink-0" />
                )}
                <span>
                  {isVerified
                    ? "Verification complete. Phase 3 is unlocked."
                    : allScansComplete
                      ? "All scan slots are filled. Ready for verification."
                      : "Waiting for Operator, Machine, and Runcard scans."}
                </span>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card
          className={`border-slate-200 bg-white shadow-sm transition-opacity ${
            isVerified ? "opacity-100" : "opacity-55"
          }`}
        >
          <CardHeader className="pb-3">
            <div className="flex items-center gap-3">
              <div
                className={`flex h-11 w-11 items-center justify-center rounded-lg ${
                  isVerified ? "bg-green-600 text-white" : "bg-slate-200 text-slate-500"
                }`}
              >
                {isVerified ? <CheckCircle2 className="h-6 w-6" /> : <Lock className="h-6 w-6" />}
              </div>
              <div>
                <CardTitle className="text-lg font-black text-slate-950">
                  Phase 3 - Production Input
                </CardTitle>
                <p className="text-sm font-semibold text-slate-500">
                  {isVerified
                    ? "Input production quantities and confirm the active routing step."
                    : "Locked until Phase 2 verification is complete."}
                </p>
              </div>
            </div>
          </CardHeader>

          <CardContent className="space-y-4">
            <fieldset disabled={!isVerified} className="space-y-4 disabled:pointer-events-none">
              <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
                <TabsList className="grid h-12 w-full grid-cols-3 bg-slate-100 p-1 md:w-[520px]">
                  <TabsTrigger value="check" className="font-black">
                    Check Runcard
                  </TabsTrigger>
                  <TabsTrigger value="split" className="font-black">
                    Split Lot
                  </TabsTrigger>
                  <TabsTrigger value="merge" className="font-black">
                    Merge/Combine
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="check" className="mt-4">
                  <div className="overflow-hidden rounded-lg border border-slate-200">
                    <Table>
                      <TableHeader>
                        <TableRow className="bg-slate-200 hover:bg-slate-200">
                          <TableHead className="font-black text-slate-800">OPER</TableHead>
                          <TableHead className="font-black text-slate-800">WC</TableHead>
                          <TableHead className="font-black text-slate-800">DESCRIPTION</TableHead>
                          <TableHead className="font-black text-slate-800">YIELD</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {routingSteps.map((step) => {
                          const isActive = step.oper === "1030";

                          return (
                            <TableRow
                              key={step.oper}
                              className={
                                isActive
                                  ? "border-l-4 border-l-green-500 bg-green-50 hover:bg-green-50"
                                  : "bg-white hover:bg-slate-50"
                              }
                            >
                              <TableCell className="font-black text-slate-950">
                                {step.oper}
                              </TableCell>
                              <TableCell className="font-bold text-slate-800">{step.wc}</TableCell>
                              <TableCell className="font-bold text-slate-800">
                                {step.description}
                                {isActive && (
                                  <span className="ml-3 rounded-full border border-green-500 bg-white px-2 py-1 text-xs font-black text-green-700">
                                    Current Active Step
                                  </span>
                                )}
                              </TableCell>
                              <TableCell
                                className={`font-black ${
                                  isActive ? "text-green-700" : "text-slate-700"
                                }`}
                              >
                                {step.yield}
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </div>
                </TabsContent>

                <TabsContent value="split" className="mt-4">
                  <div className="rounded-lg border border-amber-300 bg-amber-50 p-5 font-bold text-amber-900">
                    Split Lot mode is ready for child-lot quantity planning.
                  </div>
                </TabsContent>

                <TabsContent value="merge" className="mt-4">
                  <div className="rounded-lg border border-blue-200 bg-blue-50 p-5 font-bold text-blue-900">
                    Merge/Combine mode is ready for companion runcard selection.
                  </div>
                </TabsContent>
              </Tabs>

              <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                {[
                  ["goodQty", "GOOD QTY"],
                  ["scrapQty", "SCRAP QTY"],
                  ["downtime", "DOWNTIME (MIN)"],
                  ["setupTime", "SETUP TIME (MIN)"],
                ].map(([field, label]) => (
                  <label
                    key={field}
                    className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
                  >
                    <span className="text-xs font-black uppercase tracking-normal text-blue-800">
                      {label}
                    </span>
                    <Input
                      type="number"
                      min="0"
                      inputMode="numeric"
                      value={quantities[field]}
                      onChange={(event) => handleQuantityChange(field, event.target.value)}
                      placeholder="0"
                      className="mt-2 h-14 border-2 border-slate-200 text-2xl font-black focus-visible:ring-blue-700"
                    />
                  </label>
                ))}
              </div>

              <div className="grid gap-3 md:grid-cols-3">
                <TimestampCard
                  label="START DATE"
                  value={timestamps.start}
                  helper="Auto-filled when Phase 3 unlocks"
                />
                <TimestampCard
                  label="FINISH DATE"
                  value={timestamps.finish}
                  helper="Refreshed after save confirmation"
                />
                <TimestampCard
                  label="POSTING DATE"
                  value={timestamps.posting}
                  helper="Refreshed after save confirmation"
                />
              </div>
            </fieldset>

            <div className="flex flex-col gap-3 border-t border-slate-200 pt-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex flex-wrap gap-2 text-sm font-black text-slate-600">
                <span className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                  Required: GOOD QTY + SCRAP QTY
                </span>
                <span className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                  <Clock3 className="mr-1 inline h-4 w-4" />
                  3s hold-to-confirm
                </span>
              </div>

              <Button
                type="button"
                size="lg"
                disabled={!canSave}
                onClick={handleSaveConfirm}
                className="h-16 min-w-full rounded-lg bg-green-600 text-base font-black text-white hover:bg-green-700 disabled:bg-slate-300 disabled:text-slate-600 lg:min-w-80"
              >
                {isSaving ? (
                  <>
                    <TimerReset className="mr-2 h-5 w-5 animate-spin" />
                    Confirming... {saveCountdown}s
                  </>
                ) : isSaved ? (
                  <>
                    <CheckCircle2 className="mr-2 h-5 w-5" />
                    CONFIRM SAVED
                  </>
                ) : (
                  <>
                    <Save className="mr-2 h-5 w-5" />
                    SAVE CONFIRM
                  </>
                )}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

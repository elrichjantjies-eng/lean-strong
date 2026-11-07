import { useEffect, useState } from "react";
import {
  BellRing,
  ExternalLink,
  LayoutGrid,
  Palette,
  Ruler,
  Share,
  Smartphone,
  Info,
} from "lucide-react";
import { Sheet, Segmented, Toggle } from "@/components/ui";
import { useStore } from "@/state/store";
import type { Settings as S, WidgetStyle } from "@/state/settings";
import { isAndroid, isIos, isNative, isStandalone, WidgetBridge } from "@/platform/native";
import { WeatherIcon } from "@/icons/WeatherIcon";

const REPO = "https://github.com/elrichjantjies-eng/lean-strong";

export function Settings({ onClose }: { onClose: () => void }) {
  const s = useStore((st) => st.settings);
  const update = useStore((st) => st.updateSettings);
  const set =
    <K extends keyof S>(k: K) =>
    (v: S[K]) =>
      update({ [k]: v } as Partial<S>);
  const [info, setInfo] = useState<{
    widgets: number;
    notifications: boolean;
    canPin: boolean;
  } | null>(null);

  useEffect(() => {
    if (isAndroid)
      void WidgetBridge.info()
        .then(setInfo)
        .catch(() => {});
  }, []);

  const enableNotification = async (
    k: "notifyWarnings" | "notifyRain" | "notifyDaily",
    v: boolean,
  ) => {
    if (v && isAndroid) {
      const r = await WidgetBridge.requestNotifications().catch(() => ({ granted: false }));
      setInfo((i) => (i ? { ...i, notifications: r.granted } : i));
    }
    update({ [k]: v });
  };

  return (
    <Sheet title="Settings" onClose={onClose} tall>
      <Section icon={<Ruler size={14} />} title="Units">
        <Field label="Temperature">
          <Segmented
            label="Temperature"
            value={s.temp}
            onChange={set("temp")}
            options={[
              { value: "c", label: "°C" },
              { value: "f", label: "°F" },
            ]}
          />
        </Field>
        <Field label="Wind">
          <Segmented
            label="Wind"
            value={s.wind}
            onChange={set("wind")}
            options={[
              { value: "kmh", label: "km/h" },
              { value: "ms", label: "m/s" },
              { value: "mph", label: "mph" },
              { value: "kn", label: "knots" },
              { value: "bft", label: "Bft" },
            ]}
          />
        </Field>
        <Field label="Rain">
          <Segmented
            label="Rain"
            value={s.precip}
            onChange={set("precip")}
            options={[
              { value: "mm", label: "mm" },
              { value: "in", label: "inches" },
            ]}
          />
        </Field>
        <Field label="Pressure">
          <Segmented
            label="Pressure"
            value={s.pressure}
            onChange={set("pressure")}
            options={[
              { value: "hpa", label: "hPa" },
              { value: "inhg", label: "inHg" },
              { value: "mmhg", label: "mmHg" },
            ]}
          />
        </Field>
        <Field label="Distance">
          <Segmented
            label="Distance"
            value={s.distance}
            onChange={set("distance")}
            options={[
              { value: "km", label: "km" },
              { value: "mi", label: "miles" },
            ]}
          />
        </Field>
        <Field label="Time">
          <Segmented
            label="Time"
            value={s.h24 ? "24" : "12"}
            onChange={(v) => update({ h24: v === "24" })}
            options={[
              { value: "24", label: "24-hour" },
              { value: "12", label: "12-hour" },
            ]}
          />
        </Field>
        <Field label="Air quality index">
          <Segmented
            label="Air quality index"
            value={s.aqi}
            onChange={set("aqi")}
            options={[
              { value: "us", label: "US AQI" },
              { value: "eu", label: "European" },
            ]}
          />
        </Field>
      </Section>

      <Section icon={<Palette size={14} />} title="Appearance">
        <Toggle
          label="Animated backgrounds"
          hint="Rain, snow, clouds, stars and lightning behind the forecast"
          checked={s.animations}
          onChange={set("animations")}
        />
      </Section>

      <Section icon={<LayoutGrid size={14} />} title="Home-screen widgets">
        {isAndroid ? (
          <>
            <p className="text-dim mb-3 text-sm">
              Long-press your home screen → <b>Widgets</b> → <b>Skyglass</b>, or add one here.
              Widgets refresh every 30 minutes and show your first location unless you pick another
              (long-press a widget → settings).
              {info ? ` ${info.widgets} on your home screen.` : ""}
            </p>
            <WidgetPreview style={s.widgetStyle} opacity={s.widgetOpacity} />
            <Field label="Style">
              <Segmented<WidgetStyle>
                label="Widget style"
                value={s.widgetStyle}
                onChange={set("widgetStyle")}
                options={[
                  { value: "glass", label: "Glass" },
                  { value: "frosted", label: "Frosted" },
                  { value: "dark", label: "Dark" },
                  { value: "tinted", label: "Sky tint" },
                ]}
              />
            </Field>
            <Field label={`Glass opacity: ${s.widgetOpacity}%`}>
              <input
                type="range"
                min={20}
                max={100}
                step={5}
                value={s.widgetOpacity}
                aria-label="Widget opacity"
                onChange={(e) => update({ widgetOpacity: Number(e.target.value) })}
                className="w-full"
              />
            </Field>
            {info?.canPin && (
              <div className="mt-2 grid grid-cols-3 gap-2">
                {(
                  [
                    ["small", "Small 2×2"],
                    ["wide", "Wide 4×1"],
                    ["clock", "Clock 4×2"],
                    ["medium", "Hourly 4×2"],
                    ["large", "Forecast 4×4"],
                  ] as const
                ).map(([kind, label]) => (
                  <button
                    key={kind}
                    type="button"
                    onClick={() => void WidgetBridge.pin({ kind })}
                    className="glass-pill rounded-xl px-2 py-2 text-xs"
                  >
                    + {label}
                  </button>
                ))}
              </div>
            )}
          </>
        ) : (
          <p className="text-dim text-sm">
            Home-screen widgets are part of the Android app (Samsung and other Android phones).
            iPhone web apps can't add widgets, but you can keep Skyglass on your Home Screen for
            one-tap access.
          </p>
        )}
      </Section>

      <Section icon={<BellRing size={14} />} title="Notifications">
        {isAndroid ? (
          <>
            {info && !info.notifications && (s.notifyWarnings || s.notifyRain || s.notifyDaily) && (
              <button
                type="button"
                onClick={() =>
                  void WidgetBridge.requestNotifications().then((r) =>
                    setInfo((i) => (i ? { ...i, notifications: r.granted } : i)),
                  )
                }
                className="mb-2 w-full rounded-xl bg-amber-400/25 px-3 py-2 text-left text-sm"
              >
                Notifications are blocked. Tap to allow them.
              </button>
            )}
            <Toggle
              label="Severe weather warnings"
              hint="SAWS warnings for your location"
              checked={s.notifyWarnings}
              onChange={(v) => void enableNotification("notifyWarnings", v)}
            />
            <Toggle
              label="Rain starting soon"
              hint="When rain is expected within the hour"
              checked={s.notifyRain}
              onChange={(v) => void enableNotification("notifyRain", v)}
            />
            <Toggle
              label="Daily forecast"
              hint="A morning summary of the day"
              checked={s.notifyDaily}
              onChange={(v) => void enableNotification("notifyDaily", v)}
            />
            {s.notifyDaily && (
              <Field label="Daily forecast time">
                <input
                  type="time"
                  value={s.dailyAt}
                  onChange={(e) => e.target.value && update({ dailyAt: e.target.value })}
                  className="glass-pill w-full rounded-xl px-3 py-2 text-[16px]"
                  style={{ userSelect: "text" }}
                />
              </Field>
            )}
          </>
        ) : (
          <p className="text-dim text-sm">
            Warning, rain and daily forecast notifications are available in the Android app.
            Warnings still show at the top of the forecast here.
          </p>
        )}
      </Section>

      {!isNative && (
        <Section icon={<Smartphone size={14} />} title="Install">
          {isIos() && !isStandalone() ? (
            <p className="text-dim text-sm">
              In Safari, tap <Share size={13} className="inline" /> <b>Share</b> →{" "}
              <b>Add to Home Screen</b> to install Skyglass as an app.
            </p>
          ) : isStandalone() ? (
            <p className="text-dim text-sm">Installed. Skyglass updates itself automatically.</p>
          ) : (
            <p className="text-dim text-sm">
              Add Skyglass to your home screen from your browser's menu.
            </p>
          )}
          <a
            href={`${REPO}/releases?q=weather`}
            target="_blank"
            rel="noreferrer"
            className="mt-2 flex items-center gap-1.5 text-sm text-sky-300"
          >
            Android app with widgets (APK) <ExternalLink size={12} />
          </a>
        </Section>
      )}

      <Section icon={<Info size={14} />} title="About">
        <div className="flex items-center gap-3">
          <WeatherIcon icon="partly-day" size={44} />
          <div>
            <div className="font-semibold">Skyglass Weather {__APP_VERSION__}</div>
            <div className="text-faint text-xs">Made for South Africa, works worldwide</div>
          </div>
        </div>
        <ul className="text-dim mt-3 space-y-1.5 text-sm">
          <li>
            Forecasts, air quality and sea conditions:{" "}
            <a className="underline" href="https://open-meteo.com" target="_blank" rel="noreferrer">
              Open-Meteo
            </a>{" "}
            (CC BY 4.0)
          </li>
          <li>
            Official forecasts, warnings, fire danger, tides and storm tracking:{" "}
            <a
              className="underline"
              href="https://www.weathersa.co.za"
              target="_blank"
              rel="noreferrer"
            >
              South African Weather Service
            </a>
          </li>
          <li>
            Radar:{" "}
            <a
              className="underline"
              href="https://www.rainviewer.com"
              target="_blank"
              rel="noreferrer"
            >
              RainViewer
            </a>{" "}
            · Satellite: EUMETSAT · Maps: Esri, OpenStreetMap
          </li>
        </ul>
      </Section>
    </Sheet>
  );
}

function Section({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mb-5">
      <div className="card-title mb-2">
        {icon} <span>{title}</span>
      </div>
      <div className="glass p-4">{children}</div>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="py-1.5">
      <div className="text-dim mb-1.5 text-[13px]">{label}</div>
      {children}
    </div>
  );
}

/** A live preview of the Android widget glass, so style changes are visible right away. */
function WidgetPreview({ style, opacity }: { style: WidgetStyle; opacity: number }) {
  const a = opacity / 100;
  const bg =
    style === "dark"
      ? `linear-gradient(145deg, rgba(12,18,34,${0.75 * a}), rgba(12,18,34,${0.55 * a}))`
      : style === "frosted"
        ? `linear-gradient(145deg, rgba(255,255,255,${0.42 * a}), rgba(255,255,255,${0.26 * a}))`
        : style === "tinted"
          ? `linear-gradient(145deg, rgba(90,160,255,${0.45 * a}), rgba(40,90,200,${0.3 * a}))`
          : `linear-gradient(145deg, rgba(255,255,255,${0.26 * a}), rgba(255,255,255,${0.08 * a}))`;
  return (
    <div
      className="mb-3 rounded-[26px] p-3"
      style={{ background: "linear-gradient(135deg,#ff8a65,#7e57c2 50%,#26a69a)" }}
    >
      <div
        className="flex items-center gap-3 rounded-[24px] p-3.5"
        style={{
          background: bg,
          border: "1px solid rgba(255,255,255,.35)",
          boxShadow: "inset 0 1px 0 rgba(255,255,255,.4)",
        }}
      >
        <WeatherIcon icon="partly-day" size={46} />
        <div className="flex-1">
          <div className="text-[13px] font-semibold">Cape Town</div>
          <div className="text-[12px] text-white/85">Partly cloudy · H:24° L:15°</div>
        </div>
        <div className="text-[38px] leading-none font-light">21°</div>
      </div>
    </div>
  );
}

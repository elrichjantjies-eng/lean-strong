/** Saving and sharing files (backups, calendar files) on Android and the web. */
import { Directory, Encoding, Filesystem } from "@capacitor/filesystem";
import { Share } from "@capacitor/share";
import { isNative } from "./index";

export type ShareOutcome = "shared" | "downloaded" | "cancelled";

/**
 * Android: write the file and open the share sheet (Drive, WhatsApp, email, Files…).
 * Web: use the Web Share API with a file when the browser supports it (iPhone → Files,
 * WhatsApp, Mail), otherwise download it.
 */
export async function shareFile(
  name: string,
  text: string,
  mime: string,
  title: string,
): Promise<ShareOutcome> {
  if (isNative) {
    const written = await Filesystem.writeFile({
      path: name,
      data: text,
      directory: Directory.Cache,
      encoding: Encoding.UTF8,
    });
    try {
      await Share.share({ title, files: [written.uri], dialogTitle: title });
      return "shared";
    } catch {
      return "cancelled";
    }
  }

  const file = new File([text], name, { type: mime });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title });
      return "shared";
    } catch (err) {
      if ((err as DOMException).name === "AbortError") return "cancelled";
      // Fall through to a download if sharing failed for another reason.
    }
  }
  const url = URL.createObjectURL(file);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
  return "downloaded";
}

/** Read a file the user picked with <input type="file">. */
export function readFileText(file: File): Promise<string> {
  return file.text();
}

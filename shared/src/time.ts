const NEW_YORK = "America/New_York";

const dateFormat = new Intl.DateTimeFormat("en-CA", { timeZone: NEW_YORK, year: "numeric", month: "2-digit", day: "2-digit" });
const clockFormat = new Intl.DateTimeFormat("en-US", { timeZone: NEW_YORK, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

/** New York calendar date ("yyyy-MM-dd") of an ISO-8601 instant. */
export function etDate(iso: string): string {
  return dateFormat.format(new Date(iso));
}

/** "HH:MM ET", 24-hour clock. */
export function formatEtTime(iso: string): string {
  return `${clockFormat.format(new Date(iso))} ET`;
}

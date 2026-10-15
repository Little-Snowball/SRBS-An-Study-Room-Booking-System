/* Display helpers. All server times carry +08:00; render in Asia/Shanghai.
   No extra date library: formatting is done manually to keep the dependency tree frozen. */

function shanghaiParts(iso: string): { y: string; m: string; d: string; hh: string; mm: string } {
  const shifted = new Date(new Date(iso).getTime() + 8 * 3600 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return {
    y: String(shifted.getUTCFullYear()),
    m: pad(shifted.getUTCMonth() + 1),
    d: pad(shifted.getUTCDate()),
    hh: pad(shifted.getUTCHours()),
    mm: pad(shifted.getUTCMinutes())
  }
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const p = shanghaiParts(iso)
  return `${p.y}-${p.m}-${p.d} ${p.hh}:${p.mm}`
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const p = shanghaiParts(iso)
  return `${p.y}-${p.m}-${p.d}`
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const p = shanghaiParts(iso)
  return `${p.hh}:${p.mm}`
}

/** YYYY-MM-DD for a given instant in Asia/Shanghai. */
export function businessDateOf(iso: string): string {
  return formatDate(iso)
}

/** YYYY-MM-DD for today in Asia/Shanghai. */
export function todayBusinessDate(now: Date = new Date()): string {
  const shifted = new Date(now.getTime() + 8 * 3600 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`
}

/** Builds a +08:00 ISO string from a business date and HH:mm. */
export function toBusinessIso(date: string, time: string): string {
  return `${date}T${time}:00+08:00`
}

export function minutesBetween(startIso: string, endIso: string): number {
  return Math.round((Date.parse(endIso) - Date.parse(startIso)) / 60000)
}

export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00+08:00`)
  d.setDate(d.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

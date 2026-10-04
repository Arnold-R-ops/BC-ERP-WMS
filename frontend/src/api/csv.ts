import { saveBlob } from './files';

export type CsvCell = string | number | boolean | null | undefined;

// Quote every cell, preserve UTF-8 and neutralize text interpreted as spreadsheet formulas.
export function toCsv(rows: readonly (readonly CsvCell[])[]): string {
  return '\uFEFF' + rows.map((row) => row.map((value) => {
    let text = value == null ? '' : String(value);
    if (typeof value === 'string' && /^[\s\u0000-\u001f]*[=+\-@]/u.test(text)) text = "'" + text;
    return '"' + text.replaceAll('"', '""') + '"';
  }).join(',')).join('\r\n') + '\r\n';
}

export function downloadCsv(filename: string, rows: readonly (readonly CsvCell[])[]): void {
  saveBlob(new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' }), filename);
}

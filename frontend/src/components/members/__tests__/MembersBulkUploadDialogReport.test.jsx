import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * The import summary read fields the server has never sent.
 *
 * It asked for result.summary.totalRows, .created, .skipped, .rejected and
 * .failed. MemberImportResultDto carries totalProcessed, createdCount,
 * updatedCount, skippedCount and errorCount, and has no summary object at all
 * -- so every optional chain resolved to undefined and every `|| 0` printed a
 * zero. The panel had never shown a real number: a successful import of 160
 * rows reported the same four zeroes as a failed one.
 *
 * Nothing catches that at runtime. Optional chaining is designed to survive a
 * missing field quietly, and `|| 0` turns the silence into a plausible figure.
 * The only thing that can catch it is a check that the names on both sides
 * match, which is what this is.
 *
 * Reading the Java file as text is crude and deliberate: it costs nothing,
 * needs no build step, and fails on the day the two drift apart again.
 */

const DIALOG = resolve(__dirname, '../MembersBulkUploadDialog.jsx');
const RESULT_DTO = resolve(
  __dirname,
  '../../../../../backend/src/main/java/com/waad/tba/modules/member/dto/MemberImportResultDto.java'
);

function javaFields(source) {
  const withoutComments = source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/.*$/gm, '');
  return new Set(
    [...withoutComments.matchAll(/private\s+[\w.<>,\s]+?\s+(\w+)\s*;/g)].map((m) => m[1])
  );
}

describe('the import dialog and the result it renders', () => {
  const dialog = readFileSync(DIALOG, 'utf8');
  const fields = javaFields(readFileSync(RESULT_DTO, 'utf8'));

  it('reads only fields the result actually carries', () => {
    const read = new Set(
      [...dialog.matchAll(/\bresult\??\.(\w+)/g)].map((m) => m[1])
    );
    const unknown = [...read].filter((f) => !fields.has(f));

    expect(
      unknown,
      'a field the server never sends resolves to undefined, and the `|| 0` ' +
        'beside it turns that into a zero the reader has no reason to doubt'
    ).toEqual([]);
  });

  it('has no trace of the summary object that never existed', () => {
    expect(dialog).not.toMatch(/result\??\.summary/);
    expect(dialog).not.toMatch(/data\??\.summary/);
  });

  it('reads only error fields the result actually carries', () => {
    const errorFields = javaFields(readFileSync(RESULT_DTO, 'utf8'));
    const read = [...dialog.matchAll(/\berr\.(\w+)/g)].map((m) => m[1]);
    const unknown = read.filter((f) => !errorFields.has(f));

    // rowIdentifier, messageAr and value were read here for months, so the
    // reason column rendered empty even when the server had one.
    expect(unknown, 'the reason column is the whole point of that table').toEqual([]);
  });
});

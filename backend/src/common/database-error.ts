export function databaseErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '';
}

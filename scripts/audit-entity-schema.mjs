import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const migrationDir = path.join(
  root,
  "platform-infrastructure",
  "src",
  "main",
  "resources",
  "db",
  "migration",
);

function walk(directory, predicate) {
  const files = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === "target" || entry.name === "node_modules" || entry.name === ".git") {
      continue;
    }
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(fullPath, predicate));
    } else if (predicate(fullPath)) {
      files.push(fullPath);
    }
  }
  return files;
}

function stripSqlComments(sql) {
  return sql.replace(/\/\*[\s\S]*?\*\//g, "").replace(/--.*$/gm, "");
}

function normalizeIdentifier(identifier) {
  return identifier.replace(/["`]/g, "").split(".").at(-1).toLowerCase();
}

function splitTopLevel(value) {
  const parts = [];
  let start = 0;
  let depth = 0;
  let quote = null;
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (quote) {
      if (char === quote && value[index - 1] !== "\\") quote = null;
      continue;
    }
    if (char === "'" || char === '"') {
      quote = char;
    } else if (char === "(") {
      depth += 1;
    } else if (char === ")") {
      depth -= 1;
    } else if (char === "," && depth === 0) {
      parts.push(value.slice(start, index));
      start = index + 1;
    }
  }
  parts.push(value.slice(start));
  return parts;
}

function matchingParen(sql, openIndex) {
  let depth = 0;
  let quote = null;
  for (let index = openIndex; index < sql.length; index += 1) {
    const char = sql[index];
    if (quote) {
      if (char === quote && sql[index - 1] !== "\\") quote = null;
      continue;
    }
    if (char === "'" || char === '"') {
      quote = char;
    } else if (char === "(") {
      depth += 1;
    } else if (char === ")" && --depth === 0) {
      return index;
    }
  }
  throw new Error(`Unclosed CREATE TABLE at offset ${openIndex}`);
}

function parseSchema() {
  const tables = new Map();
  const migrationFiles = fs
    .readdirSync(migrationDir)
    .filter((name) => /^V\d+__.*\.sql$/.test(name))
    .sort((left, right) => {
      const leftVersion = Number(left.match(/^V(\d+)/)[1]);
      const rightVersion = Number(right.match(/^V(\d+)/)[1]);
      return leftVersion - rightVersion;
    });

  for (const migrationFile of migrationFiles) {
    const sql = stripSqlComments(fs.readFileSync(path.join(migrationDir, migrationFile), "utf8"));
    const createPattern = /create\s+table\s+(?:if\s+not\s+exists\s+)?([\w."`]+)\s*\(/gi;
    for (const match of sql.matchAll(createPattern)) {
      const table = normalizeIdentifier(match[1]);
      if (tables.has(table)) continue;
      const openIndex = match.index + match[0].lastIndexOf("(");
      const definition = sql.slice(openIndex + 1, matchingParen(sql, openIndex));
      const columns = new Set();
      for (const part of splitTopLevel(definition)) {
        const columnMatch = part.trim().match(/^(["`]?[a-zA-Z_][\w$]*["`]?)\s+/);
        if (!columnMatch) continue;
        const column = normalizeIdentifier(columnMatch[1]);
        if (!["primary", "constraint", "unique", "foreign", "check", "exclude"].includes(column)) {
          columns.add(column);
        }
      }
      tables.set(table, columns);
    }

    const addColumnPattern = /alter\s+table\s+(?:if\s+exists\s+)?([\w."`]+)\s+add\s+column\s+(?:if\s+not\s+exists\s+)?(["`]?[a-zA-Z_][\w$]*["`]?)/gi;
    for (const match of sql.matchAll(addColumnPattern)) {
      const table = normalizeIdentifier(match[1]);
      const column = normalizeIdentifier(match[2]);
      if (tables.has(table)) tables.get(table).add(column);
    }

    const renameColumnPattern = /alter\s+table\s+(?:if\s+exists\s+)?([\w."`]+)\s+rename\s+column\s+([\w."`]+)\s+to\s+([\w."`]+)/gi;
    for (const match of sql.matchAll(renameColumnPattern)) {
      const table = normalizeIdentifier(match[1]);
      const oldCol = normalizeIdentifier(match[2]);
      const newCol = normalizeIdentifier(match[3]);
      if (tables.has(table)) {
        tables.get(table).delete(oldCol);
        tables.get(table).add(newCol);
      }
    }
  }
  return tables;
}

function snakeCase(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/([A-Z])([A-Z][a-z])/g, "$1_$2")
    .toLowerCase();
}

function annotationColumn(annotations, annotation, attribute) {
  const argumentsMatch = annotations.match(new RegExp(`@${annotation}\\s*\\(([^)]*)\\)`, "s"));
  if (!argumentsMatch) return undefined;
  const argumentsValue = argumentsMatch[1];
  const namedValue = argumentsValue.match(new RegExp(`\\b${attribute}\\s*=\\s*"([^"]+)"`));
  return namedValue?.[1] ?? argumentsValue.match(/^\s*"([^"]+)"/)?.[1];
}

function parseEntity(file) {
  const source = fs.readFileSync(file, "utf8");
  const tableName =
    source.match(/@TableName\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/)?.[1] ??
    source.match(/@Table\s*\([^)]*?name\s*=\s*"([^"]+)"/s)?.[1];
  if (!tableName) return null;

  const columns = new Set();
  const fieldPattern = /((?:\s*@[\w.]+(?:\s*\([^)]*\))?\s*)*)\bprivate\s+(?!static\b)(?:final\s+)?[\w$.<>?, \[\]]+\s+(\w+)\s*(?:=[^;]*)?;/g;
  for (const match of source.matchAll(fieldPattern)) {
    const annotations = match[1];
    const property = match[2];
    if (/@Transient\b/.test(annotations) || /@TableField\s*\([^)]*exist\s*=\s*false/.test(annotations)) {
      continue;
    }
    const explicit =
      annotationColumn(annotations, "TableField", "value") ??
      annotationColumn(annotations, "TableId", "value") ??
      annotationColumn(annotations, "Column", "name");
    columns.add(normalizeIdentifier(explicit ?? snakeCase(property)));
  }

  return {
    table: normalizeIdentifier(tableName),
    file: path.relative(root, file).replaceAll(path.sep, "/"),
    columns,
  };
}

const schema = parseSchema();
const entities = walk(root, (file) => file.endsWith("Entity.java"))
  .map(parseEntity)
  .filter(Boolean)
  .sort((left, right) => left.table.localeCompare(right.table) || left.file.localeCompare(right.file));

let mismatchCount = 0;
const mappedTables = new Map();
for (const entity of entities) {
  if (mappedTables.has(entity.table)) {
    mismatchCount += 1;
    console.log(`DUPLICATE ENTITY MAPPING ${entity.table} <- ${entity.file} (already mapped by ${mappedTables.get(entity.table)})`);
  } else {
    mappedTables.set(entity.table, entity.file);
  }
  const tableColumns = schema.get(entity.table);
  if (!tableColumns) {
    mismatchCount += 1;
    console.log(`MISSING TABLE ${entity.table} <- ${entity.file}`);
    continue;
  }
  const entityOnly = [...entity.columns].filter((column) => !tableColumns.has(column)).sort();
  const tableOnly = [...tableColumns].filter((column) => !entity.columns.has(column)).sort();
  if (entityOnly.length === 0 && tableOnly.length === 0) continue;
  mismatchCount += 1;
  console.log(`${entity.table} <- ${entity.file}`);
  if (entityOnly.length > 0) console.log(`  entity-only: ${entityOnly.join(", ")}`);
  if (tableOnly.length > 0) console.log(`  table-only:  ${tableOnly.join(", ")}`);
}

for (const table of schema.keys()) {
  if (!mappedTables.has(table)) {
    mismatchCount += 1;
    console.log(`UNMAPPED FLYWAY TABLE ${table} (no *Entity.java maps to this table)`);
  }
}

console.log(`Checked ${entities.length} entities against ${schema.size} Flyway tables.`);
if (mismatchCount > 0) {
  console.error(`Found ${mismatchCount} entity/schema mismatches.`);
  process.exitCode = 1;
} else {
  console.log("All mapped entity columns match the Flyway schema (100% bidirectional alignment).");
}

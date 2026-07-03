import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const packageJson = JSON.parse(readFileSync("package.json", "utf8"));
const version = packageJson.version;

if (typeof version !== "string" || version.length === 0) {
  throw new Error("package.json version must be a non-empty string");
}

execFileSync(
  "mvn",
  [
    "--batch-mode",
    "versions:set",
    `-DnewVersion=${version}`,
    "-DgenerateBackupPoms=false",
  ],
  { stdio: "inherit" },
);

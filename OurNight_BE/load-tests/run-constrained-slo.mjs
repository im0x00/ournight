#!/usr/bin/env node

import { spawn } from "node:child_process";

const composeArgs = [
  "compose",
  "--project-name",
  "ournight-slo",
  "--file",
  "load-tests/docker-compose.constrained-slo.yml",
];

const limits = {
  SLO_APP_CPUS: process.env.SLO_APP_CPUS || "2.0",
  SLO_APP_MEMORY: process.env.SLO_APP_MEMORY || "1g",
  SLO_POSTGRES_CPUS: process.env.SLO_POSTGRES_CPUS || "1.0",
  SLO_POSTGRES_MEMORY: process.env.SLO_POSTGRES_MEMORY || "1g",
  SLO_REDIS_CPUS: process.env.SLO_REDIS_CPUS || "0.5",
  SLO_REDIS_MEMORY: process.env.SLO_REDIS_MEMORY || "256m",
};

let exitCode = 1;
try {
  await run("./gradlew", ["--no-daemon", "compileTestKotlin"], process.env);
  const buildArgs = process.env.SLO_REBUILD === "true" ? ["--build"] : [];
  await run("docker", [...composeArgs, "up", ...buildArgs, "--detach", "--wait"], {
    ...process.env,
    ...limits,
  });

  exitCode = await run(process.execPath, ["load-tests/ournight-boundary-load.mjs"], {
    ...process.env,
    ...limits,
    BASE_URL: process.env.BASE_URL || "http://127.0.0.1:18080",
    SCENARIO: process.env.SCENARIO || "performance",
    STORE_COUNT: process.env.STORE_COUNT || "100",
    TABLES_PER_STORE: process.env.TABLES_PER_STORE || "30",
    CUSTOMERS_PER_STORE: process.env.CUSTOMERS_PER_STORE || "1",
    POS_CONNECTIONS_PER_STORE: process.env.POS_CONNECTIONS_PER_STORE || "4",
    POS_COMMAND_SAMPLES: process.env.POS_COMMAND_SAMPLES || "100",
    POS_COMMAND_ARRIVAL_INTERVAL_MS: process.env.POS_COMMAND_ARRIVAL_INTERVAL_MS || "200",
    POSTGRES_CONTAINER: "ournight-slo-postgres",
    POSTGRES_DB: "ournight_slo",
    POSTGRES_USER: "ournight",
    REDIS_CONTAINER: "ournight-slo-redis",
    APP_CONTAINER: "ournight-slo-app",
  }, false);
} finally {
  if (process.env.KEEP_SLO_STACK !== "true") {
    await run("docker", [...composeArgs, "down", "--volumes", "--remove-orphans"], process.env, false);
  }
}

process.exitCode = exitCode;

function run(command, args, env, rejectOnFailure = true) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { env, stdio: "inherit" });
    child.on("error", reject);
    child.on("close", (code) => {
      const actual = code ?? 1;
      if (actual !== 0 && rejectOnFailure) {
        reject(new Error(`${command} exited with ${actual}`));
      } else {
        resolve(actual);
      }
    });
  });
}

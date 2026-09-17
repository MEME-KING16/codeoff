import { Elysia, t } from 'elysia'
import { Database } from 'bun:sqlite';

const db = new Database('users.db');

type QueuedPlayer = { uuid: string; mode: string; ws: any };
type Match = { matchId: string; players: { uuid: string; ws: any }[]; prompt: string; solution: string };

interface JavaExecutionResult { success: boolean; output: string; error?: string; }

const matchmakingQueue: QueuedPlayer[] = [];
const matches: Match[] = [];
const nim = Bun.argv.includes("--nim");

const javaResult = await executeJavaInPod("public class Test { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }");
console.log("Java execution result:", javaResult);
db.run(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    uuid TEXT NOT NULL UNIQUE
  )
`);

const app = new Elysia()
  .ws('/', {
    body: t.Object({
      type: t.String(),
      mode: t.Optional(t.String()),
      uuid: t.Optional(t.String()),
      solution: t.Optional(t.String()),
      matchId: t.Optional(t.String())
    }),
    open(ws) {
      console.log('Client connected');
      ws.send(JSON.stringify({ type:"uuid", uuid: crypto.randomUUID() }));
    },
    message(ws, data) {
      if (data.type === 'matchmake') {
        if (!data.uuid || !data.mode) {
          ws.send(JSON.stringify({ type: 'error', message: 'Missing uuid or mode' }));
          return;
        }

        const uuid = data.uuid;
        const mode = data.mode;

        console.log(`Matchmake request: mode=${mode}, uuid=${uuid}`);
        ws.send(JSON.stringify({ type: 'matchmake_ack', mode }));
        matchmakingQueue.push({ uuid, mode, ws });

        if (matchmakingQueue.filter(player => player.mode === mode).length >= 2) {
          const players = matchmakingQueue.filter(player => player.mode === mode).slice(0, 2);
          matchmakingQueue.splice(0, 2);

          const matchId = crypto.randomUUID();
          console.log('Match found for uuid:', players.map(p => p.uuid));
          players.forEach(player => {
            player.ws.send(JSON.stringify({ type: 'match_found', matchId }));
          });
          matches.push({ matchId, players: [{ uuid, ws }], prompt: '', solution: '' });
          startMatch(matchId);
        }
      } else if (data.type === 'submit_solution') {

        if (!data.matchId) {
          ws.send(JSON.stringify({ type: 'error', message: 'Missing matchId' }));
          return;
        }
        const match = matches.find(m => m.matchId === data.matchId);
        if (!match) {
          ws.send(JSON.stringify({ type: 'error', message: `Match with ID ${data.matchId} not found` }));
          return;
        }

        if (!data.solution) {
          ws.send(JSON.stringify({ type: 'error', message: 'Missing solution' }));
          return;
        }


        validateJavaCode(data.solution, match.solution).then((number: number) => {
          console.log(`Solution submitted for match ${data.matchId} with score: ${number}`);
          match.players.forEach(player => {
            player.ws.send(JSON.stringify({ type: 'solution_result', matchId: data.matchId, score: number }));
          });
        }).catch(err => {
          console.error('Error validating solution:', err);
          ws.send(JSON.stringify({ type: 'error', message: 'Error validating solution' }));
        });
      } else {
        ws.send(JSON.stringify({ type: 'error', message: `Unknown type: ${data.type}` }));
      }
    },
    close(ws) {
      console.log('Client disconnected');
    }
  })
  .listen({ port: 3000, hostname: '0.0.0.0' });

console.log(`Server running at ${app.server?.hostname}:${app.server?.port}`)


async function startMatch(matchId: string) {
  const match = matches.find(m => m.matchId === matchId);
  if (!match) {
    console.error(`Match with ID ${matchId} not found`);
    return;
  }

  const challenge = await generateChallenge();
  match.players.forEach(player => {
    player.ws.send(JSON.stringify({ type: 'start_match', matchId, prompt: challenge.prompt }));
    match.solution = challenge.solution;
    match.prompt = challenge.prompt;
  });
}


type Challenge = {
  prompt: string;
  solution: string;
};

async function generateChallenge(): Promise<Challenge> {
  const promptResponse = await fetch(nim ? "https://integrate.api.nvidia.com/v1/chat/completions" : "http://localhost:1234/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": nim ? `Bearer ${Bun.env.NVIDIA_API_KEY}` : `Lmstudio`,
    },
    body: JSON.stringify({
      model: nim ? "nvidia/nemotron-3-ultra-550b-a55b" : "qwen3.5-9b",
      messages: [
        {
          role: "system",
          content: "You generate short Java coding challenges for a competitive coding game. Make the prompt relatively short. Respond ONLY with valid JSON, no markdown, no explanation, no code fences. Format: { \"prompt\": \"prompt including function signature\" }. 1 phrase for the prompt. in the prompt, include the function signature."
        },
        {
          role: "user",
          content: "Generate a simple coding challenge suitable for a casual match, solvable in under 5 minutes."
        }
      ],
      temperature: 1.5
    })
  });

  const promptData = await promptResponse.json();
  const promptRaw = promptData.choices[0].message.content;

  let promptParsed: { prompt: string };
  try {
    promptParsed = JSON.parse(promptRaw);
  } catch (err) {
    console.error("Failed to parse prompt JSON:", promptRaw);
    throw err;
  }

  const solutionResponse = await fetch(nim ? "https://integrate.api.nvidia.com/v1/chat/completions" : "http://localhost:1234/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": nim ? `Bearer ${Bun.env.NVIDIA_API_KEY}` : `Lmstudio`,
    },
    body: JSON.stringify({
      model: nim ? "nvidia/nemotron-3-ultra-550b-a55b" : "qwen3.5-9b",
      messages: [
        {
          role: "system",
          content: "You solve simple coding challenges just give the code NO explanation, no markdown, no comments. Respond ONLY with valid JSON, no markdown, no explanation, no code fences. Format: { \"solution\": \"code\" }."
        },
        {
          role: "user",
          content: "Prompt: " + promptParsed.prompt
        }
      ],
      temperature: 0.5
    })
  });

  const solutionData = await solutionResponse.json();
  const solutionRaw = solutionData.choices[0].message.content;

  let solutionParsed: { solution: string };
  try {
    solutionParsed = JSON.parse(solutionRaw);
  } catch (err) {
    console.error("Failed to parse solution JSON:", solutionRaw);
    throw err;
  }

  return {
    prompt: promptParsed.prompt,
    solution: solutionParsed.solution
  };
}


async function validateJavaCode(javaCode: string, Prompt: string): Promise<number> {
  const result = await executeJavaInPod(javaCode);
  if (!result.success) {
    console.error("Java execution failed:", result.error);
    return 0;
  }
  const score = await fetch(nim ? "https://integrate.api.nvidia.com/v1/chat/completions" : "http://localhost:1234/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": nim ? `Bearer ${Bun.env.NVIDIA_API_KEY}` : `Lmstudio`,
    },
    body: JSON.stringify({
      model: nim ? "nvidia/nemotron-3-ultra-550b-a55b" : "qwen3.5-9b",
      messages: [
        {
          role: "system",
          content: "You are a judge for a competitive coding game. You grade the output of a submitted Java code. Respond ONLY with valid JSON, no markdown, no explanation, no code fences. Format: { \"score\": 0-1 } (a number from 0-1 1 being perfect 0 being not even close. You check for a: following the prompt if this isnt met thats an auto 0, b: correctness of the output, c: efficiency of the code, d: code style and readability. You will give a score based on these factors.)"
        },
        {
          role: "user",
          content: `Prompt: ${Prompt}, Code Output: ${result.output}, Code: \`\`\`java\n${javaCode}\n\`\`\``
        }
      ],
      temperature: 0.0
    })
  });

  const scoreData = await score.json();
  const scoreParsed = JSON.parse(scoreData.choices[0].message.content);
  return scoreParsed.score
}




function parseJavaSource(javaCode: string): {
  className: string;
  fileName: string;
  fullClassName: string;
} {
  const cleaned = javaCode.replace(
    /("""[\s\S]*?"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*')|(\/\*[\s\S]*?\*\/|\/\/[^\r\n]*)/g,
    (match, literal) => (literal ? '""' : " ")
  );

  const packageMatch = cleaned.match(
    /\bpackage\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)*)\s*;/
  );
  const packageName = packageMatch ? packageMatch[1] : undefined;

  let className: string | undefined;
  const publicTypeMatch =
    cleaned.match(
      /\bpublic\s+(?:(?:final|abstract|sealed|non-sealed|strictfp)\s+)*(?:class|record|enum|interface)\s+([A-Za-z0-9_$]+)\b/
    ) ||
    cleaned.match(
      /\b(?:(?:final|abstract|sealed|non-sealed|strictfp)\s+)+public\s+(?:class|record|enum|interface)\s+([A-Za-z0-9_$]+)\b/
    );

  if (publicTypeMatch?.[1]) {
    className = publicTypeMatch[1];
  }

  if (!className) {
    const mainRegex = /\b(?:(?:public\s+static|static\s+public|static)\s+void|void)\s+main\s*\(/g;
    let mainMatch: RegExpExecArray | null;

    while ((mainMatch = mainRegex.exec(cleaned)) !== null) {
      const codeBeforeMain = cleaned.slice(0, mainMatch.index);
      const classDeclarations = [
        ...codeBeforeMain.matchAll(/\b(?:class|record|enum)\s+([A-Za-z0-9_$]+)\s*\{/g),
      ];

      for (let i = classDeclarations.length - 1; i >= 0; i--) {
        const classDecl = classDeclarations[i];
        const classStart = classDecl.index;
        const segment = codeBeforeMain.slice(classStart);

        let depth = 0;
        for (const char of segment) {
          if (char === "{") depth++;
          else if (char === "}") depth--;
        }

        if (depth > 0) {
          className = classDecl[1];
          break;
        }
      }
      if (className) break;
    }
  }

  if (!className) {
    const anyClassMatch = cleaned.match(/\b(?:class|record|enum)\s+([A-Za-z0-9_$]+)\b/);
    if (anyClassMatch?.[1]) {
      className = anyClassMatch[1];
    }
  }

  if (!className) {
    className = "Main";
  }

  const fileName = `${className}.java`;
  const fullClassName = packageName ? `${packageName}.${className}` : className;

  return { className, fileName, fullClassName };
}

async function forceKillContainer(containerName: string): Promise<void> {
  try {
    const kill = Bun.spawn(["podman", "rm", "-f", containerName], {
      stdout: "ignore",
      stderr: "ignore",
    });
    await kill.exited;
  } catch {

    }
}

/**
 * @author the big google search gemini thingy + the real gemini cause it acc can do stuff (i was too lazy to rewrite this myself for podman)
 */
export async function executeJavaInPod(javaCode: string): Promise<JavaExecutionResult> {
  const uniqueId = `java-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const containerName = `run-${uniqueId}`;
  const imageName = "docker.io/library/eclipse-temurin:21-alpine";

  const { fileName, fullClassName } = parseJavaSource(javaCode);

  let timer: ReturnType<typeof setTimeout> | undefined;
  let javaProcess: ReturnType<typeof Bun.spawn> | undefined;

  try {
    javaProcess = Bun.spawn({
      cmd: [
        "podman", "run",
        "--rm",
        "-i",
        "--name", containerName,
        "-w", "/tmp",
        "--network=none",
        "--memory=256m",
        "--cpus=1",
        "--pids-limit=64",
        "--security-opt=no-new-privileges",
        imageName,
        "sh", "-c",
        'cat > "$1" && javac --enable-preview --release 21 -d . "$1" && java --enable-preview "$2"',
        "sh",
        fileName,
        fullClassName,
      ],
      stdin: "pipe",
      stdout: "pipe",
      stderr: "pipe",
    });

    const stdoutPromise = new Response(javaProcess.stdout).text();
    const stderrPromise = new Response(javaProcess.stderr).text();

    try {
      javaProcess.stdin.write(javaCode);
      javaProcess.stdin.end();
    } catch {

    }

    const timeoutPromise = new Promise<never>((_, reject) => {
      timer = setTimeout(() => {
        reject(new Error("Timeout: Java execution exceeded 5 seconds"));
      }, 5000);
    });

    const exitCode = await Promise.race([javaProcess.exited, timeoutPromise]);

    const [stdout, stderr] = await Promise.all([stdoutPromise, stderrPromise]);

    return {
      success: exitCode === 0,
      output: stdout.trim(),
      error: stderr.trim() || (exitCode !== 0 ? `Process exited with code ${exitCode}` : undefined),
    };
  } catch (error: any) {
    if (javaProcess) {
      try {
        javaProcess.kill();
      } catch {}
    }

    await forceKillContainer(containerName);

    return {
      success: false,
      output: "",
      error: error.message || String(error),
    };
  } finally {
    if (timer) clearTimeout(timer);
  }
}
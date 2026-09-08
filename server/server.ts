import { Elysia, t } from 'elysia'
import { Database } from 'bun:sqlite';

const db = new Database('users.db');

type QueuedPlayer = { uuid: string; mode: string; ws: any };
type Match = { matchId: string; players: { uuid: string; ws: any }[]; prompt: string; solution: string };

const matchmakingQueue: QueuedPlayer[] = [];
const matches: Match[] = [];

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
      uuid: t.Optional(t.String())
    }),
    open(ws) {
      console.log('Client connected');
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
  const promptResponse = await fetch("http://localhost:1234/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Lmstudio`,
    },
    body: JSON.stringify({
      model: "qwen3.5-9b",
      messages: [
        {
          role: "system",
          content: "You generate short Java coding challenges for a competitive coding game. Respond ONLY with valid JSON, no markdown, no explanation, no code fences. Format: { \"prompt\": \"prompt including function signature\" }. 1 phrase for the prompt. in the prompt, include the function signature."
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

  const solutionResponse = await fetch("http://localhost:1234/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Lmstudio`,
    },
    body: JSON.stringify({
      model: "qwen3.5-9b",
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
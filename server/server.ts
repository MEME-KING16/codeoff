import { Elysia, t } from 'elysia'
// import { Database } from 'bun:sqlite';

// const db = new Database('users.db');

// db.run(`
//   CREATE TABLE IF NOT EXISTS users (
//     id INTEGER PRIMARY KEY AUTOINCREMENT,
//     name TEXT NOT NULL
//   )
// `);

const app = new Elysia()
  .ws('/', {
    body: t.Object({
      message: t.String()
    }),
    open(ws) {
      console.log('Client connected')
    //   ws.send('Hello from server!')
    },
    message(ws, { message }) {
      ws.send(`Echo: ${message}`)
    },
    close(ws) {
      console.log('Client disconnected')
    }
  })
  .listen({port: 3000, hostname: '0.0.0.0'});

console.log(`Server running at ${app.server?.hostname}:${app.server?.port}`)
package dev.pocketagent.core.storage

import java.io.File

enum class ProjectTemplate(
    val id: String,
    val displayName: String,
    val description: String
) {
    STATIC_WEB(
        id = "static_web",
        displayName = "Static Web (HTML/CSS/JS)",
        description = "Lightweight responsive web app with vanilla JS, HTML5, and CSS3."
    ),
    REACT_VITE(
        id = "react_vite",
        displayName = "React + Vite",
        description = "Modern React single page application with Vite dev server."
    ),
    NODE_EXPRESS(
        id = "node_express",
        displayName = "Node.js + Express API",
        description = "RESTful HTTP backend service with Express.js routes."
    ),
    PYTHON_FASTAPI(
        id = "python_fastapi",
        displayName = "Python FastAPI Service",
        description = "High-performance Python API with automatic OpenAPI docs."
    ),
    EMPTY(
        id = "empty",
        displayName = "Empty Project",
        description = "Blank workspace initialized with README and agent policy."
    )
}

class TemplateManager {

    fun applyTemplate(projectDir: File, template: ProjectTemplate) {
        projectDir.mkdirs()
        when (template) {
            ProjectTemplate.STATIC_WEB -> applyStaticWebTemplate(projectDir)
            ProjectTemplate.REACT_VITE -> applyReactViteTemplate(projectDir)
            ProjectTemplate.NODE_EXPRESS -> applyNodeExpressTemplate(projectDir)
            ProjectTemplate.PYTHON_FASTAPI -> applyPythonFastApiTemplate(projectDir)
            ProjectTemplate.EMPTY -> applyEmptyTemplate(projectDir)
        }
    }

    private fun applyStaticWebTemplate(dir: File) {
        File(dir, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>PocketAgent Web App</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <main class="container">
                    <header>
                        <h1>Welcome to PocketAgent</h1>
                        <p class="subtitle">Built autonomously on Android</p>
                    </header>
                    <section class="card">
                        <h2>Interactive Counter</h2>
                        <div class="counter-display" id="count">0</div>
                        <div class="actions">
                            <button id="dec-btn" class="btn btn-secondary">-</button>
                            <button id="inc-btn" class="btn btn-primary">+</button>
                        </div>
                    </section>
                </main>
                <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent()
        )

        File(dir, "style.css").writeText(
            """
            :root {
                --primary: #4F46E5;
                --primary-hover: #4338CA;
                --bg: #0F172A;
                --card-bg: #1E293B;
                --text: #F8FAFC;
                --text-muted: #94A3B8;
            }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                background-color: var(--bg);
                color: var(--text);
                min-height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 1rem;
            }
            .container { width: 100%; max-width: 480px; text-align: center; }
            header { margin-bottom: 2rem; }
            h1 { font-size: 2rem; font-weight: 700; margin-bottom: 0.5rem; }
            .subtitle { color: var(--text-muted); font-size: 1rem; }
            .card {
                background: var(--card-bg);
                border-radius: 1rem;
                padding: 2rem;
                box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
            }
            .counter-display {
                font-size: 4rem;
                font-weight: 800;
                margin: 1.5rem 0;
                color: var(--primary);
            }
            .actions { display: flex; gap: 1rem; justify-content: center; }
            .btn {
                padding: 0.75rem 1.5rem;
                border-radius: 0.5rem;
                border: none;
                font-size: 1.25rem;
                cursor: pointer;
                transition: background-color 0.2s;
            }
            .btn-primary { background: var(--primary); color: white; }
            .btn-primary:hover { background: var(--primary-hover); }
            .btn-secondary { background: #334155; color: white; }
            .btn-secondary:hover { background: #475569; }
            """.trimIndent()
        )

        File(dir, "app.js").writeText(
            """
            let count = 0;
            const display = document.getElementById('count');
            document.getElementById('inc-btn').addEventListener('click', () => {
                count++;
                display.textContent = count;
            });
            document.getElementById('dec-btn').addEventListener('click', () => {
                count--;
                display.textContent = count;
            });
            console.log("PocketAgent web app mounted successfully.");
            """.trimIndent()
        )

        File(dir, "README.md").writeText(
            """
            # Static Web Project
            Created with PocketAgent.
            Open in preview with `python3 -m http.server 3000` or `npx serve .`.
            """.trimIndent()
        )
    }

    private fun applyReactViteTemplate(dir: File) {
        File(dir, "package.json").writeText(
            """
            {
              "name": "${dir.name.lowercase().replace(Regex("[^a-z0-9]"), "-")}",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite --port 3000 --host",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              },
              "devDependencies": {
                "@vitejs/plugin-react": "^4.2.1",
                "vite": "^5.1.4"
              }
            }
            """.trimIndent()
        )

        File(dir, "vite.config.js").writeText(
            """
            import { defineConfig } from 'vite';
            import react from '@vitejs/plugin-react';

            export default defineConfig({
              plugins: [react()],
              server: {
                port: 3000,
                host: true
              }
            });
            """.trimIndent()
        )

        File(dir, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>React + Vite on PocketAgent</title>
              </head>
              <body>
                <div id="root"></div>
                <script type="module" src="/src/main.jsx"></script>
              </body>
            </html>
            """.trimIndent()
        )

        val srcDir = File(dir, "src").apply { mkdirs() }
        File(srcDir, "main.jsx").writeText(
            """
            import React from 'react';
            import ReactDOM from 'react-dom/client';
            import App from './App.jsx';
            import './index.css';

            ReactDOM.createRoot(document.getElementById('root')).render(
              <React.StrictMode>
                <App />
              </React.StrictMode>
            );
            """.trimIndent()
        )

        File(srcDir, "App.jsx").writeText(
            """
            import React, { useState } from 'react';

            export default function App() {
              const [count, setCount] = useState(0);

              return (
                <div className="card">
                  <h1>PocketAgent + React</h1>
                  <p>Fast mobile-native web development</p>
                  <button onClick={() => setCount(c => c + 1)}>
                    Count is: {count}
                  </button>
                </div>
              );
            }
            """.trimIndent()
        )

        File(srcDir, "index.css").writeText(
            """
            body {
              margin: 0;
              display: flex;
              place-items: center;
              min-width: 320px;
              min-height: 100vh;
              background-color: #242424;
              color: rgba(255, 255, 255, 0.87);
              font-family: system-ui, sans-serif;
              justify-content: center;
            }
            .card {
              text-align: center;
              padding: 2em;
            }
            button {
              border-radius: 8px;
              border: 1px solid transparent;
              padding: 0.6em 1.2em;
              font-size: 1em;
              font-weight: 500;
              font-family: inherit;
              background-color: #1a1a1a;
              cursor: pointer;
              transition: border-color 0.25s;
              color: white;
            }
            button:hover {
              border-color: #646cff;
            }
            """.trimIndent()
        )

        File(dir, ".gitignore").writeText("node_modules/\ndist/\n.DS_Store\n")
        File(dir, "README.md").writeText("# React + Vite Project\nRun `npm install && npm run dev` to preview.")
    }

    private fun applyNodeExpressTemplate(dir: File) {
        File(dir, "package.json").writeText(
            """
            {
              "name": "${dir.name.lowercase().replace(Regex("[^a-z0-9]"), "-")}",
              "version": "1.0.0",
              "main": "server.js",
              "scripts": {
                "start": "node server.js"
              },
              "dependencies": {
                "express": "^4.19.2",
                "cors": "^2.8.5"
              }
            }
            """.trimIndent()
        )

        File(dir, "server.js").writeText(
            """
            const express = require('express');
            const cors = require('cors');
            const app = express();
            const PORT = process.env.PORT || 3000;

            app.use(cors());
            app.use(express.json());

            let todos = [
              { id: 1, title: "Initialize PocketAgent", completed: true },
              { id: 2, title: "Build Node.js API", completed: false }
            ];

            app.get('/api/health', (req, res) => {
              res.json({ status: "healthy", timestamp: Date.now() });
            });

            app.get('/api/todos', (req, res) => {
              res.json(todos);
            });

            app.post('/api/todos', (req, res) => {
              const { title } = req.body;
              if (!title) return res.status(400).json({ error: "Title is required" });
              const newTodo = { id: Date.now(), title, completed: false };
              todos.push(newTodo);
              res.status(201).json(newTodo);
            });

            app.listen(PORT, '0.0.0.0', () => {
              console.log(`Express server listening on http://localhost:${'$'}{PORT}`);
            });
            """.trimIndent()
        )

        File(dir, ".gitignore").writeText("node_modules/\n.env\n")
        File(dir, "README.md").writeText("# Node.js Express API\nRun `npm install && npm start` to launch API.")
    }

    private fun applyPythonFastApiTemplate(dir: File) {
        File(dir, "main.py").writeText(
            """
            from fastapi import FastAPI
            from pydantic import BaseModel
            from typing import List

            app = FastAPI(title="PocketAgent API")

            class Item(BaseModel):
                id: int
                name: str
                done: bool = False

            db: List[Item] = [Item(id=1, name="Setup Python in proot", done=True)]

            @app.get("/")
            def read_root():
                return {"message": "Hello from PocketAgent FastAPI server"}

            @app.get("/items", response_model=List[Item])
            def get_items():
                return db

            @app.post("/items", response_model=Item)
            def create_item(item: Item):
                db.append(item)
                return item
            """.trimIndent()
        )

        File(dir, "requirements.txt").writeText("fastapi\nuvicorn\npydantic\n")
        File(dir, ".gitignore").writeText("__pycache__/\n*.pyc\n.venv/\n")
        File(dir, "README.md").writeText("# Python FastAPI Server\nRun `uvicorn main:app --host 0.0.0.0 --port 8000`.")
    }

    private fun applyEmptyTemplate(dir: File) {
        File(dir, "README.md").writeText("# ${dir.name}\nEmpty project created in PocketAgent.")
        File(dir, ".gitignore").writeText(".DS_Store\n*.log\n")
        val agentDir = File(dir, ".agent").apply { mkdirs() }
        File(agentDir, "policy.json").writeText(
            """
            {
              "allowCommands": ["npm test", "python3 -m unittest", "git status"],
              "denyCommands": ["rm -rf /", "git push --force"]
            }
            """.trimIndent()
        )
    }
}

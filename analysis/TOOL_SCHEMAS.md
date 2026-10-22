# PocketAgent Tool Schemas (JSON Schema Specification)

This specification defines the neutral, provider-independent tool interfaces used by the **PocketAgent** runtime on Android. All model providers (Gemini, Anthropic, OpenAI-compatible) translate their function call representations into these schema instances.

---

## 1. Filesystem Tools

### `fs.read_file`
Reads the content of a file within the workspace.
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "fs_read_file",
  "description": "Read file contents from the workspace. Supports start/end line slice notation.",
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "description": "Relative path to the file within the active workspace."
    },
    "start_line": {
      "type": "integer",
      "description": "Optional 1-indexed starting line to read from.",
      "minimum": 1
    },
    "end_line": {
      "type": "integer",
      "description": "Optional 1-indexed ending line to read to (inclusive).",
      "minimum": 1
    }
  },
  "required": ["path"]
}
```

### `fs.write_file`
Creates or overwrites a file.
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "fs_write_file",
  "description": "Create a new file or overwrite an existing file in the workspace.",
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "description": "Relative path to the file in the workspace."
    },
    "content": {
      "type": "string",
      "description": "Full file content to write."
    },
    "overwrite": {
      "type": "boolean",
      "description": "Must be set to true if replacing an existing file."
    }
  },
  "required": ["path", "content"]
}
```

### `fs.edit_file`
Applies surgical search-and-replace edits to a file.
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "fs_edit_file",
  "description": "Perform precise search-and-replace modification to an existing workspace file.",
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "description": "Relative path of the target file."
    },
    "target_content": {
      "type": "string",
      "description": "Exact text substring to be replaced."
    },
    "replacement_content": {
      "type": "string",
      "description": "New replacement content."
    },
    "allow_multiple": {
      "type": "boolean",
      "default": false,
      "description": "Whether to replace multiple occurrences if found."
    }
  },
  "required": ["path", "target_content", "replacement_content"]
}
```

### `fs.list_dir`
Lists entries in a directory.
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "fs_list_dir",
  "description": "List directory contents including child files and directories.",
  "type": "object",
  "properties": {
    "path": {
      "type": "string",
      "description": "Relative path of directory to list (leave empty for workspace root)."
    },
    "recursive": {
      "type": "boolean",
      "default": false,
      "description": "Whether to recursively list all nested subdirectories."
    }
  }
}
```

---

## 2. Search Tools

### `search.grep`
Performs fast regex or literal text search across files.
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "search_grep",
  "description": "Search for exact string patterns or regex matches across workspace files.",
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "Search pattern or string."
    },
    "is_regex": {
      "type": "boolean",
      "default": false,
      "description": "Treat query as a regular expression."
    },
    "case_insensitive": {
      "type": "boolean",
      "default": false,
      "description": "Perform case-insensitive search."
    },
    "path": {
      "type": "string",
      "description": "Subdirectory to restrict search to."
    },
    "file_glob": {
      "type": "string",
      "description": "Glob pattern to filter filenames (e.g. *.kt, *.ts)."
    }
  },
  "required": ["query"]
}
```

---

## 3. Terminal & Sandbox Tools

### `terminal.run`
Runs a command inside the sandboxed Linux environment (proot / PTY).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "terminal_run",
  "description": "Execute a shell command in the workspace Linux environment.",
  "type": "object",
  "properties": {
    "command": {
      "type": "string",
      "description": "Exact shell command to run."
    },
    "cwd": {
      "type": "string",
      "description": "Working directory relative to workspace root."
    },
    "timeout_ms": {
      "type": "integer",
      "default": 30000,
      "description": "Maximum execution time before sending SIGTERM/SIGKILL."
    },
    "background": {
      "type": "boolean",
      "default": false,
      "description": "Run process as a persistent background daemon."
    }
  },
  "required": ["command"]
}
```

---

## 4. Artifact & Collaboration Tools

### `artifact.write`
Saves or updates a structured artifact (e.g. Plan, Task Checklist, Walkthrough).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "artifact_write",
  "description": "Create or update an agent artifact (implementation plan, tasks checklist, or walkthrough report).",
  "type": "object",
  "properties": {
    "type": {
      "type": "string",
      "enum": ["plan", "tasks", "walkthrough", "diff"],
      "description": "The category of artifact."
    },
    "title": {
      "type": "string",
      "description": "Display title of the artifact."
    },
    "content": {
      "type": "string",
      "description": "Markdown formatted content of the artifact."
    },
    "requires_feedback": {
      "type": "boolean",
      "default": false,
      "description": "If true, pauses agent loop to solicit user review/proceed action."
    }
  },
  "required": ["type", "title", "content"]
}
```

### `ask_user`
Prompts the human user with a multiple-choice question or clarification input.
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "ask_user",
  "description": "Ask the user a clarifying question with options or freeform text.",
  "type": "object",
  "properties": {
    "question": {
      "type": "string",
      "description": "The question to present."
    },
    "options": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Suggested choices for quick selection."
    },
    "allow_custom_input": {
      "type": "boolean",
      "default": true,
      "description": "Allow freeform text response."
    }
  },
  "required": ["question"]
}
```

---

### `browser.open`
Navigates the internal preview WebView (Policy: auto on localhost, ask elsewhere).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "browser_open",
  "description": "Open a URL in the in-app browser or preview WebView (e.g. http://localhost:3000).",
  "type": "object",
  "properties": {
    "url": {
      "type": "string",
      "description": "URL to navigate to (e.g. http://localhost:3000)."
    }
  },
  "required": ["url"]
}
```

### `browser.screenshot`
Captures a screenshot of the current preview browser state (Policy: auto/read-only).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "browser_screenshot",
  "description": "Capture a screenshot of the rendered preview screen and save as artifact under screens/.",
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "description": "Optional name for screenshot artifact."
    }
  }
}
```

### `browser.click`
Clicks an element by CSS selector (Policy: auto on localhost, ask elsewhere).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "browser_click",
  "description": "Click on an element in the browser page matching a CSS selector.",
  "type": "object",
  "properties": {
    "selector": {
      "type": "string",
      "description": "CSS selector of the element to click."
    }
  },
  "required": ["selector"]
}
```

### `browser.type`
Types text into an input element by CSS selector (Policy: auto on localhost, ask elsewhere).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "browser_type",
  "description": "Type text into an input or textarea element matching a CSS selector.",
  "type": "object",
  "properties": {
    "selector": { "type": "string", "description": "CSS selector of the input element" },
    "text": { "type": "string", "description": "Text to type into the input" },
    "clear": { "type": "boolean", "description": "Whether to clear existing text before typing" }
  },
  "required": ["selector", "text"]
}
```

### `browser.eval`
Evaluates JavaScript in the browser context (Policy: auto/read-only).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "browser_eval",
  "description": "Evaluate a JavaScript expression in the browser page context and return the result.",
  "type": "object",
  "properties": {
    "script": { "type": "string", "description": "JavaScript code to execute in the page" }
  },
  "required": ["script"]
}
```

### `browser.console`
Retrieves captured browser console logs (Policy: auto/read-only).
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "name": "browser_console",
  "description": "Retrieve recent console logs (log, warn, error) captured from the browser page.",
  "type": "object",
  "properties": {
    "limit": { "type": "integer", "description": "Maximum number of logs to return (default 50)" },
    "level": { "type": "string", "description": "Optional level filter: ALL, LOG, INFO, WARN, ERROR (default ALL)" }
  }
}
```

---

## 6. MCP External Tools

### `mcp.<server>.<tool>`
Dynamic external tools provided by connected Model Context Protocol servers over HTTP/SSE or STDIO.
- **Naming format:** `mcp.<server_name>.<tool_name>`
- **Policy:** Per-server policy configured in MCP management UI (`ALLOW`, `ASK`, `DENY`).
- **Security Boundary:** All output returned by MCP tools is framed as untrusted external content to mitigate prompt-injection vulnerabilities:
  ```
  [MCP Tool Result from '<server>/<tool>'] (Untrusted external content):
  <raw_tool_output>
  ```


package dev.pocketagent.core.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TemplateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var templateManager: TemplateManager
    private lateinit var baseDir: File

    @Before
    fun setup() {
        templateManager = TemplateManager()
        baseDir = tempFolder.newFolder("templates_test")
    }

    @Test
    fun testStaticWebTemplate() {
        val proj = File(baseDir, "web_app")
        templateManager.applyTemplate(proj, ProjectTemplate.STATIC_WEB)

        assertThat(File(proj, "index.html").exists()).isTrue()
        assertThat(File(proj, "index.html").readText()).contains("<title>PocketAgent Web App</title>")
        assertThat(File(proj, "style.css").exists()).isTrue()
        assertThat(File(proj, "app.js").exists()).isTrue()
        assertThat(File(proj, "README.md").exists()).isTrue()
    }

    @Test
    fun testReactViteTemplate() {
        val proj = File(baseDir, "react_app")
        templateManager.applyTemplate(proj, ProjectTemplate.REACT_VITE)

        assertThat(File(proj, "package.json").exists()).isTrue()
        assertThat(File(proj, "package.json").readText()).contains("\"vite\"")
        assertThat(File(proj, "vite.config.js").exists()).isTrue()
        assertThat(File(proj, "src/App.jsx").exists()).isTrue()
        assertThat(File(proj, "src/main.jsx").exists()).isTrue()
    }

    @Test
    fun testNodeExpressTemplate() {
        val proj = File(baseDir, "node_api")
        templateManager.applyTemplate(proj, ProjectTemplate.NODE_EXPRESS)

        assertThat(File(proj, "package.json").exists()).isTrue()
        assertThat(File(proj, "package.json").readText()).contains("\"express\"")
        assertThat(File(proj, "server.js").exists()).isTrue()
        assertThat(File(proj, "server.js").readText()).contains("express()")
    }

    @Test
    fun testPythonFastApiTemplate() {
        val proj = File(baseDir, "fastapi_app")
        templateManager.applyTemplate(proj, ProjectTemplate.PYTHON_FASTAPI)

        assertThat(File(proj, "main.py").exists()).isTrue()
        assertThat(File(proj, "main.py").readText()).contains("FastAPI")
        assertThat(File(proj, "requirements.txt").exists()).isTrue()
        assertThat(File(proj, "requirements.txt").readText()).contains("uvicorn")
    }

    @Test
    fun testEmptyTemplate() {
        val proj = File(baseDir, "empty_proj")
        templateManager.applyTemplate(proj, ProjectTemplate.EMPTY)

        assertThat(File(proj, "README.md").exists()).isTrue()
        assertThat(File(proj, ".agent/policy.json").exists()).isTrue()
        assertThat(File(proj, ".agent/policy.json").readText()).contains("allowCommands")
    }
}

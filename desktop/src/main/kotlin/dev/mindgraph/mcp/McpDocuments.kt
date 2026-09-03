package dev.mindgraph.mcp

/**
 * The working agreement, served to any client that connects.
 *
 * It is served rather than installed. A tool that wrote into `~/.claude/skills/` would be the
 * server reaching into the client's configuration - a far larger privilege than agents have over
 * the vault itself, where they are append-only on purpose - and it would only appear to work
 * because client and server share a filesystem today. Over a tunnel it writes to the wrong
 * machine entirely.
 *
 * Served, it needs no installation, cannot drift from the app it describes, and reaches every
 * MCP client rather than the one that happens to understand skill files.
 */
object McpDocuments {

    const val WORKING_AGREEMENT_URI = "mindgraph://working-agreement"
    const val WORKING_AGREEMENT_NAME = "working-agreement"

    const val WORKING_AGREEMENT_TITLE = "How to work through MindGraph"

    const val WORKING_AGREEMENT_DESCRIPTION =
        "The working agreement for using this vault: load context before starting, record work " +
            "as nodes, and what agents may not rewrite. Read this before working in any " +
            "repository connected to MindGraph."

    /**
     * Deliberately not the repository's own `mindgraph-workflow` skill. That one is about
     * building MindGraph - Gradle commands, this codebase's traps, how its commits are verified -
     * and an agent in an unrelated repository being told to run `./gradlew` is worse than being
     * told nothing.
     */
    val workingAgreement: String by lazy {
        McpDocuments::class.java.getResourceAsStream("/working-agreement.md")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("working-agreement.md is missing from the jar")
    }
}

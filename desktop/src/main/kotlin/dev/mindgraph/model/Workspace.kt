package dev.mindgraph.model

/**
 * A saved selection over the vault: what to look at, not what to keep apart.
 *
 * A workspace never partitions. Edges cross it, retrieval ignores it, and nothing is copied or
 * moved — the moment it becomes separate storage, a vault that spans every project turns back
 * into the per-project silos it exists to replace.
 *
 * Membership is a rule *and* a correction. A rule alone cannot say "and also these three notes";
 * a hand-written list alone cannot cover 337. Measured on a real vault: `context_for`, built for
 * exactly this, gathered six edges in a day, while one folder rule gathers 194 nodes that someone
 * already curated by maintaining the folder for years.
 */
data class Workspace(
    val rule: Rule,
    /** Nodes the rule missed. Judgement on top of structure. */
    val include: List<NodeId> = emptyList(),
    /** Nodes the rule caught wrongly. Kept as a list rather than by narrowing the rule, because
     *  a rule bent around three exceptions stops saying what it means. */
    val exclude: List<NodeId> = emptyList(),
) {
    /**
     * How the bulk of a workspace is gathered.
     *
     * Only axes the vault already has, so a workspace can be made today rather than after
     * tagging 467 nodes.
     */
    sealed interface Rule {
        /**
         * Everything imported from under a directory.
         *
         * The important one, and the reason nothing needs re-importing: every imported node
         * already stores its absolute source path, so `…/vaults/main/estudos` recovers all 194
         * notes in that folder from data on disk.
         */
        data class OriginUnder(val path: String) : Rule

        /** Everything attributed to one project. */
        data class InProject(val project: String) : Rule

        /** Every note, RFC, or reference. */
        data class OfKind(val kind: NodeKind) : Rule

        /**
         * Everything pointed at one node with `context_for`.
         *
         * The curated case, expressed as a rule so it composes with the others: a project node
         * gathers its briefing, and `include` adds whatever was never linked.
         */
        data class ContextFor(val nodeId: NodeId) : Rule

        /** Nothing gathered; the selection is exactly what [include] lists. */
        data object Nothing : Rule
    }
}

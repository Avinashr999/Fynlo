package app.fynlo.data

object ProjectScope {
    fun belongsToSelectedProject(itemProjectId: String, selectedProjectId: String): Boolean {
        val selected = selectedProjectId.ifBlank { "personal" }
        return if (selected == "personal") {
            itemProjectId.isBlank() || itemProjectId == "personal"
        } else {
            itemProjectId == selected
        }
    }
}

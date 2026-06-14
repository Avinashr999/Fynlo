package app.fynlo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectScopeDataIntegrityTest {
    @Test
    fun personalProjectIncludesPersonalAndLegacyRowsOnly() {
        assertTrue(ProjectScope.belongsToSelectedProject("personal", "personal"))
        assertTrue(ProjectScope.belongsToSelectedProject("", "personal"))
        assertFalse(ProjectScope.belongsToSelectedProject("project-a", "personal"))
    }

    @Test
    fun customProjectExcludesPersonalAndLegacyRows() {
        assertTrue(ProjectScope.belongsToSelectedProject("project-a", "project-a"))
        assertFalse(ProjectScope.belongsToSelectedProject("personal", "project-a"))
        assertFalse(ProjectScope.belongsToSelectedProject("", "project-a"))
        assertFalse(ProjectScope.belongsToSelectedProject("project-b", "project-a"))
    }

    @Test
    fun blankSelectedProjectFallsBackToPersonalScope() {
        assertTrue(ProjectScope.belongsToSelectedProject("personal", ""))
        assertTrue(ProjectScope.belongsToSelectedProject("", ""))
        assertFalse(ProjectScope.belongsToSelectedProject("project-a", ""))
    }
}

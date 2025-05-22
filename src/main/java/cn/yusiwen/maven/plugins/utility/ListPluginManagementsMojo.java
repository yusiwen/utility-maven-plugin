package cn.yusiwen.maven.plugins.utility;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.List;

@Mojo(name = "list-pluginManagements")
public class ListPluginManagementsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${maven.version}", readonly = true)
    private String mavenVersion;

    @Override
    public void execute() throws MojoExecutionException {
        // 版本检查
        if (!isMavenVersionValid()) {
            throw new MojoExecutionException(
                    "This plugin requires Maven 3.6.3 or later. Current version: " + mavenVersion
            );
        }

        if (project.getBuild() == null ||
                project.getBuild().getPluginManagement() == null) {
            getLog().info("No pluginManagement section found");
            return;
        }

        List<Plugin> plugins = project.getBuild()
                .getPluginManagement()
                .getPlugins();

        getLog().info("Plugins in pluginManagement:");
        getLog().info("----------------------------------------");

        StringBuilder info = new StringBuilder();
        info.append("\n");
        for (Plugin plugin : plugins) {
            info.append(getPluginKey(plugin))
                    .append(":")
                    .append(plugin.getVersion());

            if (!plugin.getExecutions().isEmpty()) {
                info.append("\n  Executions:");
                for (PluginExecution exec : plugin.getExecutions()) {
                    info.append("\n    - ")
                            .append(exec.getId())
                            .append(" [phase: ")
                            .append(exec.getPhase())
                            .append(", goals: ")
                            .append(exec.getGoals())
                            .append("]");
                }
            }

            if (!plugin.getDependencies().isEmpty()) {
                info.append("\n  Dependencies: ")
                        .append(plugin.getDependencies().size());
            }
            info.append("\n");
        }
        getLog().info(info.toString());
    }

    private boolean isMavenVersionValid() {
        try {
            ComparableVersion min = new ComparableVersion("3.6.3");
            ComparableVersion current = new ComparableVersion(mavenVersion);
            return current.compareTo(min) >= 0;
        } catch (Exception e) {
            getLog().warn("Failed to parse Maven version: " + mavenVersion);
            return false;
        }
    }

    private String getPluginKey(Plugin plugin) {
        return plugin.getGroupId() + ":" + plugin.getArtifactId();
    }
}

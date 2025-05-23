package cn.yusiwen.maven.plugins.utility;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Mojo(name = "list-pluginManagements")
public class ListPluginManagementsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${maven.version}", readonly = true)
    private String mavenVersion;

    @Parameter(property = "jdkVersion", defaultValue = "false")
    private Boolean jdkVersion;

    private final MavenSession mavenSession;

    @Inject
    public ListPluginManagementsMojo(MavenSession mavenSession) {
        this.mavenSession = mavenSession;
    }

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

            if (Boolean.TRUE.equals(jdkVersion)) {
                Path jarPath = constructJarPath(
                        new File(mavenSession.getLocalRepository().getBasedir()),
                        plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
                info.append(" [JDK version: ")
                        .append(JdkVersionUtil.getMinimumJDKVersion(jarPath));
                info.append("]");
            }

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
                info.append("\n  Dependencies: ");
                plugin.getDependencies()
                        .forEach(d -> {
                            info.append("\n    - ")
                                    .append(d.getGroupId()).append(":")
                                    .append(d.getArtifactId()).append(":")
                                    .append(d.getVersion());

                            if (Boolean.TRUE.equals(jdkVersion)) {
                                Path jarPath = constructJarPath(
                                        new File(mavenSession.getLocalRepository().getBasedir()),
                                        d.getGroupId(), d.getArtifactId(), d.getVersion());
                                info.append(" [JDK version: ")
                                        .append(JdkVersionUtil.getMinimumJDKVersion(jarPath));
                                info.append("]");
                            }
                        });

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

    private Path constructJarPath(File localRepoDir, String groupId,
                                  String artifactId, String version) {
        String[] groupParts = groupId.split("\\.");
        Path path = localRepoDir.toPath();
        for (String part : groupParts) {
            path = path.resolve(part);
        }
        return path.resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar");
    }
}

package cn.yusiwen.maven.plugins.utility;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "install-pluginManagements")
public class InstallPluginManagementsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private final MavenSession session;
    private final DependencyResolver dependencyResolver;

    @Inject
    public InstallPluginManagementsMojo(MavenSession session,
                                        DependencyResolver dependencyResolver) {
        this.session = session;
        this.dependencyResolver = dependencyResolver;
    }


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (project.getBuild() == null ||
                project.getBuild().getPluginManagement() == null) {
            getLog().info("No plugins in pluginManagement section");
            return;
        }

        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        project.getBuild().getPluginManagement().getPlugins().stream()
                .map(this::toCoordinate)
                .forEach(e -> {
                    e.forEach(c -> {
                        try {
                            getLog().info("Resolving " + e + " with transitive dependencies");
                            dependencyResolver.resolveDependencies(buildingRequest, c, null);
                        } catch (DependencyResolverException ex) {
                            throw new RuntimeException("Couldn't download artifact: " + ex.getMessage(), ex);
                        }
                    });
                });

    }

    private List<DependableCoordinate> toCoordinate(Plugin plugin) {
        List<DependableCoordinate> list = new ArrayList<>();
        DefaultDependableCoordinate pluginCoordinate = new DefaultDependableCoordinate();
        pluginCoordinate.setArtifactId(plugin.getArtifactId());
        pluginCoordinate.setGroupId(plugin.getGroupId());
        pluginCoordinate.setVersion(resolveVersion(plugin));
        list.add(pluginCoordinate);

        plugin.getDependencies().forEach(d -> {
            DefaultDependableCoordinate dc = new DefaultDependableCoordinate();
            dc.setArtifactId(plugin.getArtifactId());
            dc.setGroupId(plugin.getGroupId());
            dc.setVersion(resolveVersion(plugin));
            list.add(dc);
        });

        return list;
    }

    private String resolveVersion(Plugin plugin) {
        String version = plugin.getVersion();
        if (version == null) {
            version = resolveFromParent(plugin);
        }
        if (version == null) {
            throw new RuntimeException(
                    "Version not specified for plugin: " + plugin.getGroupId() + ":" + plugin.getArtifactId()
            );
        }
        return version;
    }

    private String resolveFromParent(Plugin plugin) {
        // 实现父POM版本解析逻辑
        return project.getParent().getPluginManagement()
                .getPlugins().stream()
                .filter(p -> p.getGroupId().equals(plugin.getGroupId()) &&
                        p.getArtifactId().equals(plugin.getArtifactId()))
                .findFirst()
                .map(Plugin::getVersion)
                .orElse(null);
    }
}

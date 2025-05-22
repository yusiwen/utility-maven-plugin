package cn.yusiwen.maven.plugins.utility;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
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
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "install-pluginManagements")
public class InstallPluginManagementsMojo extends AbstractMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> pomRemoteRepositories;

    private final MavenSession session;
    private final DependencyResolver dependencyResolver;
    private final RepositorySystem repositorySystem;

    /**
     * Map that contains the layouts.
     */
    private final Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    @Inject
    public InstallPluginManagementsMojo(MavenSession session,
                                        DependencyResolver dependencyResolver,
                                        RepositorySystem repositorySystem,
                                        Map<String, ArtifactRepositoryLayout> repositoryLayouts) {
        this.session = session;
        this.dependencyResolver = dependencyResolver;
        this.repositorySystem = repositorySystem;
        this.repositoryLayouts = repositoryLayouts;
    }


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (project.getBuild() == null ||
                project.getBuild().getPluginManagement() == null) {
            getLog().info("No plugins in pluginManagement section");
            return;
        }

        ArtifactRepositoryPolicy always = new ArtifactRepositoryPolicy(
                true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        List<ArtifactRepository> repoList = new ArrayList<>();

        if (pomRemoteRepositories != null) {
            repoList.addAll(pomRemoteRepositories);
        }

        if (remoteRepositories != null) {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = remoteRepositories.split(",");
            for (String repo : repos) {
                repoList.add(parseRepository(repo, always));
            }
        }

        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        Settings settings = session.getSettings();
        repositorySystem.injectMirror(repoList, settings.getMirrors());
        repositorySystem.injectProxy(repoList, settings.getProxies());
        repositorySystem.injectAuthentication(repoList, settings.getServers());

        buildingRequest.setRemoteRepositories(repoList);

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

    private Set<DependableCoordinate> toCoordinate(Plugin plugin) {
        Set<DependableCoordinate> set = new HashSet<>();
        DefaultDependableCoordinate pluginCoordinate = new DefaultDependableCoordinate();
        pluginCoordinate.setArtifactId(plugin.getArtifactId());
        pluginCoordinate.setGroupId(plugin.getGroupId());
        pluginCoordinate.setVersion(resolveVersion(plugin));
        set.add(pluginCoordinate);

        plugin.getDependencies().forEach(d -> {
            DefaultDependableCoordinate dc = new DefaultDependableCoordinate();
            dc.setArtifactId(d.getArtifactId());
            dc.setGroupId(d.getGroupId());
            dc.setVersion(d.getVersion());
            set.add(dc);
        });

        return set;
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

    ArtifactRepository parseRepository(String repo, ArtifactRepositoryPolicy policy) throws MojoFailureException {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout("default");
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if (repo.contains("::")) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);
            if (!matcher.matches()) {
                throw new MojoFailureException(
                        repo,
                        "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\".");
            }

            id = matcher.group(1).trim();
            if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                layout = getLayout(matcher.group(2).trim());
            }
            url = matcher.group(3).trim();
        }
        return new MavenArtifactRepository(id, url, layout, policy, policy);
    }

    private ArtifactRepositoryLayout getLayout(String id) throws MojoFailureException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);

        if (layout == null) {
            throw new MojoFailureException(id, "Invalid repository layout", "Invalid repository layout: " + id);
        }

        return layout;
    }
}

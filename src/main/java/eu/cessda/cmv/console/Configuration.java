package eu.cessda.cmv.console;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class Configuration {
    private final Path rootDirectory;
    private final List<Repository> repositories;

    public Configuration(@JsonProperty("rootDirectory") Path rootDirectory, @JsonProperty("repositories") Collection<Repository> repositories) {
        this.rootDirectory = rootDirectory;
        this.repositories = List.copyOf(repositories);
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }
}

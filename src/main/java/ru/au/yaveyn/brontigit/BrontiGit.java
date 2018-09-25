package ru.au.yaveyn.brontigit;

import ru.au.yaveyn.brontigit.exception.GitException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDataException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDirException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class BrontiGit {

    private static final String GIT_FOLDER_NAME = ".brontiGit";
    private static final String COMMITS_FOLDER_NAME = "commits";
    private static final String HEAD_FOLDER_NAME = "head";

    private Path root;
    private Path gitFolder;
    private Path commitsFolder;
    private Path headFolder;

    private BrontiGitData.Serializer dataSerializer;

    public BrontiGit(Path root) {
        this.root = root;
        gitFolder = root.resolve(GIT_FOLDER_NAME).normalize();
        commitsFolder = gitFolder.resolve(COMMITS_FOLDER_NAME).normalize();
        headFolder = commitsFolder.resolve(HEAD_FOLDER_NAME).normalize();
        dataSerializer = new BrontiGitData.Serializer(gitFolder);
    }

    private void checkGitFolder() throws InvalidGitDirException {
        if (!Files.exists(gitFolder) || !Files.exists(commitsFolder) || !Files.exists(headFolder)) {
            throw new InvalidGitDirException("Git folder corrupted or nonexistent.");
        }
    }

    private void checkFilesExist(List<Path> files) throws GitException {
        for (Path file : files) {
            if (!Files.exists(file) || ! Files.isRegularFile(file)) {
                throw new GitException("File " + file + " doesn't exist.");
            }
        }
    }

    private Path getCommitFolder(Commit commit) {
        return commitsFolder.resolve(commit.getName());
    }

    private Stream<Path> dismantle(Path path) {
        List<Path> parents = new ArrayList<>();
        while (path != null) {
            parents.add(path);
            path = path.getParent();
        }
        Collections.reverse(parents);
        return parents.stream();
    }

    private void replacingCopy(List<Path> files, Path dest) throws IOException {
        List<Path> toCopy = files
                .stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .map(root::relativize)
                .flatMap(this::dismantle)
                .distinct()
                .map(root::resolve)
                .collect(Collectors.toList());

        for (Path from : toCopy) {
            Path to = dest.resolve(root.relativize(from));
            if (Files.isRegularFile(from)) {
                Files.copy(from, to, REPLACE_EXISTING);
            } else if (!Files.exists(to)) {
                Files.copy(from, to);
            }
        }
    }

    public void init() throws GitException, IOException {
        if (Files.exists(gitFolder)) {
            throw new GitException("Git folder already exists.");
        }
        Files.createDirectory(gitFolder);
        Files.createDirectory(commitsFolder);
        Files.createDirectory(headFolder);
        BrontiGitData data = dataSerializer.init();
        dataSerializer.serialize(data);
    }

    public void add(List<Path> files) throws GitException, IOException, InvalidGitDirException {
        checkGitFolder();
        checkFilesExist(files);
        BrontiGitData data = dataSerializer.deserialize();
        // todo: check diff
        data.add(files);
        replacingCopy(files, headFolder);
        dataSerializer.serialize(data);
    }

    public void remove(List<Path> files) throws GitException, IOException, InvalidGitDirException, InvalidGitDataException {
        checkGitFolder();
        checkFilesExist(files);
        BrontiGitData data = dataSerializer.deserialize();
        List<Path> toRemveFromAdded = data.remove(files);
        for (Path file : toRemveFromAdded) {
            Files.delete(headFolder.resolve(file));
        }
        dataSerializer.serialize(data);
    }

    public void reset(String name) throws GitException, IOException, InvalidGitDirException, InvalidGitDataException {
        checkGitFolder();
        BrontiGitData data = dataSerializer.deserialize();
        List<Commit> toRemove = data.reset(name);
        for (Commit commit : toRemove) {
            Path commitFolder = commitsFolder.resolve(commit.getName());
            List<Path> toDelete = Files.walk(commitFolder).collect(Collectors.toList());
            Collections.reverse(toDelete);
            for (Path file : toDelete) {
                Files.delete(file);
            }
        }
        dataSerializer.serialize(data);
    }

    public void commit(String msg) throws InvalidGitDataException, IOException, InvalidGitDirException {
        checkGitFolder();
        BrontiGitData data = dataSerializer.deserialize();
        Commit commit = data.commit(msg);
        Files.move(headFolder, getCommitFolder(commit));
        Files.createDirectory(headFolder);
        dataSerializer.serialize(data);
    }
}

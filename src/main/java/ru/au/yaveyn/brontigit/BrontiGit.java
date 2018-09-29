package ru.au.yaveyn.brontigit;

import ru.au.yaveyn.brontigit.exception.GitException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDirException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    private BrontiGitData.Generator dataGenerator;

    public BrontiGit(Path root) {
        this.root = root;
        gitFolder = root.resolve(GIT_FOLDER_NAME).normalize();
        commitsFolder = gitFolder.resolve(COMMITS_FOLDER_NAME).normalize();
        headFolder = commitsFolder.resolve(HEAD_FOLDER_NAME).normalize();
        dataGenerator = new BrontiGitData.Generator(gitFolder);
    }

    private void checkGitFolder() throws InvalidGitDirException {
        if (!Files.exists(gitFolder) || !Files.exists(commitsFolder) || !Files.exists(headFolder)) {
            throw new InvalidGitDirException("Git folder corrupted or nonexistent.");
        }
    }

    private void checkFilesExist(List<Path> files) throws GitException {
        for (Path file : files) {
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
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

    private List<Path> resolveFilesFromRoot(List<Path> files, Path dir) {
        return files
                .stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .map(root::relativize)
                .map(dir::resolve)
                .collect(Collectors.toList());
    }

    private void replacingCopy(List<Path> files, Path source, Path dest) throws IOException {
        List<Path> toCopy = files
                .stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
//                .map(source::resolve)
                .map(source::relativize)
                .flatMap(this::dismantle)
                .distinct()
                .collect(Collectors.toList());

        for (Path relativeFile : toCopy) {
            Path from = source.resolve(relativeFile);
            Path to = dest.resolve(relativeFile);
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
        try (BrontiGitData data = dataGenerator.init()) {
            //do nothing
        }
    }

    public void add(List<Path> files) throws GitException, IOException, InvalidGitDirException {
        checkGitFolder();
        checkFilesExist(files);
        try (BrontiGitData data = dataGenerator.deserialize()) {
            // todo: check diff
            data.add(files);
            replacingCopy(files, root, headFolder);
        }
    }

    public void remove(List<Path> files) throws GitException, IOException, InvalidGitDirException {
        checkGitFolder();
        checkFilesExist(files);
        try (BrontiGitData data = dataGenerator.deserialize()) {
            List<Path> toRemoveFromAdded = data.remove(files);
            for (Path file : resolveFilesFromRoot(toRemoveFromAdded, headFolder)) {
                Files.delete(file);
            }
        }
    }

    public void reset(String name) throws GitException, IOException, InvalidGitDirException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            List<Commit> toRemove = data.reset(name);
            for (Commit commit : toRemove) {
                Path commitFolder = commitsFolder.resolve(commit.getName());
                List<Path> toDelete = Files.walk(commitFolder).collect(Collectors.toList());
                Collections.reverse(toDelete);
                for (Path file : toDelete) {
                    Files.delete(file);
                }
            }
        }
    }

    public Commit commit(String msg) throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            Commit commit = data.commit(msg);
            Files.move(headFolder, getCommitFolder(commit));
            Files.createDirectory(headFolder);
            return commit;
        }
    }

    public void checkoutFiles(List<Path> files) throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            Map<Path, Commit> commits = data.checkoutFiles(files);
            for (Path file : files) {
                Commit toGetFileFrom = commits.get(file);
                Path commitFolder = getCommitFolder(toGetFileFrom);
                Path toCopyFrom = commitFolder.resolve(root.relativize(file));
                replacingCopy(Collections.singletonList(toCopyFrom), commitFolder, root);
            }
        }
    }

    public void checkoutRevision(String commitName) throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            Map<Path, Commit> commits = data.checkoutRevision(commitName);
            for (Path file : commits.keySet()) {
                Commit toGetFileFrom = commits.get(file);
                if (toGetFileFrom == null) {
                    Files.delete(file);
                    continue;
                }
                Path commitFolder = getCommitFolder(toGetFileFrom);
                Path toCopyFrom = commitFolder.resolve(root.relativize(file));
                replacingCopy(Collections.singletonList(toCopyFrom), commitFolder, root);
            }
        }
    }

    public List<Commit> log(String commitName) throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            return data.getCommitsFrom(commitName);
        }
    }

    public List<Path> getUntracked() throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            List<Path> added = data.getAdded();
            List<Path> removed = data.getRemoved();
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(f -> !added.contains(f))
                    .filter(f -> !removed.contains(f))
                    .collect(Collectors.toList());
        }
    }

    public List<Path> getAdded() throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            return data.getAdded();
        }
    }

    public List<Path> getRemoved() throws IOException, InvalidGitDirException, GitException {
        checkGitFolder();
        try (BrontiGitData data = dataGenerator.deserialize()) {
            return data.getRemoved();
        }
    }
}
